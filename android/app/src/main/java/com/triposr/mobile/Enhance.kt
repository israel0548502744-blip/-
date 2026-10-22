package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * The optional models from [Extras]: photo upscaling (Real-ESRGAN), stronger background removal
 * (ISNet), tap-to-select (MobileSAM) and depth (Depth Anything V2 Small). They run on the CPU
 * through ONNX Runtime, outside the NPU/GPU path used by TripoSR.
 */
object Enhance {
    private val env = OrtEnvironment.getEnvironment()
    private val sessions = HashMap<String, OrtSession>()

    @Synchronized
    private fun session(file: File): OrtSession = sessions.getOrPut(file.absolutePath) {
        val o = OrtSession.SessionOptions()
        o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        o.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
        env.createSession(file.absolutePath, o)
    }

    /** Closes sessions, e.g. before an extra is deleted. */
    @Synchronized
    fun close() { sessions.values.forEach { it.close() }; sessions.clear() }

    private fun chw(bmp: Bitmap, norm: (Int, Float) -> Float): FloatArray {
        val w = bmp.width; val h = bmp.height; val plane = w * h
        val px = IntArray(plane).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            out[i] = norm(0, Color.red(c).toFloat()); out[plane + i] = norm(1, Color.green(c).toFloat()); out[2 * plane + i] = norm(2, Color.blue(c).toFloat())
        }
        return out
    }

    private fun run(s: OrtSession, feeds: Map<String, OnnxTensor>): List<FloatArray> =
        try {
            s.run(feeds).use { r -> (0 until r.size()).map { i -> (r[i] as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } } } }
        } finally { feeds.values.forEach { it.close() } }

    private fun tensor(data: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    // ------------------------------------------------------------------ upscaling

    /** Small photos only: the 4x output of a larger one would not fit in memory and adds nothing at 512 px. */
    const val UPSCALE_MAX_INPUT = 600

    fun shouldUpscale(ctx: Context, bmp: Bitmap) =
        Extras.active(ctx, Extras.UPSCALE) && maxOf(bmp.width, bmp.height) <= UPSCALE_MAX_INPUT

    /** Real-ESRGAN general-x4v3: 4x larger, sharper and cleaner. Keeps the alpha channel if any. */
    fun upscale(ctx: Context, src: Bitmap): Bitmap {
        val s = session(Extras.file(ctx, Extras.UPSCALE))
        val w = src.width; val h = src.height
        val out = run(s, mapOf("image" to tensor(chw(src) { _, v -> v / 255f }, 1, 3, h.toLong(), w.toLong())))[0]
        val W = w * 4; val H = h * 4; val plane = W * H
        val alpha = if (Preprocess.hasTransparency(src)) Bitmap.createScaledBitmap(src, W, H, true) else null
        val ap = alpha?.let { a -> IntArray(plane).also { a.getPixels(it, 0, W, 0, 0, W, H) } }
        val px = IntArray(plane) { i ->
            fun ch(k: Int) = (out[k * plane + i].coerceIn(0f, 1f) * 255f).roundToInt()
            Color.argb(ap?.let { Color.alpha(it[i]) } ?: 255, ch(0), ch(1), ch(2))
        }
        return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ background removal

    /** Background removal with ISNet when it is installed and on, else the built-in u2netp. */
    fun removeBackground(ctx: Context, src: Bitmap, builtIn: () -> OrtSession): Bitmap =
        if (Extras.active(ctx, Extras.SEGMENT)) Preprocess.removeBackground(src, session(Extras.file(ctx, Extras.SEGMENT)), Preprocess.ISNET)
        else Preprocess.removeBackground(src, builtIn())

    // ------------------------------------------------------------------ tap to select (MobileSAM)

    private const val SAM = 1024
    private val SAM_MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
    private val SAM_STD = floatArrayOf(58.395f, 57.12f, 57.375f)

    /** Image embedding for one picture; computed once, then every tap only runs the small decoder. */
    class SamImage(val width: Int, val height: Int, val scale: Float, val embedding: FloatArray)

    fun samEncode(ctx: Context, src: Bitmap): SamImage {
        val s = session(Extras.file(ctx, Extras.TAP, 0))
        val scale = SAM.toFloat() / maxOf(src.width, src.height)
        val rw = (src.width * scale).roundToInt(); val rh = (src.height * scale).roundToInt()
        val resized = Bitmap.createScaledBitmap(src, rw, rh, true)
        val px = IntArray(rw * rh).also { resized.getPixels(it, 0, rw, 0, 0, rw, rh) }
        // Normalised, padded with zeros to 1024x1024 at the bottom/right (as SamPredictor does).
        val input = FloatArray(3 * SAM * SAM)
        for (y in 0 until rh) for (x in 0 until rw) {
            val c = px[y * rw + x]; val o = y * SAM + x
            input[o] = (Color.red(c) - SAM_MEAN[0]) / SAM_STD[0]
            input[SAM * SAM + o] = (Color.green(c) - SAM_MEAN[1]) / SAM_STD[1]
            input[2 * SAM * SAM + o] = (Color.blue(c) - SAM_MEAN[2]) / SAM_STD[2]
        }
        val emb = run(s, mapOf("image" to tensor(input, 1, 3, SAM.toLong(), SAM.toLong())))[0]
        return SamImage(src.width, src.height, scale, emb)
    }

    /** Foreground mask (0..255 per pixel of the original image) for the tapped points. */
    fun samMask(ctx: Context, img: SamImage, points: List<Pair<Float, Float>>): IntArray {
        val s = session(Extras.file(ctx, Extras.TAP, 1))
        val n = points.size + 1  // plus the padding point SAM expects without a box
        val coords = FloatArray(n * 2); val labels = FloatArray(n)
        points.forEachIndexed { i, (x, y) -> coords[i * 2] = x * img.scale; coords[i * 2 + 1] = y * img.scale; labels[i] = 1f }
        labels[n - 1] = -1f
        val out = run(s, mapOf(
            "image_embeddings" to tensor(img.embedding, 1, 256, 64, 64),
            "point_coords" to tensor(coords, 1, n.toLong(), 2),
            "point_labels" to tensor(labels, 1, n.toLong()),
            "mask_input" to tensor(FloatArray(256 * 256), 1, 1, 256, 256),
            "has_mask_input" to tensor(floatArrayOf(0f), 1),
            "orig_im_size" to tensor(floatArrayOf(img.height.toFloat(), img.width.toFloat()), 2),
        ))[0]
        // Logits -> soft alpha (a little feathering along the edge).
        return IntArray(img.width * img.height) { i -> ((out[i] * 2f + 0.5f).coerceIn(0f, 1f) * 255f).roundToInt() }
    }

    /** [src] with [mask] as its alpha channel. */
    fun applyMask(src: Bitmap, mask: IntArray): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        for (i in px.indices) px[i] = (px[i] and 0x00FFFFFF) or (mask[i] shl 24)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ depth

    private const val DEPTH = 518
    private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** Relative inverse depth (bigger = closer) resampled to [w]×[h]. */
    fun depth(ctx: Context, src: Bitmap, w: Int, h: Int): FloatArray {
        val s = session(Extras.file(ctx, Extras.DEPTH))
        val input = chw(Bitmap.createScaledBitmap(src, DEPTH, DEPTH, true)) { c, v -> (v / 255f - IMAGENET_MEAN[c]) / IMAGENET_STD[c] }
        val d = run(s, mapOf("image" to tensor(input, 1, 3, DEPTH.toLong(), DEPTH.toLong())))[0]
        // Bilinear resample 518x518 -> w×h.
        return FloatArray(w * h) { i ->
            val fx = (i % w + 0.5f) * DEPTH / w - 0.5f; val fy = (i / w + 0.5f) * DEPTH / h - 0.5f
            val x0 = fx.toInt().coerceIn(0, DEPTH - 1); val y0 = fy.toInt().coerceIn(0, DEPTH - 1)
            val x1 = (x0 + 1).coerceAtMost(DEPTH - 1); val y1 = (y0 + 1).coerceAtMost(DEPTH - 1)
            val tx = (fx - x0).coerceIn(0f, 1f); val ty = (fy - y0).coerceIn(0f, 1f)
            val a = d[y0 * DEPTH + x0] + (d[y0 * DEPTH + x1] - d[y0 * DEPTH + x0]) * tx
            val b = d[y1 * DEPTH + x0] + (d[y1 * DEPTH + x1] - d[y1 * DEPTH + x0]) * tx
            a + (b - a) * ty
        }
    }
}
