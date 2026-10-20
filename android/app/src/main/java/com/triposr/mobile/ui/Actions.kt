package com.triposr.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.triposr.mobile.CrashLog
import com.triposr.mobile.ExportFormat
import com.triposr.mobile.Mesh
import com.triposr.mobile.MeshTools
import java.io.File

object Actions {
    fun copy(ctx: Context, text: String, what: String = "הדוח") {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("TripoSR report", text))
        Toast.makeText(ctx, "$what הועתק — אפשר להדביק בצ'אט", Toast.LENGTH_SHORT).show()
    }

    fun shareText(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(send, "שתף דוח").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun loadBitmap(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1536) decoder.setTargetSampleSize(longest / 1024)
        }.copy(Bitmap.Config.ARGB_8888, false)
    }.onFailure { CrashLog.error("load image $uri", it) }.getOrNull()

    fun cameraUri(ctx: Context): Uri {
        val f = File(ctx.cacheDir, "camera/photo.jpg").apply { parentFile?.mkdirs() }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    private fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifEmpty { "model" }

    /** Writes the mesh to the app cache and returns a shareable content URI. */
    fun exportToCache(ctx: Context, mesh: Mesh, format: ExportFormat, name: String): Uri {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "${safeName(name)}.${format.ext}")
        f.outputStream().buffered(1 shl 20).use { MeshTools.write(mesh, format, it) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** Saves to Download/TripoSR. Returns the display path. */
    fun saveToDownloads(ctx: Context, mesh: Mesh, format: ExportFormat, name: String): String {
        val file = "${safeName(name)}_${System.currentTimeMillis() / 1000}.${format.ext}"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file)
            put(MediaStore.Downloads.MIME_TYPE, format.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TripoSR")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("cannot create $file")
        ctx.contentResolver.openOutputStream(uri)!!.buffered(1 shl 20).use { MeshTools.write(mesh, format, it) }
        return "Download/TripoSR/$file"
    }

    fun share(ctx: Context, uri: Uri, format: ExportFormat) {
        val send = Intent(Intent.ACTION_SEND).setType(format.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "שתף מודל 3D").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openExternal(ctx: Context, uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, ExportFormat.GLB.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            ctx.startActivity(Intent.createChooser(view, "פתח באפליקציה").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(ctx, "לא נמצאה אפליקציה שפותחת GLB", Toast.LENGTH_LONG).show() }
    }

    fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
}
