package com.triposr.mobile

import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.math.max
import kotlin.math.sqrt

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    GLB("glb", "model/gltf-binary", "GLB"),
    OBJ("obj", "text/plain", "OBJ"),
    STL("stl", "model/stl", "STL"),
    PLY("ply", "application/octet-stream", "PLY"),
}

/** Mesh editing (smoothing, simplification), extra export formats and project storage. */
object MeshTools {

    fun normals(p: FloatArray, idx: IntArray): FloatArray {
        val n = FloatArray(p.size)
        for (t in 0 until idx.size / 3) {
            val a = idx[t * 3] * 3; val b = idx[t * 3 + 1] * 3; val c = idx[t * 3 + 2] * 3
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val vx = p[c] - p[a]; val vy = p[c + 1] - p[a + 1]; val vz = p[c + 2] - p[a + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            n[a] += nx; n[a + 1] += ny; n[a + 2] += nz
            n[b] += nx; n[b + 1] += ny; n[b + 2] += nz
            n[c] += nx; n[c + 1] += ny; n[c + 2] += nz
        }
        for (v in 0 until n.size / 3) {
            val l = sqrt(n[v * 3] * n[v * 3] + n[v * 3 + 1] * n[v * 3 + 1] + n[v * 3 + 2] * n[v * 3 + 2])
            if (l > 0f) { n[v * 3] /= l; n[v * 3 + 1] /= l; n[v * 3 + 2] /= l }
        }
        return n
    }

    /** Taubin smoothing (lambda/mu), which smooths without shrinking the model. */
    fun smooth(mesh: Mesh, iterations: Int): Mesh {
        if (iterations <= 0) return mesh
        val nv = mesh.vertexCount
        // Neighbour lists in CSR form, built from the triangle edges.
        val deg = IntArray(nv)
        val idx = mesh.indices
        for (t in 0 until idx.size / 3) for (e in 0..2) deg[idx[t * 3 + e]] += 2
        val start = IntArray(nv + 1)
        for (v in 0 until nv) start[v + 1] = start[v] + deg[v]
        val nbr = IntArray(start[nv])
        val fill = start.copyOf(nv)
        for (t in 0 until idx.size / 3) for (e in 0..2) {
            val v = idx[t * 3 + e]
            nbr[fill[v]++] = idx[t * 3 + (e + 1) % 3]
            nbr[fill[v]++] = idx[t * 3 + (e + 2) % 3]
        }
        var p = mesh.positions.copyOf()
        var tmp = FloatArray(p.size)
        repeat(iterations * 2) { it ->
            val f = if (it % 2 == 0) 0.5f else -0.53f
            for (v in 0 until nv) {
                val s = start[v]; val e = start[v + 1]
                if (e == s) { tmp[v * 3] = p[v * 3]; tmp[v * 3 + 1] = p[v * 3 + 1]; tmp[v * 3 + 2] = p[v * 3 + 2]; continue }
                var sx = 0f; var sy = 0f; var sz = 0f
                for (k in s until e) { val o = nbr[k] * 3; sx += p[o]; sy += p[o + 1]; sz += p[o + 2] }
                val inv = 1f / (e - s)
                for (c in 0..2) {
                    val cur = p[v * 3 + c]
                    val avg = (if (c == 0) sx else if (c == 1) sy else sz) * inv
                    tmp[v * 3 + c] = cur + f * (avg - cur)
                }
            }
            val sw = p; p = tmp; tmp = sw
        }
        return Mesh(p, normals(p, idx), mesh.colors, idx)
    }

    /**
     * Vertex-clustering simplification: snaps vertices to a grid and merges each cell.
     * [reduction] 0..0.95 is the fraction of vertices to remove (approximately).
     */
    fun simplify(mesh: Mesh, reduction: Float): Mesh {
        if (reduction <= 0.01f || mesh.vertexCount < 100) return mesh
        val p = mesh.positions
        val min = FloatArray(3) { Float.MAX_VALUE }
        val maxv = FloatArray(3) { -Float.MAX_VALUE }
        for (v in 0 until mesh.vertexCount) for (c in 0..2) {
            min[c] = minOf(min[c], p[v * 3 + c]); maxv[c] = maxOf(maxv[c], p[v * 3 + c])
        }
        val extent = max(max(maxv[0] - min[0], maxv[1] - min[1]), maxv[2] - min[2])
        // A surface with N vertices on a grid of g^3 cells touches roughly 6 g^2 of them.
        val target = mesh.vertexCount * (1f - reduction)
        val g = max(4, sqrt(target / 6f).toInt() * 2)
        val cell = extent / g * 1.0001f

        val cellOf = HashMap<Long, Int>()
        val remap = IntArray(mesh.vertexCount)
        val acc = FloatList(); val colAcc = FloatList(); val counts = IntList()
        for (v in 0 until mesh.vertexCount) {
            val ix = ((p[v * 3] - min[0]) / cell).toLong()
            val iy = ((p[v * 3 + 1] - min[1]) / cell).toLong()
            val iz = ((p[v * 3 + 2] - min[2]) / cell).toLong()
            val key = (ix shl 42) or (iy shl 21) or iz
            val id = cellOf.getOrPut(key) {
                counts.add(0)
                repeat(3) { acc.add(0f); colAcc.add(0f) }
                counts.size - 1
            }
            remap[v] = id
        }
        val sum = acc.toArray(); val col = colAcc.toArray(); val cnt = IntArray(counts.size)
        for (v in 0 until mesh.vertexCount) {
            val id = remap[v]
            cnt[id]++
            for (c in 0..2) { sum[id * 3 + c] += p[v * 3 + c]; col[id * 3 + c] += mesh.colors[v * 3 + c] }
        }
        for (id in cnt.indices) for (c in 0..2) { sum[id * 3 + c] /= cnt[id]; col[id * 3 + c] /= cnt[id] }

        val out = IntList()
        val seen = HashSet<Long>()
        for (t in 0 until mesh.triangleCount) {
            val a = remap[mesh.indices[t * 3]]; val b = remap[mesh.indices[t * 3 + 1]]; val c = remap[mesh.indices[t * 3 + 2]]
            if (a == b || b == c || a == c) continue
            // Drop duplicate triangles that collapse onto the same cells.
            val lo = minOf(a, b, c).toLong(); val hi = maxOf(a, b, c).toLong(); val mid = (a.toLong() + b + c) - lo - hi
            if (!seen.add((lo shl 42) or (mid shl 21) or hi)) continue
            out.add(a); out.add(b); out.add(c)
        }
        val indices = out.toArray()
        return Mesh(sum, normals(sum, indices), col, indices)
    }

    fun write(mesh: Mesh, format: ExportFormat, out: OutputStream) = when (format) {
        ExportFormat.GLB -> mesh.writeGlb(out)
        ExportFormat.OBJ -> mesh.writeObj(out)
        ExportFormat.STL -> writeStl(mesh, out)
        ExportFormat.PLY -> writePly(mesh, out)
    }

    private fun writeStl(m: Mesh, out: OutputStream) {
        val header = ByteArray(80).also { "TripoSR mobile".toByteArray().copyInto(it) }
        out.write(header)
        val buf = ByteBuffer.allocate(4 + m.triangleCount * 50).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(m.triangleCount)
        val p = m.positions
        for (t in 0 until m.triangleCount) {
            val a = m.indices[t * 3] * 3; val b = m.indices[t * 3 + 1] * 3; val c = m.indices[t * 3 + 2] * 3
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val vx = p[c] - p[a]; val vy = p[c + 1] - p[a + 1]; val vz = p[c + 2] - p[a + 2]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val l = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0f } ?: 1f
            nx /= l; ny /= l; nz /= l
            buf.putFloat(nx).putFloat(ny).putFloat(nz)
            for (o in intArrayOf(a, b, c)) buf.putFloat(p[o]).putFloat(p[o + 1]).putFloat(p[o + 2])
            buf.putShort(0)
        }
        out.write(buf.array())
        out.flush()
    }

    private fun writePly(m: Mesh, out: OutputStream) {
        val header = """
            ply
            format binary_little_endian 1.0
            comment TripoSR mobile
            element vertex ${m.vertexCount}
            property float x
            property float y
            property float z
            property uchar red
            property uchar green
            property uchar blue
            element face ${m.triangleCount}
            property list uchar int vertex_indices
            end_header
        """.trimIndent() + "\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        val buf = ByteBuffer.allocate(m.vertexCount * 15 + m.triangleCount * 13).order(ByteOrder.LITTLE_ENDIAN)
        for (v in 0 until m.vertexCount) {
            buf.putFloat(m.positions[v * 3]).putFloat(m.positions[v * 3 + 1]).putFloat(m.positions[v * 3 + 2])
            for (c in 0..2) buf.put((m.colors[v * 3 + c].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
        }
        for (t in 0 until m.triangleCount) {
            buf.put(3).putInt(m.indices[t * 3]).putInt(m.indices[t * 3 + 1]).putInt(m.indices[t * 3 + 2])
        }
        out.write(buf.array())
        out.flush()
    }

    /** Compact binary format for saved projects. */
    fun save(mesh: Mesh, file: File) {
        val nv = mesh.positions.size; val ni = mesh.indices.size
        val buf = ByteBuffer.allocate(12 + (nv * 3 + ni) * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x4D525354).putInt(nv).putInt(ni)
        buf.asFloatBuffer().put(mesh.positions).put(mesh.normals).put(mesh.colors)
        buf.position(12 + nv * 3 * 4)
        buf.asIntBuffer().put(mesh.indices)
        buf.rewind()
        FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING).use { it.write(buf) }
    }

    fun load(file: File): Mesh {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.int == 0x4D525354) { "bad project file" }
        val nv = buf.int; val ni = buf.int
        val fb = buf.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val pos = FloatArray(nv); val nor = FloatArray(nv); val col = FloatArray(nv)
        fb.get(pos); fb.get(nor); fb.get(col)
        val ib = ByteBuffer.wrap(bytes, 12 + nv * 12, ni * 4).slice().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val idx = IntArray(ni).also { ib.get(it) }
        return Mesh(pos, nor, col, idx)
    }
}
