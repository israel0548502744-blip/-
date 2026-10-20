package com.triposr.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.FloatBuffer

/**
 * Port of TripoSR's run.py preprocessing:
 *   remove_background (rembg / U2-Net) -> resize_foreground(0.85) -> composite on grey 0.5 -> 512x512.
 */
object Preprocess {
    private const val SEG_SIZE = 320
    private const val FOREGROUND_RATIO = 0.85f

    /** Returns a copy of [src] whose alpha channel is the foreground mask. */
    fun removeBackground(src: Bitmap, segmenter: OrtSession): Bitmap {
        if (hasTransparency(src)) return src.copy(Bitmap.Config.ARGB_8888, true)

        // rembg: resize to 320, divide by the image max, ImageNet mean/std.
        val small = Bitmap.createScaledBitmap(src, SEG_SIZE, SEG_SIZE, true)
        val px = IntArray(SEG_SIZE * SEG_SIZE).also { small.getPixels(it, 0, SEG_SIZE, 0, 0, SEG_SIZE, SEG_SIZE) }
        var maxV = 1
        for (c in px) maxV = maxOf(maxV, Color.red(c), Color.green(c), Color.blue(c))
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val plane = SEG_SIZE * SEG_SIZE
        val input = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = px[i]
            input[i] = (Color.red(c).toFloat() / maxV - mean[0]) / std[0]
            input[plane + i] = (Color.green(c).toFloat() / maxV - mean[1]) / std[1]
            input[2 * plane + i] = (Color.blue(c).toFloat() / maxV - mean[2]) / std[2]
        }
        val env = OrtEnvironment.getEnvironment()
        val pred = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SEG_SIZE.toLong(), SEG_SIZE.toLong())).use { t ->
            segmenter.run(mapOf(segmenter.inputNames.first() to t)).use { out ->
                val fb = (out[0] as OnnxTensor).floatBuffer
                FloatArray(plane).also { fb.get(it) }
            }
        }
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (v in pred) { lo = minOf(lo, v); hi = maxOf(hi, v) }
        val maskPx = IntArray(plane) { i ->
            val a = (((pred[i] - lo) / maxOf(hi - lo, 1e-6f)) * 255f).toInt().coerceIn(0, 255)
            Color.argb(a, 0, 0, 0)
        }
        val mask = Bitmap.createBitmap(maskPx, SEG_SIZE, SEG_SIZE, Bitmap.Config.ARGB_8888)
        val maskFull = Bitmap.createScaledBitmap(mask, src.width, src.height, true)

        val w = src.width; val h = src.height
        val rgb = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val alpha = IntArray(w * h).also { maskFull.getPixels(it, 0, w, 0, 0, w, h) }
        for (i in rgb.indices) rgb[i] = (rgb[i] and 0x00FFFFFF) or (Color.alpha(alpha[i]) shl 24)
        return Bitmap.createBitmap(rgb, w, h, Bitmap.Config.ARGB_8888)
    }

    /** resize_foreground + grey composite + resize. Returns the 512x512 preview bitmap. */
    fun frameForeground(rgba: Bitmap, size: Int = TripoEngine.IMAGE_SIZE): Bitmap {
        val w = rgba.width; val h = rgba.height
        val px = IntArray(w * h).also { rgba.getPixels(it, 0, w, 0, 0, w, h) }
        var x1 = w; var y1 = h; var x2 = -1; var y2 = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (Color.alpha(px[y * w + x]) > 127) {
                x1 = minOf(x1, x); x2 = maxOf(x2, x); y1 = minOf(y1, y); y2 = maxOf(y2, y)
            }
        }
        if (x2 < 0) { x1 = 0; y1 = 0; x2 = w - 1; y2 = h - 1 }
        val fgW = x2 - x1 + 1; val fgH = y2 - y1 + 1
        val square = maxOf(fgW, fgH)
        val padded = (square / FOREGROUND_RATIO).toInt()

        // Place the cropped foreground in the centre of a padded square, then scale onto grey.
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(128, 128, 128))
        val scale = size.toFloat() / padded
        val left = ((padded - fgW) / 2f) * scale
        val top = ((padded - fgH) / 2f) * scale
        val cropped = Bitmap.createBitmap(rgba, x1, y1, fgW, fgH)
        val dstW = (fgW * scale).toInt().coerceAtLeast(1)
        val dstH = (fgH * scale).toInt().coerceAtLeast(1)
        canvas.drawBitmap(downscale(cropped, dstW, dstH), null,
            Rect(left.toInt(), top.toInt(), left.toInt() + dstW, top.toInt() + dstH),
            Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun toChw(bmp: Bitmap): FloatArray {
        val s = bmp.width
        val px = IntArray(s * s).also { bmp.getPixels(it, 0, s, 0, 0, s, s) }
        val plane = s * s
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            out[i] = Color.red(px[i]) / 255f
            out[plane + i] = Color.green(px[i]) / 255f
            out[2 * plane + i] = Color.blue(px[i]) / 255f
        }
        return out
    }

    /** Halve repeatedly before the final bilinear resize so large photos don't alias. */
    private fun downscale(src: Bitmap, w: Int, h: Int): Bitmap {
        var cur = src
        while (cur.width / 2 >= w && cur.height / 2 >= h) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
        }
        return Bitmap.createScaledBitmap(cur, w, h, true)
    }

    private fun hasTransparency(bmp: Bitmap): Boolean {
        if (!bmp.hasAlpha()) return false
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        return px.count { Color.alpha(it) < 128 } > px.size / 100
    }
}
