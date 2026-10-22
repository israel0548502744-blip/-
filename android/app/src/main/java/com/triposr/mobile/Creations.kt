package com.triposr.mobile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Print products that don't need the 3D model: lithophane (light shines through a photo),
 * relief (photo raised by its depth, needs the depth extra) and QR code plates.
 * All are made with [Heightfield] at 100 mm on the longest side, the size the print export uses,
 * so thicknesses stay exactly as chosen.
 */
object Creations {
    enum class Kind(val label: String) { LITHOPHANE("ליתופן"), RELIEF("תבליט"), QR("קוד QR") }

    class Options(
        val frame: Boolean = true,
        val maxMm: Float = 3f,
        val invert: Boolean = false,
        val reliefMm: Float = 8f,
        val flatBackground: Boolean = true,
    )

    /** Status line while something is being built, null when idle. */
    var busy by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null)

    private const val SIZE_MM = Printability.TARGET_MM
    private const val SAMPLES = 300 // along the longest side: 0.33 mm per sample at 100 mm

    fun create(kind: Kind, image: Bitmap?, text: String, o: Options) {
        if (busy != null) return
        busy = "יוצר ${kind.label}…"; error = null
        val app = AppState.app
        AppState.scope.launch {
            try {
                val t0 = System.nanoTime()
                val (mesh, thumb) = when (kind) {
                    Kind.LITHOPHANE -> lithophane(image!!, o)
                    Kind.RELIEF -> relief(image!!, o)
                    Kind.QR -> qr(text)
                }
                val seconds = (System.nanoTime() - t0) / 1e9
                val p = AppState.saveProject(mesh, thumb, "—", kind.label, seconds, emptyMap(),
                    name = "${kind.label} ${AppState.projects.count { it.quality == kind.label } + 1}")
                CrashLog.log("Created ${kind.name} in %.1fs: ${mesh.triangleCount} triangles".format(seconds))
                withContext(Dispatchers.Main) { AppState.showNew(mesh, p) }
            } catch (e: Throwable) {
                CrashLog.error("create ${kind.name}", e)
                error = (e.message ?: e.javaClass.simpleName).take(200)
            } finally {
                busy = null
            }
        }
    }

    /** Resized so the longest side has [SAMPLES] samples. */
    private fun sample(src: Bitmap): Triple<IntArray, Int, Int> {
        val s = SAMPLES.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * s).toInt().coerceAtLeast(8); val h = (src.height * s).toInt().coerceAtLeast(8)
        val b = Bitmap.createScaledBitmap(src, w, h, true)
        return Triple(IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }, w, h)
    }

    private fun widthFor(w: Int, h: Int) = SIZE_MM * (w - 1) / (maxOf(w, h) - 1)

    private fun lithophane(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        var (px, w, h) = sample(src)
        if (o.invert) px = IntArray(px.size) { px[it] xor 0x00FFFFFF }
        val mesh = Heightfield.lithophane(px, w, h, widthMm = widthFor(w, h), maxMm = o.maxMm, frameMm = if (o.frame) 3f else 0f)
        return mesh to grey(src)
    }

    private fun relief(src: Bitmap, o: Options): Pair<Mesh, Bitmap> {
        val app = AppState.app
        if (!Extras.ready(app, Extras.DEPTH)) throw IllegalStateException("תבליט צריך את \"מפת עומק\" מהשיפורים בהגדרות")
        val (px, w, h) = sample(src)
        val raw = Enhance.depth(app, src, w, h)
        val mask = if (o.flatBackground) {
            val cut = Enhance.removeBackground(app, src) { AppState.segmenter() }
            val small = Bitmap.createScaledBitmap(cut, w, h, true)
            IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }.map { Color.alpha(it) / 255f }.toFloatArray()
        } else null
        val depth = Heightfield.normalizeDepth(raw, mask)
        val mesh = Heightfield.relief(depth, px, w, h, mask, widthMm = widthFor(w, h), reliefMm = o.reliefMm)
        return mesh to src
    }

    fun qrMatrix(text: String): Pair<BooleanArray, Int> {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val n = m.width
        return BooleanArray(n * n) { m.get(it % n, it / n) } to n
    }

    fun qrBitmap(text: String, px: Int = 8): Bitmap {
        val (mod, n) = qrMatrix(text)
        val pixels = IntArray(n * px * n * px) { i -> val x = i % (n * px) / px; val y = i / (n * px) / px; if (mod[y * n + x]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, n * px, n * px, Bitmap.Config.ARGB_8888)
    }

    private fun qr(text: String): Pair<Mesh, Bitmap> {
        if (text.isBlank()) throw IllegalStateException("צריך להכניס קישור או טקסט")
        val (mod, n) = qrMatrix(text.trim())
        return Heightfield.qr(mod, n, moduleMm = SIZE_MM / n) to qrBitmap(text.trim())
    }

    private fun grey(src: Bitmap): Bitmap {
        val s = 256f / maxOf(src.width, src.height)
        val b = Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        for (i in px.indices) { val l = (Heightfield.luminance(intArrayOf(px[i]))[0] * 255).toInt(); px[i] = Color.rgb(l, l, l) }
        return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
    }
}
