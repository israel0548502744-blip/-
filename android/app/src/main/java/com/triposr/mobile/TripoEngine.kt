package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ln

enum class Backend(val label: String) {
    NPU("NPU (Hexagon)"),
    GPU("GPU (Adreno)"),
    CPU("CPU"),
}

/**
 * Runs the two TripoSR graphs exported by convert/export_onnx.py with ONNX Runtime.
 * NPU/GPU go through the Qualcomm QNN execution provider; any operator QNN cannot take
 * falls back to the CPU automatically.
 */
class TripoEngine(private val context: Context, private val log: (String) -> Unit) : AutoCloseable {
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
    var segmenter: OrtSession? = null; private set
    var backend: Backend? = null; private set
    var encoderBackend = ""; private set

    fun load(wanted: Backend, onProgress: (String) -> Unit) {
        if (backend == wanted && encoder != null) return
        closeSessions()
        val enc = modelFile(ENCODER, onProgress)
        val dec = modelFile(DECODER, onProgress)
        val seg = modelFile(SEGMENTER, onProgress)

        onProgress("טוען מודל ל-${wanted.label}... (בפעם הראשונה ב-NPU זה יכול לקחת כמה דקות)")
        val (e, used) = createSession(enc, wanted, cacheName = "encoder")
        encoder = e
        encoderBackend = used.label
        decoder = createSession(dec, wanted, cacheName = "decoder").first
        segmenter = createSession(seg, Backend.CPU, cacheName = null).first
        backend = wanted
        log("מקודד רץ על: ${used.label}")
    }

    /** image: CHW float RGB in 0..1, 3 x 512 x 512, background already grey. Returns scene codes [3,40,64,64]. */
    fun encode(image: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(image), shape).use { input ->
            encoder!!.run(mapOf("image" to input)).use { out ->
                val t = out[0] as OnnxTensor
                val fb = t.floatBuffer
                return FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
    }

    /**
     * Evaluates the triplane NeRF at [count] points produced by [pointAt] (writes xyz into the array).
     * Calls [sink] with (start index, density logits, colors) for every chunk.
     */
    fun query(
        sceneCodes: FloatArray,
        count: Int,
        pointAt: (Int, FloatArray) -> Unit,
        sink: (Int, Int, FloatBuffer, FloatBuffer) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val codes = OnnxTensor.createTensor(env, FloatBuffer.wrap(sceneCodes), longArrayOf(3, 40, 64, 64))
        val pts = ByteBuffer.allocateDirect(CHUNK * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val p = FloatArray(3)
        codes.use {
            var start = 0
            while (start < count) {
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

    /** Full pipeline after preprocessing: triplane -> density grid -> surface -> vertex colors. */
    fun reconstruct(image: FloatArray, resolution: Int, onProgress: (String) -> Unit): Mesh {
        var t0 = System.nanoTime()
        onProgress("מריץ את המודל (${encoderBackend})...")
        val codes = encode(image)
        log("מקודד: %.1f שניות".format((System.nanoTime() - t0) / 1e9))

        t0 = System.nanoTime()
        val res = resolution
        val step = 2f * RADIUS / (res - 1)
        val field = FloatArray(res * res * res)
        val iso = ln(DENSITY_THRESHOLD)
        query(codes, field.size,
            pointAt = { idx, p ->
                p[0] = -RADIUS + (idx / (res * res)) * step
                p[1] = -RADIUS + (idx / res % res) * step
                p[2] = -RADIUS + (idx % res) * step
            },
            sink = { start, n, density, _ -> for (q in 0 until n) field[start + q] = density.get(q) - iso },
            onProgress = { onProgress("בונה רשת תלת מימד %d%%".format((it * 100).toInt())) })
        log("שדה צפיפות $res³: %.1f שניות".format((System.nanoTime() - t0) / 1e9))

        t0 = System.nanoTime()
        onProgress("מחלץ משטח...")
        val mesh = SurfaceNets.extract(field, res, RADIUS)
        if (mesh.vertexCount == 0) throw IllegalStateException("לא נמצא אובייקט בתמונה")
        log("משטח: ${mesh.vertexCount} קודקודים, ${mesh.triangleCount} משולשים, %.1f שניות"
            .format((System.nanoTime() - t0) / 1e9))

        val colors = FloatArray(mesh.positions.size)
        query(codes, mesh.vertexCount,
            pointAt = { idx, p -> System.arraycopy(mesh.positions, idx * 3, p, 0, 3) },
            sink = { start, n, _, color -> color.position(0); color.get(colors, start * 3, n * 3) },
            onProgress = { onProgress("צובע %d%%".format((it * 100).toInt())) })
        mesh.colors = colors
        return mesh.toYUp()
    }

    private fun createSession(model: File, wanted: Backend, cacheName: String?): Pair<OrtSession, Backend> {
        if (wanted == Backend.CPU) return cpuSession(model) to Backend.CPU
        // Compiling a large graph for the NPU takes minutes, so the compiled QNN context is cached.
        val ctx = cacheName?.takeIf { wanted == Backend.NPU }?.let { File(context.filesDir, "${it}_qnn_ctx.onnx") }
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
            log("${wanted.label} לא זמין עבור ${model.name} (${e.message?.take(200)}), עובר ל-CPU")
            cpuSession(model) to Backend.CPU
        }
    }

    private fun cpuSession(model: File): OrtSession {
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
        return env.createSession(model.absolutePath, opts)
    }

    /** ONNX Runtime needs a real file, so the models are copied out of the APK once. */
    private fun modelFile(name: String, onProgress: (String) -> Unit): File {
        val out = File(context.filesDir, name)
        val expected = context.assets.openFd(name).use { it.length }
        if (out.exists() && out.length() == expected) return out
        val tmp = File(context.filesDir, "$name.tmp")
        context.assets.open(name).use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(8 shl 20)
                var copied = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    output.write(buf, 0, r)
                    copied += r
                    onProgress("מכין את המודל $name: ${copied * 100 / expected}%")
                }
            }
        }
        tmp.renameTo(out)
        return out
    }

    private fun closeSessions() {
        encoder?.close(); decoder?.close(); segmenter?.close()
        encoder = null; decoder = null; segmenter = null; backend = null
    }

    override fun close() = closeSessions()
}
