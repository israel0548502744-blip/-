package com.triposr.mobile

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var engine: TripoEngine
    private lateinit var preview: ImageView
    private lateinit var meshView: MeshView
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var backendSpinner: Spinner
    private lateinit var resSpinner: Spinner
    private lateinit var runButton: Button
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button

    private var input: Bitmap? = null
    private var mesh: Mesh? = null
    private var lastGlb: Uri? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = TripoEngine(this) { msg -> runOnUiThread { logView.append("$msg\n") } }
        buildUi()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { loadImage(it) }
        }
    }

    private fun buildUi() {
        val dp = resources.displayMetrics.density
        fun button(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val pick = button("בחר תמונה") { pickImage() }
        runButton = button("צור מודל 3D") { run() }
        saveButton = button("שמור GLB + OBJ") { save() }
        shareButton = button("שתף") { share() }

        backendSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, Backend.values().map { it.label })
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        resSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf("רזולוציה 128 (מהיר)", "רזולוציה 192", "רזולוציה 256 (איכותי)"))
            setSelection(1)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams((140 * dp).toInt(), (140 * dp).toInt())
        }
        status = TextView(this).apply {
            text = "בחר תמונה של אובייקט כדי להתחיל"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        }
        meshView = MeshView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f) }
        logView = TextView(this).apply { textSize = 11f; setTextIsSelectable(true) }
        val logScroll = ScrollView(this).apply {
            addView(logView)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (90 * dp).toInt())
        }

        fun row(vararg views: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (28 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            addView(row(pick, runButton))
            addView(row(backendSpinner, resSpinner))
            addView(row(preview, status))
            addView(meshView)
            addView(row(saveButton, shareButton))
            addView(logScroll)
        }
        setContentView(root)
        updateButtons()
    }

    private fun updateButtons() {
        runButton.isEnabled = !busy && input != null
        saveButton.isEnabled = !busy && mesh != null
        shareButton.isEnabled = !busy && mesh != null
    }

    @Suppress("DEPRECATION")
    private fun pickImage() {
        startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"), REQ_PICK)
    }

    @Deprecated("Activity result API needs androidx; plain Activity is enough here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) data?.data?.let { loadImage(it) }
    }

    private fun loadImage(uri: Uri) {
        val src = ImageDecoder.createSource(contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1536) decoder.setTargetSampleSize(longest / 1024)
        }
        input = bmp.copy(Bitmap.Config.ARGB_8888, false)
        preview.setImageBitmap(input)
        status.text = "מוכן. לחץ \"צור מודל 3D\""
        updateButtons()
    }

    private fun run() {
        val bmp = input ?: return
        val backend = Backend.values()[backendSpinner.selectedItemPosition]
        val resolution = intArrayOf(128, 192, 256)[resSpinner.selectedItemPosition]
        busy = true
        updateButtons()
        val progress: (String) -> Unit = { msg -> runOnUiThread { status.text = msg } }
        scope.launch {
            try {
                val t0 = System.nanoTime()
                val result = withContext(Dispatchers.Default) {
                    engine.load(backend, progress)
                    progress("מסיר רקע...")
                    val cut = Preprocess.removeBackground(bmp, engine.segmenter!!)
                    val framed = Preprocess.frameForeground(cut)
                    runOnUiThread { preview.setImageBitmap(framed) }
                    engine.reconstruct(Preprocess.toChw(framed), resolution, progress)
                }
                mesh = result
                lastGlb = null
                meshView.setMesh(result)
                status.text = "הושלם תוך %.1f שניות".format((System.nanoTime() - t0) / 1e9)
            } catch (e: Throwable) {
                status.text = "שגיאה: ${e.message}"
                logView.append("${e.stackTraceToString().take(1500)}\n")
            } finally {
                busy = false
                updateButtons()
            }
        }
    }

    private fun save() {
        val m = mesh ?: return
        busy = true
        updateButtons()
        scope.launch {
            try {
                val name = "triposr_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                withContext(Dispatchers.IO) {
                    lastGlb = writeDownload("$name.glb", "model/gltf-binary") { m.writeGlb(it) }
                    writeDownload("$name.obj", "text/plain") { m.writeObj(it) }
                }
                status.text = "נשמר בהורדות/TripoSR: $name.glb, $name.obj"
            } catch (e: Throwable) {
                status.text = "שמירה נכשלה: ${e.message}"
            } finally {
                busy = false
                updateButtons()
            }
        }
    }

    private fun share() {
        scope.launch {
            if (lastGlb == null) {
                val m = mesh ?: return@launch
                lastGlb = withContext(Dispatchers.IO) {
                    writeDownload("triposr_${System.currentTimeMillis()}.glb", "model/gltf-binary") { m.writeGlb(it) }
                }
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType("model/gltf-binary")
                .putExtra(Intent.EXTRA_STREAM, lastGlb)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "שתף מודל 3D"))
        }
    }

    private fun writeDownload(name: String, mime: String, write: (java.io.OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TripoSR")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("cannot create $name")
        contentResolver.openOutputStream(uri)!!.use { write(it.buffered(1 shl 20)) }
        return uri
    }

    override fun onResume() { super.onResume(); meshView.onResume() }
    override fun onPause() { meshView.onPause(); super.onPause() }

    override fun onDestroy() {
        scope.cancel()
        engine.close()
        super.onDestroy()
    }

    companion object { private const val REQ_PICK = 1 }
}
