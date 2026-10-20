package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ln

enum class Backend(val label: String, val short: String) {
    NPU("NPU (Hexagon)", "NPU"),
    GPU("GPU (Adreno)", "GPU"),
    CPU("CPU", "CPU"),
}

/** Pipeline steps, with their rough share of the total time (for the progress ring). */
enum class Stage(val label: String, val weight: Float) {
    LOAD("טעינת המודל", 0.10f),
    SEGMENT("הסרת רקע", 0.05f),
    ENCODE("ניתוח התמונה", 0.25f),
    FIELD("בניית שדה תלת־ממד", 0.40f),
    SURFACE("יצירת רשת פוליגונים", 0.10f),
    COLOR("צביעה והחלקה", 0.10f),
}

/**
 * Runs the two TripoSR graphs exported by convert/export_onnx.py with ONNX Runtime.
 * NPU/GPU go through the Qualcomm QNN execution provider; any operator QNN cannot take
 * falls back to the CPU automatically.
 */
class TripoEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val ENCODER = "triposr_encoder.onnx"
        const val DECODER = "triposr_decoder.onnx"
        const val SEGMENTER = "u2netp.onnx"
        const val IMAGE_SIZE = 512
        const val CHUNK = 32768          // must match DECODER_CHUNK in export_onnx.py
        const val RADIUS = 0.87f
        const val DENSITY_THRESHOLD = 25f
    }

    private val env = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var segmenter: OrtSession? = null
    var backend: Backend? = null; private set
    var encoderBackend: Backend = Backend.CPU; private set

    fun load(wanted: Backend, onStatus: (String) -> Unit) {
        if (backend == wanted && encoder != null) return
        closeSessions()
        val t0 = System.nanoTime()
        if (wanted == Backend.NPU) onStatus("מכין את המודל ל-NPU (בפעם הראשונה עד כמה דקות)")
        val (e, used) = createSession(ModelStore.file(context, ENCODER), wanted, "encoder")
        encoder = e
        encoderBackend = used
        decoder = createSession(ModelStore.file(context, DECODER), wanted, "decoder").first
        backend = wanted
        CrashLog.log("Sessions ready in %.1fs, encoder on ${used.label}".format((System.nanoTime() - t0) / 1e9))
    }

    fun segmenter(): OrtSession = segmenter ?: run {
        val f = File(context.filesDir, SEGMENTER)
        if (!f.exists()) context.assets.open(SEGMENTER).use { i -> f.outputStream().use { i.copyTo(it) } }
        cpuSession(f).also { segmenter = it }
    }

    /** image: CHW float RGB in 0..1, 3 x 512 x 512, background already grey. Returns scene codes [3,40,64,64]. */
    fun encode(image: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(image), shape).use { input ->
            encoder!!.run(mapOf("image" to input)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
    }

    /**
     * Evaluates the triplane NeRF at [count] points produced by [pointAt] (writes xyz into the array).
     * Calls [sink] with (start index, count, density logits, colors) for every chunk.
     */
    fun query(
        sceneCodes: FloatArray,
        count: Int,
        pointAt: (Int, FloatArray) -> Unit,
        sink: (Int, Int, FloatBuffer, FloatBuffer) -> Unit,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val codes = OnnxTensor.createTensor(env, FloatBuffer.wrap(sceneCodes), longArrayOf(3, 40, 64, 64))
        val pts = ByteBuffer.allocateDirect(CHUNK * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val p = FloatArray(3)
        codes.use {
            var start = 0
            while (start < count) {
                if (cancelled()) throw CancellationException()
                val n = minOf(CHUNK, count - start)
                pts.clear()
                for (q in 0 until CHUNK) {
                    if (q < n) pointAt(start + q, p) else { p[0] = 0f; p[1] = 0f; p[2] = 0f }
                    pts.put(p)
                }
                pts.rewind()
                OnnxTensor.createTensor(env, pts, longArrayOf(CHUNK.toLong(), 3)).use { pt ->
                    decoder!!.run(mapOf("scene_codes" to codes, "points" to pt)).use { out ->
                        sink(start, n, (out[0] as OnnxTensor).floatBuffer, (out[1] as OnnxTensor).floatBuffer)
                    }
                }
                start += n
                onProgress(start.toFloat() / count)
            }
        }
    }

    class Output(val mesh: Mesh, val timings: Map<Stage, Double>)

    /** Triplane -> density grid -> surface -> vertex colors. [progress] gets (stage, 0..1 within it). */
    fun reconstruct(
        image: FloatArray,
        resolution: Int,
        smooth: Boolean,
        progress: (Stage, Float) -> Unit,
        cancelled: () -> Boolean,
    ): Output {
        val timings = LinkedHashMap<Stage, Double>()
        fun <T> timed(stage: Stage, block: () -> T): T {
            if (cancelled()) throw CancellationException()
            progress(stage, 0f)
            CrashLog.log("Stage ${stage.name} start")
            val t0 = System.nanoTime()
            val r = block()
            timings[stage] = (System.nanoTime() - t0) / 1e9
            CrashLog.log("Stage ${stage.name} done in %.2fs".format(timings[stage]))
            return r
        }

        val codes = timed(Stage.ENCODE) { encode(image) }

        val res = resolution
        val step = 2f * RADIUS / (res - 1)
        val field = timed(Stage.FIELD) {
            val field = FloatArray(res * res * res)
            val iso = ln(DENSITY_THRESHOLD)
            query(codes, field.size,
                pointAt = { idx, p ->
                    p[0] = -RADIUS + (idx / (res * res)) * step
                    p[1] = -RADIUS + (idx / res % res) * step
                    p[2] = -RADIUS + (idx % res) * step
                },
                sink = { start, n, density, _ -> for (q in 0 until n) field[start + q] = density.get(q) - iso },
                onProgress = { progress(Stage.FIELD, it) },
                cancelled = cancelled)
            field
        }

        var mesh = timed(Stage.SURFACE) { SurfaceNets.extract(field, res, RADIUS) }
        if (mesh.vertexCount == 0) throw IllegalStateException("לא נמצא אובייקט בתמונה. נסה תמונה עם אובייקט ברור במרכז.")
        CrashLog.log("Surface: ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles")

        mesh = timed(Stage.COLOR) {
            val m = if (smooth) MeshTools.smooth(mesh, 3) else mesh
            val colors = FloatArray(m.positions.size)
            query(codes, m.vertexCount,
                pointAt = { idx, p -> System.arraycopy(m.positions, idx * 3, p, 0, 3) },
                sink = { start, n, _, color -> color.position(0); color.get(colors, start * 3, n * 3) },
                onProgress = { progress(Stage.COLOR, it) },
                cancelled = cancelled)
            m.colors = colors
            m.toYUp()
        }
        return Output(mesh, timings)
    }

    private fun createSession(model: File, wanted: Backend, cacheName: String): Pair<OrtSession, Backend> {
        if (!model.exists()) throw IllegalStateException("המודל לא הורד עדיין (${model.name})")
        if (wanted == Backend.CPU) return cpuSession(model) to Backend.CPU
        // Compiling a large graph for the NPU takes minutes, so the compiled QNN context is cached.
        // The model size is part of the name, so a new model never reuses an old cache.
        val ctx = if (wanted == Backend.NPU) File(context.filesDir, "${cacheName}_${model.length()}_qnn_ctx.onnx") else null
        return try {
            val opts = OrtSession.SessionOptions()
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            val qnn = mutableMapOf<String, String>()
            if (wanted == Backend.NPU) {
                qnn["backend_path"] = "libQnnHtp.so"
                qnn["htp_performance_mode"] = "burst"
                qnn["htp_graph_finalization_optimization_mode"] = "3"
                qnn["enable_htp_fp16_precision"] = "1"
            } else {
                qnn["backend_path"] = "libQnnGpu.so"
            }
            opts.addQnn(qnn)
            CrashLog.log("Creating ${wanted.short} session for ${model.name} (cached=${ctx?.exists() == true})")
            if (ctx != null && ctx.exists()) {
                env.createSession(ctx.absolutePath, opts) to wanted
            } else {
                if (ctx != null) {
                    opts.addConfigEntry("ep.context_enable", "1")
                    opts.addConfigEntry("ep.context_file_path", ctx.absolutePath)
                }
                env.createSession(model.absolutePath, opts) to wanted
            }
        } catch (e: Exception) {
            ctx?.delete()
            CrashLog.error("${wanted.short} session for ${model.name}, falling back to CPU", e)
            cpuSession(model) to Backend.CPU
        }
    }

    private fun cpuSession(model: File): OrtSession {
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
        return env.createSession(model.absolutePath, opts)
    }

    private fun closeSessions() {
        encoder?.close(); decoder?.close()
        encoder = null; decoder = null; backend = null
    }

    override fun close() {
        closeSessions()
        segmenter?.close()
        segmenter = null
    }
}
