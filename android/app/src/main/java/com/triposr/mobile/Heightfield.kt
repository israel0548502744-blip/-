package com.triposr.mobile

import kotlin.math.max
import kotlin.math.min

/**
 * Solids built from 2D height data: lithophanes and reliefs (smooth height map) and QR codes
 * (blocky columns). Every mesh is closed (top, walls and bottom share their edges), built in
 * millimetres with Z up and then returned in the app's Y-up viewer space via [toViewer].
 */
object Heightfield {

    /**
     * Smooth solid: the top surface follows [heights] (mm, w×h samples, row 0 at the back),
     * the bottom is flat at z = 0. [colors] (RGB 0..1 per sample) is optional.
     */
    fun smooth(heights: FloatArray, w: Int, h: Int, cellMm: Float, colors: FloatArray? = null): Mesh {
        val n = w * h
        val pos = FloatArray(n * 2 * 3)
        val col = FloatArray(n * 2 * 3)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            for ((layer, z) in listOf(0 to heights[i], 1 to 0f)) {
                val v = (layer * n + i) * 3
                pos[v] = x * cellMm; pos[v + 1] = (h - 1 - y) * cellMm; pos[v + 2] = z
                if (colors != null) System.arraycopy(colors, i * 3, col, v, 3) else { col[v] = 0.85f; col[v + 1] = 0.85f; col[v + 2] = 0.88f }
            }
        }
        val tri = IntList()
        fun quad(a: Int, b: Int, c: Int, d: Int) { tri.add(a); tri.add(b); tri.add(c); tri.add(a); tri.add(c); tri.add(d) }
        fun top(x: Int, y: Int) = y * w + x
        fun bot(x: Int, y: Int) = n + y * w + x
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            // Top faces +z, bottom faces -z (positions have y flipped, so winding is mirrored).
            quad(top(x, y), top(x, y + 1), top(x + 1, y + 1), top(x + 1, y))
            quad(bot(x, y), bot(x + 1, y), bot(x + 1, y + 1), bot(x, y + 1))
        }
        for (x in 0 until w - 1) {
            quad(top(x, 0), top(x + 1, 0), bot(x + 1, 0), bot(x, 0))
            quad(top(x + 1, h - 1), top(x, h - 1), bot(x, h - 1), bot(x + 1, h - 1))
        }
        for (y in 0 until h - 1) {
            quad(top(0, y + 1), top(0, y), bot(0, y), bot(0, y + 1))
            quad(top(w - 1, y), top(w - 1, y + 1), bot(w - 1, y + 1), bot(w - 1, y))
        }
        return finish(pos, col, tri.toArray())
    }

    /**
     * Blocky solid: one flat-topped column per cell, heights taken from [levels] (a small set
     * of distinct values, e.g. plate and raised modules). Walls are split at every level so
     * neighbouring faces always share whole edges (no T-junctions), which keeps it watertight.
     */
    fun columns(cellHeight: FloatArray, w: Int, h: Int, cellMm: Float, cellColor: (Int) -> FloatArray): Mesh {
        val levels = (cellHeight.toList() + 0f).distinct().sorted()
        val pos = FloatList(); val col = FloatList(); val tri = IntList()
        val ids = HashMap<Long, Int>()
        // A vertex is identified by grid corner + level + the colour group it belongs to.
        fun vid(cx: Int, cy: Int, z: Float, color: FloatArray): Int {
            val li = levels.indexOf(z)
            val key = ((cx.toLong() * (h + 1) + cy) * levels.size + li) * 4 + if (color[0] < 0.5f) 1 else 0
            return ids.getOrPut(key) {
                pos.add(cx * cellMm); pos.add((h - cy) * cellMm); pos.add(z)
                col.add(color[0]); col.add(color[1]); col.add(color[2])
                pos.size / 3 - 1
            }
        }
        fun quad(a: Int, b: Int, c: Int, d: Int) { tri.add(a); tri.add(b); tri.add(c); tri.add(a); tri.add(c); tri.add(d) }
        fun hAt(x: Int, y: Int) = if (x in 0 until w && y in 0 until h) cellHeight[y * w + x] else 0f
        val white = floatArrayOf(0.94f, 0.94f, 0.94f)
        for (y in 0 until h) for (x in 0 until w) {
            val z = hAt(x, y); val c = cellColor(y * w + x)
            // corners: (x,y) top-left in grid coords; y grows towards the front (down in viewer).
            quad(vid(x, y, z, c), vid(x, y + 1, z, c), vid(x + 1, y + 1, z, c), vid(x + 1, y, z, c))
            quad(vid(x, y, 0f, white), vid(x + 1, y, 0f, white), vid(x + 1, y + 1, 0f, white), vid(x, y + 1, 0f, white))
            // Walls towards each neighbour that is lower (outside the grid counts as 0).
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val zn = hAt(x + dx, y + dy)
                if (zn >= z) continue
                // edge endpoints, ordered so the wall faces outward (towards the neighbour)
                val (ax, ay, bx, by) = when {
                    dx == 1 -> listOf(x + 1, y + 1, x + 1, y)
                    dx == -1 -> listOf(x, y, x, y + 1)
                    dy == 1 -> listOf(x, y + 1, x + 1, y + 1)
                    else -> listOf(x + 1, y, x, y)
                }
                val span = levels.filter { it in zn..z }
                for (k in 0 until span.size - 1) {
                    val lo = span[k]; val hi = span[k + 1]
                    val cl = if (hi > hAt(x + dx, y + dy) && lo >= levelBelowTop(levels, z)) c else white
                    // Lower wall parts belong to the plate (white), the top step to the cell colour.
                    quad(vid(ax, ay, hi, cl), vid(ax, ay, lo, cl), vid(bx, by, lo, cl), vid(bx, by, hi, cl))
                }
            }
        }
        return finish(pos.toArray(), col.toArray(), tri.toArray())
    }

    private fun levelBelowTop(levels: List<Float>, z: Float): Float {
        val i = levels.indexOf(z)
        return if (i > 0) levels[i - 1] else 0f
    }

    private fun finish(pos: FloatArray, col: FloatArray, tri: IntArray): Mesh {
        // Fix winding so faces point outward (positive signed volume), then move to viewer space.
        if (Printability.signedVolume(pos, tri) < 0) for (t in 0 until tri.size / 3) {
            val a = tri[t * 3 + 1]; tri[t * 3 + 1] = tri[t * 3 + 2]; tri[t * 3 + 2] = a
        }
        return toViewer(pos, col, tri)
    }

    /** Z-up millimetres -> the app's Y-up space, scaled so the longest side is 1.6 units. */
    fun toViewer(pos: FloatArray, col: FloatArray, tri: IntArray): Mesh {
        val nv = pos.size / 3
        val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until nv) for (c in 0..2) { lo[c] = min(lo[c], pos[v * 3 + c]); hi[c] = max(hi[c], pos[v * 3 + c]) }
        val s = 1.6f / max(hi[0] - lo[0], max(hi[1] - lo[1], hi[2] - lo[2])).coerceAtLeast(1e-6f)
        val cx = (lo[0] + hi[0]) / 2; val cy = (lo[1] + hi[1]) / 2; val cz = (lo[2] + hi[2]) / 2
        val out = FloatArray(pos.size)
        for (v in 0 until nv) {
            val x = pos[v * 3] - cx; val y = pos[v * 3 + 1] - cy; val z = pos[v * 3 + 2] - cz
            out[v * 3] = x * s; out[v * 3 + 1] = z * s; out[v * 3 + 2] = -y * s
        }
        return Mesh(out, MeshTools.normals(out, tri), col, tri)
    }

    // ------------------------------------------------------------------ products

    /** Rec. 709 luminance of ARGB pixels, 0..1. */
    fun luminance(px: IntArray) = FloatArray(px.size) {
        val c = px[it]
        (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
    }

    /**
     * Lithophane: a thin plate whose thickness follows the picture (dark = thick), so it shows
     * the image when lit from behind. [px] is the image already resized to w×h samples.
     */
    fun lithophane(px: IntArray, w: Int, h: Int, widthMm: Float = 100f, minMm: Float = 0.8f, maxMm: Float = 3.0f,
                   frameMm: Float = 3f): Mesh {
        val cell = widthMm / (w - 1)
        val lum = luminance(px)
        val frame = (frameMm / cell).toInt()
        val heights = FloatArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x < frame || y < frame || x >= w - frame || y >= h - frame) maxMm
            else minMm + (1f - lum[i]) * (maxMm - minMm)
        }
        // Preview colours as the lit lithophane would look: thin = bright.
        val colors = FloatArray(w * h * 3) { val t = (heights[it / 3] - minMm) / (maxMm - minMm); 0.95f - 0.75f * t }
        return smooth(heights, w, h, cell, colors)
    }

    /**
     * Relief: a base plate with the subject raised by its depth ([depth] 0..1, 1 = nearest),
     * coloured with the photo. [mask] (0..1, optional) keeps the background flat.
     */
    fun relief(depth: FloatArray, px: IntArray, w: Int, h: Int, mask: FloatArray?, widthMm: Float = 100f,
               baseMm: Float = 2f, reliefMm: Float = 8f): Mesh {
        val cell = widthMm / (w - 1)
        val heights = FloatArray(w * h) { i -> baseMm + reliefMm * depth[i] * (mask?.get(i) ?: 1f) }
        val colors = FloatArray(w * h * 3)
        for (i in 0 until w * h) {
            val c = px[i]
            colors[i * 3] = ((c shr 16) and 0xFF) / 255f; colors[i * 3 + 1] = ((c shr 8) and 0xFF) / 255f; colors[i * 3 + 2] = (c and 0xFF) / 255f
        }
        return smooth(heights, w, h, cell, colors)
    }

    /**
     * QR code plate: [modules] is the QR matrix (true = dark) including its quiet zone. Dark
     * modules stand [raiseMm] above a [plateMm] plate, so a filament change at that height
     * prints them in a second colour.
     */
    fun qr(modules: BooleanArray, size: Int, moduleMm: Float = 2.5f, plateMm: Float = 2f, raiseMm: Float = 1f): Mesh {
        val dark = floatArrayOf(0.08f, 0.08f, 0.1f); val light = floatArrayOf(0.94f, 0.94f, 0.94f)
        val heights = FloatArray(size * size) { if (modules[it]) plateMm + raiseMm else plateMm }
        return columns(heights, size, size, moduleMm) { if (modules[it]) dark else light }
    }

    /** Normalises raw depth to 0..1 using the 2nd and 98th percentiles (robust to outliers). */
    fun normalizeDepth(d: FloatArray, mask: FloatArray? = null): FloatArray {
        val vals = d.filterIndexed { i, _ -> mask == null || mask[i] > 0.5f }.sorted()
        if (vals.isEmpty()) return FloatArray(d.size)
        val lo = vals[(vals.size * 0.02).toInt()]; val hi = vals[((vals.size - 1) * 0.98).toInt()]
        val span = (hi - lo).takeIf { it > 1e-6f } ?: 1f
        return FloatArray(d.size) { ((d[it] - lo) / span).coerceIn(0f, 1f) }
    }
}
