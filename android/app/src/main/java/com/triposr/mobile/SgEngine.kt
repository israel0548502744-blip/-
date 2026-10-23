package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.util.Random
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * TripoSG (VAST-AI, MIT weights) on the NPU: the graphs exported by convert/export_triposg.py and
 * compiled in CI for this chip. DINOv2 encodes the picture, the DiT turns noise into 2048 shape
 * tokens over [steps] flow-matching steps (with and without the image, for guidance), and the
 * VAE's occupancy field is sampled coarse-to-fine: a 64³ grid, then only near the surface at
 * 128³ and 256³. Geometry only, no colors.
 */
class SgEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val CONFIG = "sg_config.json"
        val GRAPHS = listOf("sg_dino", "sg_dit0", "sg_dit1", "sg_dit2", "sg_vae_kv", "sg_vae_query")
        const val COARSE = 64
        const val LEVELS = 2

        fun files(chip: String) = listOf(CONFIG) + GRAPHS.flatMap { listOf("${it}_${chip}_ctx.onnx", "${it}_${chip}_ctx_qnn.bin") }
    }

    class Config(o: JSONObject) {
        val tokens = o.getInt("tokens")
        val channels = o.getInt("latent_channels")
        val width = o.getInt("width")
        val condDim = o.getInt("cond_dim")
        val guidance = o.getDouble("guidance_scale").toFloat()
        val defaultSteps = o.getInt("default_steps")
        val shift = o.getJSONObject("scheduler").getDouble("shift")
        private val dino = o.getJSONObject("dino")
        val resize = dino.getInt("resize_shortest")
        val crop = dino.getInt("crop")
        val rescale = dino.getDouble("rescale").toFloat()
        val mean = FloatArray(3) { dino.getJSONArray("mean").getDouble(it).toFloat() }
        val std = FloatArray(3) { dino.getJSONArray("std").getDouble(it).toFloat() }
        private val vae = o.getJSONObject("vae")
        val chunk = vae.getInt("query_chunk")
        val freqs = vae.getInt("embed_freqs")
        val bounds = vae.getDouble("bounds").toFloat()
        val padding = o.getJSONObject("preprocess").getDouble("padding_ratio").toFloat()
        /** Per DiT part: input and output names in order. */
        val parts = o.getJSONArray("dit_parts").let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                fun names(k: String) = p.getJSONArray(k).let { n -> (0 until n.length()).map { n.getString(it) } }
                names("inputs") to names("outputs")
            }
        }

        /** Flow-matching sigmas for [steps] steps (shifted linear schedule), plus a final 0. */
        fun sigmas(steps: Int) = DoubleArray(steps + 1) { i ->
            if (i == steps) 0.0 else (1.0 - i.toDouble() / steps).let { t -> shift * t / (1 + (shift - 1) * t) }
        }
    }

    private val env = OrtEnvironment.getEnvironment()
    private val chip = NpuPack.chip() ?: throw IllegalStateException("TripoSG צריך מעבד Snapdragon 8 Elite")
    val config = Config(JSONObject(ModelStore.file(context, CONFIG).readText()))

    private fun open(graph: String): OrtSession {
        val f = ModelStore.file(context, "${graph}_${chip}_ctx.onnx")
        if (!f.exists()) throw IllegalStateException("חבילת TripoSG לא הורדה (${f.name})")
        val opts = OrtSession.SessionOptions()
        opts.addQnn(TripoEngine.qnnOptions(Backend.NPU))
        val t0 = System.nanoTime()
        return env.createSession(f.absolutePath, opts).also {
            CrashLog.log("TripoSG: opened $graph in %.1fs".format((System.nanoTime() - t0) / 1e9))
        }
    }

    private fun tensor(data: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun OrtSession.Result.floats(i: Int = 0): FloatArray =
        (get(i) as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }

    // ------------------------------------------------------------------ image

    /**
     * Like TripoSG's prepare_image: crop to the object (alpha), white background, pad 10% and
     * to a square, then the DINOv2 processor's resize + centre crop and normalisation. CHW floats.
     */
    fun prepare(cut: Bitmap): FloatArray {
        val c = config
        val w = cut.width; val h = cut.height
        val px = IntArray(w * h).also { cut.getPixels(it, 0, w, 0, 0, w, h) }
        var x1 = w; var y1 = h; var x2 = -1; var y2 = -1
        for (y in 0 until h) for (x in 0 until w) if (Color.alpha(px[y * w + x]) > 127) {
            if (x < x1) x1 = x; if (x > x2) x2 = x; if (y < y1) y1 = y; if (y > y2) y2 = y
        }
        if (x2 < 0) { x1 = 0; y1 = 0; x2 = w - 1; y2 = h - 1 }
        val bw = x2 - x1 + 1; val bh = y2 - y1 + 1
        val side = (maxOf(bw, bh) * (1 + 2 * c.padding)).toInt()
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.WHITE)
            drawBitmap(Bitmap.createBitmap(cut, x1, y1, bw, bh), ((side - bw) / 2).toFloat(), ((side - bh) / 2).toFloat(),
                Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val scaled = Bitmap.createScaledBitmap(square, c.resize, c.resize, true)
        val o = (c.resize - c.crop) / 2
        val img = Bitmap.createBitmap(scaled, o, o, c.crop, c.crop)
        val n = c.crop * c.crop
        val p = IntArray(n).also { img.getPixels(it, 0, c.crop, 0, 0, c.crop, c.crop) }
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            out[i] = (Color.red(p[i]) * c.rescale - c.mean[0]) / c.std[0]
            out[n + i] = (Color.green(p[i]) * c.rescale - c.mean[1]) / c.std[1]
            out[2 * n + i] = (Color.blue(p[i]) * c.rescale - c.mean[2]) / c.std[2]
        }
        return out
    }

    // ------------------------------------------------------------------ embeddings (fp32 on the CPU)

    /** diffusers Timesteps(width, flip_sin_to_cos=False, shift 0): [sin, cos], twice (batch 2). */
    private fun timestepEmbedding(t: Double): FloatArray {
        val dim = config.width; val half = dim / 2
        val out = FloatArray(2 * dim)
        for (i in 0 until half) {
            val a = t * exp(-ln(10000.0) * i / half)
            out[i] = sin(a).toFloat(); out[half + i] = cos(a).toFloat()
        }
        System.arraycopy(out, 0, out, dim, dim)
        return out
    }

    /** [x, sin(2^k x), cos(2^k x)] per point, as in FrequencyPositionalEmbedding. */
    private fun embedPoint(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
        val f = config.freqs
        out[at] = x; out[at + 1] = y; out[at + 2] = z
        var o = at + 3
        val p = floatArrayOf(x, y, z)
        for (k in 0..2) for (j in 0 until f) out[o++] = sin(p[k].toDouble() * (1 shl j)).toFloat()
        for (k in 0..2) for (j in 0 until f) out[o++] = cos(p[k].toDouble() * (1 shl j)).toFloat()
    }

    // ------------------------------------------------------------------ pipeline

    fun generate(cut: Bitmap, steps: Int, seed: Long, smoothIterations: Int, progress: (Stage, Float) -> Unit,
                 cancelled: () -> Boolean): TripoEngine.Output {
        val c = config
        val timings = LinkedHashMap<Stage, Double>()
        fun <T> timed(stage: Stage, block: () -> T): T {
            if (cancelled()) throw CancellationException()
            progress(stage, 0f)
            val t0 = System.nanoTime()
            val r = block()
            timings[stage] = (System.nanoTime() - t0) / 1e9
            CrashLog.log("TripoSG ${stage.name} done in %.2fs".format(timings[stage]))
            return r
        }

        val cond = timed(Stage.ENCODE) {
            open("sg_dino").use { s ->
                tensor(prepare(cut), 1, 3, c.crop.toLong(), c.crop.toLong()).use { x ->
                    s.run(mapOf("image" to x)).use { it.floats() }
                }
            }
        }

        val latents = timed(Stage.DENOISE) { denoise(cond, steps, seed, progress, cancelled) }

        val res = COARSE shl LEVELS
        val field = timed(Stage.FIELD) { field(latents, progress, cancelled) }

        var mesh = timed(Stage.SURFACE) { SurfaceNets.extract(field, res + 1, c.bounds) }
        if (mesh.vertexCount == 0) throw IllegalStateException("TripoSG לא מצא צורה. נסה תמונה אחרת.")
        CrashLog.log("TripoSG surface: ${mesh.vertexCount} vertices, ${mesh.triangleCount} triangles")
        mesh = timed(Stage.COLOR) {
            val m = if (smoothIterations > 0) MeshTools.smooth(mesh, smoothIterations) else mesh
            m.colors = FloatArray(m.positions.size) { 0.82f }
            m
        }
        return TripoEngine.Output(mesh, timings)
    }

    private fun denoise(cond: FloatArray, steps: Int, seed: Long, progress: (Stage, Float) -> Unit,
                        cancelled: () -> Boolean): FloatArray {
        val c = config
        val n = c.tokens * c.channels
        val rnd = Random(seed)
        val lat = FloatArray(n) { rnd.nextGaussian().toFloat() }
        val sig = c.sigmas(steps)
        val condTokens = cond.size / c.condDim
        // Batch 2: [no image, image]. The unconditional branch sees zeros.
        val cond2 = FloatArray(2 * cond.size).also { System.arraycopy(cond, 0, it, cond.size, cond.size) }
        val sessions = (0 until 3).map { open("sg_dit$it") }
        try {
            tensor(cond2, 2, condTokens.toLong(), c.condDim.toLong()).use { condT ->
                val both = FloatArray(2 * n)
                for (i in 0 until steps) {
                    if (cancelled()) throw CancellationException()
                    val t0 = System.nanoTime()
                    System.arraycopy(lat, 0, both, 0, n); System.arraycopy(lat, 0, both, n, n)
                    val v = tensor(both, 2, c.tokens.toLong(), c.channels.toLong()).use { latT ->
                        tensor(timestepEmbedding(sig[i] * 1000), 2, c.width.toLong()).use { tembT ->
                            runDit(sessions, latT, tembT, condT)
                        }
                    }
                    // Guidance, then one Euler step along the predicted flow.
                    val dt = (sig[i] - sig[i + 1]).toFloat()
                    for (k in 0 until n) lat[k] += dt * (v[k] + c.guidance * (v[n + k] - v[k]))
                    if (i == 0) CrashLog.log("TripoSG step 1 in %.2fs".format((System.nanoTime() - t0) / 1e9))
                    progress(Stage.DENOISE, (i + 1f) / steps)
                }
            }
        } finally {
            sessions.forEach { it.close() }
        }
        return lat
    }

    /** One DiT evaluation through the three parts; skips go from part 0 straight to part 2. */
    private fun runDit(s: List<OrtSession>, latT: OnnxTensor, tembT: OnnxTensor, condT: OnnxTensor): FloatArray {
        val (in0, out0) = config.parts[0]
        val (in1, _) = config.parts[1]
        val (in2, _) = config.parts[2]
        s[0].run(mapOf(in0[0] to latT, in0[1] to tembT, in0[2] to condT)).use { r0 ->
            val outs0 = out0.indices.associate { out0[it] to (r0[it] as OnnxTensor) }
            val hidden0 = outs0.getValue("hidden")
            s[1].run(mapOf(in1[0] to hidden0, in1[1] to condT)).use { r1 ->
                val feeds = HashMap<String, OnnxTensor>()
                feeds[in2[0]] = r1[0] as OnnxTensor
                feeds[in2[1]] = condT
                for (name in in2.drop(2)) feeds[name] = outs0[name] ?: throw IllegalStateException("missing $name")
                return s[2].run(feeds).use { it.floats() }
            }
        }
    }

    /** Occupancy logits on a (res+1)³ grid over [-bounds, bounds]³, positive inside. */
    private fun field(latents: FloatArray, progress: (Stage, Float) -> Unit, cancelled: () -> Boolean): FloatArray {
        val c = config
        val kv = open("sg_vae_kv").use { s ->
            tensor(latents, 1, c.tokens.toLong(), c.channels.toLong()).use { t -> s.run(mapOf("latents" to t)).use { it.floats() } }
        }
        open("sg_vae_query").use { s ->
            tensor(kv, 1, c.tokens.toLong(), (kv.size / c.tokens).toLong()).use { kvT ->
                val dim = 3 * (2 * c.freqs + 1)
                val buf = FloatArray(c.chunk * dim)
                var done = 0L; var total = 0L
                /** Evaluates [count] points of a (r+1)³ grid given by flat indices. */
                fun query(r: Int, idx: IntArray, count: Int, out: FloatArray) {
                    val g = r + 1; val step = 2f * c.bounds / r
                    var start = 0
                    while (start < count) {
                        if (cancelled()) throw CancellationException()
                        val m = minOf(c.chunk, count - start)
                        java.util.Arrays.fill(buf, 0f)
                        for (q in 0 until m) {
                            val id = idx[start + q]
                            embedPoint(-c.bounds + id / (g * g) * step, -c.bounds + id / g % g * step,
                                -c.bounds + id % g * step, buf, q * dim)
                        }
                        val logits = tensor(buf, 1, c.chunk.toLong(), dim.toLong()).use { p ->
                            s.run(mapOf("kv" to kvT, "points" to p)).use { it.floats() }
                        }
                        for (q in 0 until m) out[idx[start + q]] = logits[q]
                        start += m; done += m
                        progress(Stage.FIELD, (done.toFloat() / total.coerceAtLeast(1)).coerceAtMost(0.99f))
                    }
                }

                var r = COARSE
                var g = r + 1
                var vals = FloatArray(g * g * g)
                total = vals.size.toLong() * 3  // rough: the finer levels query about as much again each
                query(r, IntArray(vals.size) { it }, vals.size, vals)
                val counts = arrayListOf(vals.size)
                repeat(LEVELS) {
                    val near = nearSurface(vals, g)
                    val r2 = r * 2; val g2 = r2 + 1
                    val fine = upsample(vals, g)
                    val mask = BooleanArray(g2 * g2 * g2)
                    for (i in 0 until g2) for (j in 0 until g2) for (k in 0 until g2)
                        if (near[((i / 2) * g + j / 2) * g + k / 2]) mask[(i * g2 + j) * g2 + k] = true
                    val grown = dilate(mask, g2)
                    val idx = IntArray(grown.count { it }).also { a -> var n = 0; for (q in grown.indices) if (grown[q]) a[n++] = q }
                    counts += idx.size
                    total = done + idx.size
                    query(r2, idx, idx.size, fine)
                    vals = fine; r = r2; g = g2
                }
                CrashLog.log("TripoSG field: query points per level $counts")
                // Outer layer counts as outside, so the surface is always closed.
                for (i in 0 until g) for (j in 0 until g) for (k in 0 until g)
                    if (i == 0 || j == 0 || k == 0 || i == g - 1 || j == g - 1 || k == g - 1) {
                        val q = (i * g + j) * g + k
                        if (vals[q] > 0f) vals[q] = -1e-3f
                    }
                return vals
            }
        }
    }

    /** Points whose value is small or whose sign differs from a neighbour, grown by one. */
    private fun nearSurface(v: FloatArray, g: Int): BooleanArray {
        val near = BooleanArray(v.size)
        for (i in 0 until g) for (j in 0 until g) for (k in 0 until g) {
            val q = (i * g + j) * g + k
            val s = v[q] > 0f
            near[q] = abs(v[q]) < 0.95f ||
                (i > 0 && (v[q - g * g] > 0f) != s) || (i < g - 1 && (v[q + g * g] > 0f) != s) ||
                (j > 0 && (v[q - g] > 0f) != s) || (j < g - 1 && (v[q + g] > 0f) != s) ||
                (k > 0 && (v[q - 1] > 0f) != s) || (k < g - 1 && (v[q + 1] > 0f) != s)
        }
        return dilate(near, g)
    }

    /** 6-neighbour dilation by one. */
    private fun dilate(m: BooleanArray, g: Int): BooleanArray {
        val out = m.copyOf()
        for (i in 0 until g) for (j in 0 until g) for (k in 0 until g) {
            val q = (i * g + j) * g + k
            if (!m[q]) continue
            if (i > 0) out[q - g * g] = true; if (i < g - 1) out[q + g * g] = true
            if (j > 0) out[q - g] = true; if (j < g - 1) out[q + g] = true
            if (k > 0) out[q - 1] = true; if (k < g - 1) out[q + 1] = true
        }
        return out
    }

    /** Trilinear 2x upsampling of a g³ grid to (2g-1)³, corners aligned. */
    private fun upsample(v: FloatArray, g: Int): FloatArray {
        val g2 = 2 * g - 1
        val out = FloatArray(g2 * g2 * g2)
        for (i in 0 until g2) for (j in 0 until g2) for (k in 0 until g2) {
            val i0 = i / 2; val j0 = j / 2; val k0 = k / 2
            val i1 = i0 + i % 2; val j1 = j0 + j % 2; val k1 = k0 + k % 2
            out[(i * g2 + j) * g2 + k] = 0.125f * (
                v[(i0 * g + j0) * g + k0] + v[(i0 * g + j0) * g + k1] + v[(i0 * g + j1) * g + k0] + v[(i0 * g + j1) * g + k1] +
                v[(i1 * g + j0) * g + k0] + v[(i1 * g + j0) * g + k1] + v[(i1 * g + j1) * g + k0] + v[(i1 * g + j1) * g + k1])
        }
        return out
    }

    override fun close() {}
}
