package com.triposr.mobile

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What [Printability.prepare] found and fixed. Lengths are millimetres. */
class PrintReport(
    val sizeMm: FloatArray,           // width (x), depth (y), height (z)
    val degenerateRemoved: Int,
    val floatersRemoved: Int,         // small loose parts dropped
    val nonManifoldBefore: Int,       // edges shared by more than two triangles
    val nonManifoldAfter: Int,
    val holesFilled: Int,
    val openEdgesAfter: Int,
    val watertight: Boolean,
    val volumeCm3: Double,
    val minWallMm: Float,             // 2nd percentile of sampled wall thickness (robust minimum)
    val thinShare: Float,             // share of sampled points thinner than [Printability.THIN_MM]
    val triangles: Int,
)

/** A print-ready copy of a mesh: millimetres, Z up, standing on z = 0, centred on x/y. */
class PrintModel(val mesh: Mesh, val report: PrintReport, val thickness: FloatArray)

/**
 * Turns a reconstructed mesh into something a slicer accepts: removes degenerate triangles and
 * floating debris, splits non-manifold edges/vertices (Surface Nets can make edges with four
 * triangles), fills holes, orients outward, scales to a fixed size, and measures wall thickness.
 * Pure Kotlin so it is tested on the JVM against real reconstructions.
 */
object Printability {
    /** Longest side of the printed object. A common figurine size that fits every printer bed. */
    const val TARGET_MM = 100f
    /** Walls thinner than this are flagged (two 0.4 mm nozzle perimeters plus margin). */
    const val THIN_MM = 1.0f

    @Volatile private var cache: Pair<Mesh, PrintModel>? = null

    /** [prepare] for the default size, remembered for the last mesh (the result screen and exports share it). */
    fun prepared(src: Mesh): PrintModel {
        cache?.let { (m, pm) -> if (m === src) return pm }
        return prepare(src).also { cache = src to it }
    }

    fun prepare(src: Mesh, targetMm: Float = TARGET_MM): PrintModel {
        var pos = src.positions.copyOf()
        var tris = src.indices.copyOf()

        // 1. Degenerate and duplicate triangles.
        val before = tris.size / 3
        tris = dropDegenerate(pos, tris)
        val degenerate = before - tris.size / 3

        // 2. Loose debris: keep parts with at least 2% of the largest part's triangles.
        val (kept, floaters) = dropFloaters(pos.size / 3, tris)
        tris = kept

        // 3. Non-manifold edges / pinched vertices: split each vertex into one copy per fan.
        val nmBefore = countEdges(tris).nonManifold
        val split = splitNonManifold(pos, tris)
        pos = split.first; tris = split.second

        // 4. Holes: fan-fill every boundary loop around its centroid.
        val filled = fillHoles(pos, tris)
        pos = filled.positions; tris = filled.indices

        // 5. Outward orientation (positive signed volume).
        if (signedVolume(pos, tris) < 0) for (t in 0 until tris.size / 3) {
            val a = tris[t * 3 + 1]; tris[t * 3 + 1] = tris[t * 3 + 2]; tris[t * 3 + 2] = a
        }

        // 6. Y-up (glTF) -> Z-up (slicers), scale to millimetres, put on the bed.
        val nv = pos.size / 3
        val out = FloatArray(pos.size)
        for (v in 0 until nv) {
            out[v * 3] = pos[v * 3]; out[v * 3 + 1] = -pos[v * 3 + 2]; out[v * 3 + 2] = pos[v * 3 + 1]
        }
        val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until nv) for (c in 0..2) { lo[c] = min(lo[c], out[v * 3 + c]); hi[c] = max(hi[c], out[v * 3 + c]) }
        val longest = max(hi[0] - lo[0], max(hi[1] - lo[1], hi[2] - lo[2])).coerceAtLeast(1e-6f)
        val s = targetMm / longest
        val cx = (lo[0] + hi[0]) / 2; val cy = (lo[1] + hi[1]) / 2
        for (v in 0 until nv) {
            out[v * 3] = (out[v * 3] - cx) * s; out[v * 3 + 1] = (out[v * 3 + 1] - cy) * s; out[v * 3 + 2] = (out[v * 3 + 2] - lo[2]) * s
        }
        val size = FloatArray(3) { (hi[it] - lo[it]) * s }

        val normals = MeshTools.normals(out, tris)
        // Judge the result the way a slicer reads an STL: vertices merged by position.
        val edges = countEdges(weld(out, tris))
        val colors = FloatArray(out.size) { 0.8f }
        val mesh = Mesh(out, normals, colors, tris)
        val thickness = wallThickness(mesh)
        val sampled = thickness.filter { it.isFinite() }.sorted()
        val minWall = if (sampled.isEmpty()) Float.NaN else sampled[(sampled.size * 0.02f).toInt().coerceAtMost(sampled.size - 1)]
        val thin = if (sampled.isEmpty()) 0f else sampled.count { it < THIN_MM } / sampled.size.toFloat()

        val report = PrintReport(size, degenerate, floaters, nmBefore, edges.nonManifold, filled.holes, edges.open,
            edges.open == 0 && edges.nonManifold == 0 && edges.misoriented == 0,
            abs(signedVolume(out, tris)) / 1000.0, minWall, thin, tris.size / 3)
        return PrintModel(mesh, report, thickness)
    }

    // ---------------------------------------------------------------- topology

    class EdgeStats(val open: Int, val nonManifold: Int, val misoriented: Int)

    private fun key(a: Int, b: Int) = (min(a, b).toLong() shl 32) or max(a, b).toLong()

    fun countEdges(tris: IntArray): EdgeStats {
        val count = HashMap<Long, Int>(tris.size)
        val dir = HashMap<Long, Int>(tris.size)  // +1 for a<b direction, -1 for b<a, summed
        for (t in 0 until tris.size / 3) for (e in 0..2) {
            val a = tris[t * 3 + e]; val b = tris[t * 3 + (e + 1) % 3]
            val k = key(a, b)
            count[k] = (count[k] ?: 0) + 1
            dir[k] = (dir[k] ?: 0) + if (a < b) 1 else -1
        }
        var open = 0; var nm = 0; var mis = 0
        for ((k, c) in count) when {
            c == 1 -> open++
            c > 2 -> nm++
            dir[k] != 0 -> mis++
        }
        return EdgeStats(open, nm, mis)
    }

    /** Indices remapped so vertices at the same position (to 1e-4 mm) share one id. */
    fun weld(p: FloatArray, tris: IntArray): IntArray {
        val ids = HashMap<Triple<Long, Long, Long>, Int>(p.size / 3)
        val map = IntArray(p.size / 3) { v ->
            val k = Triple(Math.round(p[v * 3] * 1e4), Math.round(p[v * 3 + 1] * 1e4), Math.round(p[v * 3 + 2] * 1e4))
            ids.getOrPut(k) { ids.size }
        }
        return IntArray(tris.size) { map[tris[it]] }
    }

    private fun dropDegenerate(pos: FloatArray, tris: IntArray): IntArray {
        val out = IntList()
        val seen = HashSet<Long>()
        for (t in 0 until tris.size / 3) {
            val a = tris[t * 3]; val b = tris[t * 3 + 1]; val c = tris[t * 3 + 2]
            if (a == b || b == c || a == c) continue
            val ux = pos[b * 3] - pos[a * 3]; val uy = pos[b * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b * 3 + 2] - pos[a * 3 + 2]
            val vx = pos[c * 3] - pos[a * 3]; val vy = pos[c * 3 + 1] - pos[a * 3 + 1]; val vz = pos[c * 3 + 2] - pos[a * 3 + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            if (nx * nx + ny * ny + nz * nz < 1e-24f) continue
            val lo = minOf(a, b, c).toLong(); val hi = maxOf(a, b, c).toLong(); val mid = a.toLong() + b + c - lo - hi
            if (!seen.add((lo shl 42) or (mid shl 21) or hi)) continue
            out.add(a); out.add(b); out.add(c)
        }
        return out.toArray()
    }

    private fun dropFloaters(nv: Int, tris: IntArray): Pair<IntArray, Int> {
        val parent = IntArray(nv) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
        for (t in 0 until tris.size / 3) {
            val a = find(tris[t * 3]); val b = find(tris[t * 3 + 1]); val c = find(tris[t * 3 + 2])
            parent[b] = a; parent[find(c)] = a
        }
        val size = HashMap<Int, Int>()
        for (t in 0 until tris.size / 3) { val r = find(tris[t * 3]); size[r] = (size[r] ?: 0) + 1 }
        val largest = size.values.maxOrNull() ?: return tris to 0
        val keep = size.filterValues { it >= largest * 0.02 }.keys
        val out = IntList()
        for (t in 0 until tris.size / 3) if (find(tris[t * 3]) in keep) { out.add(tris[t * 3]); out.add(tris[t * 3 + 1]); out.add(tris[t * 3 + 2]) }
        return out.toArray() to (size.size - keep.size)
    }

    /**
     * Around each vertex, groups its triangles into fans connected through manifold edges (edges
     * with exactly two triangles). Every fan after the first gets its own copy of the vertex, so
     * two sheets that only touch along an edge or at a point become separate, each closed.
     */
    private fun splitNonManifold(pos: FloatArray, tris: IntArray): Pair<FloatArray, IntArray> {
        val nt = tris.size / 3
        val edgeCount = HashMap<Long, Int>(tris.size)
        for (t in 0 until nt) for (e in 0..2) {
            val k = key(tris[t * 3 + e], tris[t * 3 + (e + 1) % 3]); edgeCount[k] = (edgeCount[k] ?: 0) + 1
        }
        val nv = pos.size / 3
        // vertex -> incident corners (CSR)
        val deg = IntArray(nv + 1)
        for (c in tris) deg[c + 1]++
        for (v in 0 until nv) deg[v + 1] += deg[v]
        val corners = IntArray(tris.size); val fill = deg.copyOf()
        for (i in tris.indices) corners[fill[tris[i]]++] = i

        val newPos = FloatList(); for (x in pos) newPos.add(x)
        val out = tris.copyOf()
        val fan = HashMap<Int, Int>()
        for (v in 0 until nv) {
            val s = deg[v]; val e = deg[v + 1]
            if (e - s <= 1) continue
            // union-find over this vertex's triangles
            fan.clear()
            val tri = IntArray(e - s) { corners[s + it] / 3 }
            val parent = IntArray(tri.size) { it }
            fun find(x: Int): Int { var r = x; while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }; return r }
            // two triangles at v are joined if they share an edge (v, w) that is manifold
            val byOther = HashMap<Int, Int>()
            for ((i, t) in tri.withIndex()) for (k in 0..2) {
                val w = tris[t * 3 + k]
                if (w == v || edgeCount[key(v, w)] != 2) continue
                val prev = byOther.put(w, i)
                if (prev != null) parent[find(prev)] = find(i)
            }
            var first = -1
            val copies = ArrayList<Int>()
            for ((i, t) in tri.withIndex()) {
                val root = find(i)
                if (first < 0) first = root
                if (root == first) continue
                val copy = fan.getOrPut(root) {
                    val id = newPos.size / 3
                    newPos.add(pos[v * 3]); newPos.add(pos[v * 3 + 1]); newPos.add(pos[v * 3 + 2])
                    copies += id
                    id
                }
                for (k in 0..2) if (out[t * 3 + k] == v) out[t * 3 + k] = copy
            }
            if (copies.isEmpty()) continue
            // STL has no shared vertices: slicers re-merge points at the same position, which
            // would undo the split. Pull every fan's vertex 5% towards its own neighbours.
            val sums = HashMap<Int, FloatArray>()
            for (t in tri) {
                val owner = (0..2).map { out[t * 3 + it] }.firstOrNull { it == v || it in copies } ?: continue
                val acc = sums.getOrPut(owner) { FloatArray(4) }
                for (k in 0..2) {
                    val w = out[t * 3 + k]
                    if (w == owner) continue
                    for (c in 0..2) acc[c] += if (w < nv) pos[w * 3 + c] else newPos[w * 3 + c]
                    acc[3]++
                }
            }
            for ((owner, acc) in sums) {
                if (acc[3] == 0f) continue
                for (c in 0..2) {
                    val cur = newPos[owner * 3 + c]
                    newPos[owner * 3 + c] = cur + (acc[c] / acc[3] - cur) * 0.05f
                }
            }
        }
        return newPos.toArray() to out
    }

    class Filled(val positions: FloatArray, val indices: IntArray, val holes: Int)

    private fun fillHoles(pos: FloatArray, tris: IntArray): Filled {
        // Directed boundary edges a->b (as used by their only triangle).
        val count = HashMap<Long, Int>(tris.size)
        for (t in 0 until tris.size / 3) for (e in 0..2) {
            val k = key(tris[t * 3 + e], tris[t * 3 + (e + 1) % 3]); count[k] = (count[k] ?: 0) + 1
        }
        val next = HashMap<Int, Int>()
        for (t in 0 until tris.size / 3) for (e in 0..2) {
            val a = tris[t * 3 + e]; val b = tris[t * 3 + (e + 1) % 3]
            if (count[key(a, b)] == 1) next[b] = a  // walk the hole in the opposite direction
        }
        if (next.isEmpty()) return Filled(pos, tris, 0)
        val p = FloatList(); for (x in pos) p.add(x)
        val out = IntList(); for (x in tris) out.add(x)
        var holes = 0
        val visited = HashSet<Int>()
        for (start in next.keys.toList()) {
            if (start in visited) continue
            val loop = ArrayList<Int>()
            var v = start
            while (v !in visited) {
                visited += v; loop += v
                v = next[v] ?: break
            }
            if (loop.size < 3 || v != start) continue
            var cx = 0f; var cy = 0f; var cz = 0f
            for (u in loop) { cx += pos[u * 3]; cy += pos[u * 3 + 1]; cz += pos[u * 3 + 2] }
            val c = p.size / 3
            p.add(cx / loop.size); p.add(cy / loop.size); p.add(cz / loop.size)
            for (i in loop.indices) { out.add(loop[i]); out.add(loop[(i + 1) % loop.size]); out.add(c) }
            holes++
        }
        return Filled(p.toArray(), out.toArray(), holes)
    }

    fun signedVolume(p: FloatArray, tris: IntArray): Double {
        var v = 0.0
        for (t in 0 until tris.size / 3) {
            val a = tris[t * 3] * 3; val b = tris[t * 3 + 1] * 3; val c = tris[t * 3 + 2] * 3
            v += (p[a].toDouble() * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) -
                p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) +
                p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])) / 6.0
        }
        return v
    }

    // ---------------------------------------------------------------- wall thickness

    /**
     * Thickness at each vertex: distance along the inward normal to the opposite surface, found
     * with a uniform-grid ray cast. NaN where the ray escapes (only happens on open meshes).
     * Large meshes are subsampled (every k-th vertex), the rest stay NaN.
     */
    fun wallThickness(m: Mesh, maxSamples: Int = 60000): FloatArray {
        val nv = m.vertexCount; val p = m.positions; val idx = m.indices; val nt = m.triangleCount
        val res = FloatArray(nv) { Float.NaN }
        if (nt == 0) return res
        val lo = FloatArray(3) { Float.MAX_VALUE }; val hi = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until nv) for (c in 0..2) { lo[c] = min(lo[c], p[v * 3 + c]); hi[c] = max(hi[c], p[v * 3 + c]) }
        val g = max(8, min(96, (Math.cbrt(nt.toDouble()) * 1.5).toInt()))
        val cell = FloatArray(3) { ((hi[it] - lo[it]) / g).coerceAtLeast(1e-6f) }
        fun ci(x: Float, c: Int) = floor((x - lo[c]) / cell[c]).toInt().coerceIn(0, g - 1)
        // triangles per cell (CSR), by bounding box
        val counts = IntArray(g * g * g + 1)
        fun forCells(t: Int, f: (Int) -> Unit) {
            val a = idx[t * 3] * 3; val b = idx[t * 3 + 1] * 3; val c = idx[t * 3 + 2] * 3
            val x0 = ci(min(p[a], min(p[b], p[c])), 0); val x1 = ci(max(p[a], max(p[b], p[c])), 0)
            val y0 = ci(min(p[a + 1], min(p[b + 1], p[c + 1])), 1); val y1 = ci(max(p[a + 1], max(p[b + 1], p[c + 1])), 1)
            val z0 = ci(min(p[a + 2], min(p[b + 2], p[c + 2])), 2); val z1 = ci(max(p[a + 2], max(p[b + 2], p[c + 2])), 2)
            for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) f((x * g + y) * g + z)
        }
        for (t in 0 until nt) forCells(t) { counts[it + 1]++ }
        for (i in 0 until g * g * g) counts[i + 1] += counts[i]
        val list = IntArray(counts[g * g * g]); val fill = counts.copyOf()
        for (t in 0 until nt) forCells(t) { list[fill[it]++] = t }

        val stride = max(1, nv / maxSamples)
        val n = m.normals
        val stamp = IntArray(nt) { -1 }
        for (v in 0 until nv step stride) {
            val ox = p[v * 3]; val oy = p[v * 3 + 1]; val oz = p[v * 3 + 2]
            val dx = -n[v * 3]; val dy = -n[v * 3 + 1]; val dz = -n[v * 3 + 2]
            if (dx == 0f && dy == 0f && dz == 0f) continue
            // 3D DDA through the grid
            var x = ci(ox, 0); var y = ci(oy, 1); var z = ci(oz, 2)
            val sx = if (dx > 0) 1 else -1; val sy = if (dy > 0) 1 else -1; val sz = if (dz > 0) 1 else -1
            fun tMax(o: Float, d: Float, i: Int, c: Int): Float {
                if (d == 0f) return Float.MAX_VALUE
                val edge = lo[c] + (if (d > 0) i + 1 else i) * cell[c]
                return (edge - o) / d
            }
            var tx = tMax(ox, dx, x, 0); var ty = tMax(oy, dy, y, 1); var tz = tMax(oz, dz, z, 2)
            val ddx = if (dx != 0f) cell[0] / abs(dx) else Float.MAX_VALUE
            val ddy = if (dy != 0f) cell[1] / abs(dy) else Float.MAX_VALUE
            val ddz = if (dz != 0f) cell[2] / abs(dz) else Float.MAX_VALUE
            var best = Float.MAX_VALUE
            while (true) {
                val c = (x * g + y) * g + z
                for (k in counts[c] until counts[c + 1]) {
                    val t = list[k]
                    if (stamp[t] == v) continue
                    stamp[t] = v
                    val a = idx[t * 3]; val b = idx[t * 3 + 1]; val cc = idx[t * 3 + 2]
                    if (a == v || b == v || cc == v) continue
                    val d = rayTri(ox, oy, oz, dx, dy, dz, p, a, b, cc)
                    if (d > 1e-5f && d < best) best = d
                }
                val tNext = min(tx, min(ty, tz))
                if (best <= tNext) break
                if (tx <= ty && tx <= tz) { x += sx; tx += ddx } else if (ty <= tz) { y += sy; ty += ddy } else { z += sz; tz += ddz }
                if (x !in 0 until g || y !in 0 until g || z !in 0 until g) break
            }
            if (best < Float.MAX_VALUE) res[v] = best
        }
        return res
    }

    /** Möller–Trumbore; returns the hit distance or -1. */
    private fun rayTri(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, p: FloatArray, a: Int, b: Int, c: Int): Float {
        val ax = p[a * 3]; val ay = p[a * 3 + 1]; val az = p[a * 3 + 2]
        val e1x = p[b * 3] - ax; val e1y = p[b * 3 + 1] - ay; val e1z = p[b * 3 + 2] - az
        val e2x = p[c * 3] - ax; val e2y = p[c * 3 + 1] - ay; val e2z = p[c * 3 + 2] - az
        val px = dy * e2z - dz * e2y; val py = dz * e2x - dx * e2z; val pz = dx * e2y - dy * e2x
        val det = e1x * px + e1y * py + e1z * pz
        if (abs(det) < 1e-12f) return -1f
        val inv = 1f / det
        val tx = ox - ax; val ty = oy - ay; val tz = oz - az
        val u = (tx * px + ty * py + tz * pz) * inv
        if (u < 0f || u > 1f) return -1f
        val qx = ty * e1z - tz * e1y; val qy = tz * e1x - tx * e1z; val qz = tx * e1y - ty * e1x
        val w = (dx * qx + dy * qy + dz * qz) * inv
        if (w < 0f || u + w > 1f) return -1f
        return (e2x * qx + e2y * qy + e2z * qz) * inv
    }

    // ---------------------------------------------------------------- output

    /**
     * Copy of the print model for the on-screen viewer (Y up, unit size) with thin walls in red,
     * borderline in amber, the rest neutral.
     */
    fun thicknessPreview(pm: PrintModel): Mesh {
        val m = pm.mesh; val p = m.positions; val nv = m.vertexCount
        val s = 1.6f / Printability.TARGET_MM
        val out = FloatArray(p.size); val nor = FloatArray(p.size)
        val h = pm.report.sizeMm[2]
        for (v in 0 until nv) {
            out[v * 3] = p[v * 3] * s; out[v * 3 + 1] = (p[v * 3 + 2] - h / 2) * s; out[v * 3 + 2] = -p[v * 3 + 1] * s
            nor[v * 3] = m.normals[v * 3]; nor[v * 3 + 1] = m.normals[v * 3 + 2]; nor[v * 3 + 2] = -m.normals[v * 3 + 1]
        }
        // Spread sampled values to unsampled vertices through the triangles.
        val t = pm.thickness.copyOf()
        repeat(4) {
            for (k in 0 until m.triangleCount) {
                val a = m.indices[k * 3]; val b = m.indices[k * 3 + 1]; val c = m.indices[k * 3 + 2]
                val known = listOf(t[a], t[b], t[c]).filter { it.isFinite() }
                if (known.isEmpty()) continue
                val mn = known.min()
                if (!t[a].isFinite()) t[a] = mn; if (!t[b].isFinite()) t[b] = mn; if (!t[c].isFinite()) t[c] = mn
            }
        }
        val col = FloatArray(p.size)
        for (v in 0 until nv) {
            val x = t[v]
            val rgb = when {
                !x.isFinite() -> floatArrayOf(0.6f, 0.6f, 0.65f)
                x < THIN_MM -> floatArrayOf(0.95f, 0.25f, 0.3f)
                x < THIN_MM * 2 -> floatArrayOf(0.98f, 0.75f, 0.2f)
                else -> floatArrayOf(0.78f, 0.8f, 0.86f)
            }
            System.arraycopy(rgb, 0, col, v * 3, 3)
        }
        return Mesh(out, nor, col, m.indices)
    }

    /** 3MF (the slicers' native format): a ZIP with the mesh in millimetres. */
    fun write3mf(m: Mesh, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, text: String) {
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/></Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/></Relationships>""")
        zip.putNextEntry(ZipEntry("3D/3dmodel.model"))
        val w = zip.bufferedWriter(Charsets.UTF_8)
        w.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        w.write("""<model unit="millimeter" xml:lang="en-US" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">""")
        w.write("""<metadata name="Application">TripoSR mobile</metadata><resources><object id="1" type="model"><mesh><vertices>""")
        for (v in 0 until m.vertexCount) {
            w.write("<vertex x=\"${m.positions[v * 3]}\" y=\"${m.positions[v * 3 + 1]}\" z=\"${m.positions[v * 3 + 2]}\"/>")
        }
        w.write("</vertices><triangles>")
        for (t in 0 until m.triangleCount) {
            w.write("<triangle v1=\"${m.indices[t * 3]}\" v2=\"${m.indices[t * 3 + 1]}\" v3=\"${m.indices[t * 3 + 2]}\"/>")
        }
        w.write("""</triangles></mesh></object></resources><build><item objectid="1"/></build></model>""")
        w.flush()
        zip.closeEntry()
        zip.finish()
        out.flush()
    }
}
