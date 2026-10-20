package com.triposr.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data object Create : Screen
    data object Processing : Screen
    data object Result : Screen
    data object Projects : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
}

data class JobState(
    val stage: Stage = Stage.LOAD,
    val stageProgress: Float = 0f,
    val status: String = "",
    val error: String? = null,
    val done: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val backend: Backend = Backend.GPU,
) {
    /** 0..1 over the whole pipeline, weighted by how long each stage usually takes. */
    val overall: Float
        get() {
            if (done) return 1f
            var sum = 0f
            for (s in Stage.entries) {
                if (s.ordinal < stage.ordinal) sum += s.weight
                else if (s == stage) sum += s.weight * stageProgress
            }
            return sum.coerceIn(0f, 1f)
        }
}

data class Project(
    val id: String,
    val name: String,
    val created: Long,
    val backend: String,
    val quality: String,
    val seconds: Double,
    val vertices: Int,
    val triangles: Int,
) {
    fun dir(ctx: Context) = File(File(ctx.filesDir, "projects"), id)
    fun thumb(ctx: Context): Bitmap? = File(dir(ctx), "thumb.png").takeIf { it.exists() }
        ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    val date: String get() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(created))
}

/**
 * App-wide state. It lives in the Application (not the Activity) so a running reconstruction
 * keeps going when the user leaves the app; ProcessingService keeps the process alive meanwhile.
 */
object AppState {
    lateinit var app: Context; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: TripoEngine? = null
    private var runningJob: Job? = null
    @Volatile private var cancelRequested = false

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = backStack.last()

    var input by mutableStateOf<Bitmap?>(null)
    var framed by mutableStateOf<Bitmap?>(null)
    var job by mutableStateOf<JobState?>(null)
    var project by mutableStateOf<Project?>(null)
    var mesh by mutableStateOf<Mesh?>(null)
    var originalMesh: Mesh? = null
    val projects = mutableStateListOf<Project>()
    var update by mutableStateOf<Updater.Update?>(null)
    /** Shown once on the home screen, e.g. "NPU crashed, switched to GPU". */
    var notice by mutableStateOf<String?>(null)

    private fun prefs() = app.getSharedPreferences("state", Context.MODE_PRIVATE)

    fun init(context: Context) {
        app = context.applicationContext
        // The previous run died while a backend was in use. Only count it against the backend when
        // Android reports an abnormal exit (crash / native crash / ANR / low memory), not a force-stop.
        prefs().getString("active_backend", null)?.let { name ->
            prefs().edit().remove("active_backend").commit()
            if (!CrashLog.previousSessionCrashed) return@let
            val crashed = Backend.valueOf(name)
            prefs().edit().putBoolean("crashed_$name", true).commit()
            CrashLog.log("Previous run died while using $name")
            if (crashed != Backend.CPU) {
                if (Settings.backendChoice(app) == crashed) Settings.setBackendChoice(app, null)
                notice = "בהפעלה הקודמת האפליקציה נסגרה בזמן עבודה על ${crashed.label}. " +
                    "מעכשיו המצב האוטומטי ידלג עליו. אפשר לאפס בהגדרות, ודוח הקריסה זמין להעתקה."
            }
        }
        scope.launch { loadProjects() }
    }

    fun crashedBefore(b: Backend) = prefs().getBoolean("crashed_${b.name}", false)
    fun clearCrashed(b: Backend) = prefs().edit().remove("crashed_${b.name}").apply()

    fun navigate(s: Screen) {
        if (screen != s) backStack.add(s)
    }

    fun navigateRoot(s: Screen) {
        backStack.clear()
        backStack.add(Screen.Home)
        if (s != Screen.Home) backStack.add(s)
    }

    /** Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun setImage(bmp: Bitmap) {
        input = bmp
        framed = null
    }

    fun start(choice: Backend?, quality: Quality, removeBackground: Boolean, smooth: Boolean) {
        val image = input ?: return
        if (runningJob?.isActive == true) return
        cancelRequested = false
        job = JobState(backend = choice ?: Capabilities.best(app), status = "מתחיל...")
        navigate(Screen.Processing)
        ProcessingService.start(app)
        CrashLog.log("Job start: backend=${choice ?: "AUTO"} quality=$quality removeBg=$removeBackground smooth=$smooth " +
            "image=${image.width}x${image.height}")

        runningJob = scope.launch {
            try {
                fun stage(s: Stage, p: Float, status: String = s.label) {
                    job = job?.copy(stage = s, stageProgress = p, status = status)
                }
                stage(Stage.LOAD, 0f)
                if (!ModelStore.isReady(app)) throw IllegalStateException("צריך להוריד את המודל קודם (מסך הבית)")
                if (choice == null && !Capabilities.probed(app)) {
                    Capabilities.probe(app) { stage(Stage.LOAD, 0.2f, it) }
                }
                val chain = Capabilities.chain(app, choice)
                CrashLog.log("Backend chain: $chain (${Capabilities.describe(app)})")
                val eng = engine ?: TripoEngine(app).also { engine = it }
                eng.load(chain, onStatus = { stage(Stage.LOAD, 0.5f, it) }, onAttempt = { b ->
                    // Written synchronously: if the process dies inside native code we find it on next launch.
                    prefs().edit().putString("active_backend", b.name).commit()
                    job = job?.copy(backend = b)
                })

                stage(Stage.SEGMENT, 0f)
                val cut = if (removeBackground) Preprocess.removeBackground(image, eng.segmenter())
                else image.copy(Bitmap.Config.ARGB_8888, false)
                val frame = Preprocess.frameForeground(cut)
                framed = frame

                val t0 = System.nanoTime()
                val out = eng.reconstruct(Preprocess.toChw(frame), quality.resolution, smooth,
                    progress = { s, p -> stage(s, p) },
                    cancelled = { cancelRequested })
                val seconds = (System.nanoTime() - t0) / 1e9

                val p = saveProject(out.mesh, frame, eng.encoderBackend, quality,
                    (System.currentTimeMillis() - job!!.startedAt) / 1000.0)
                CrashLog.log("Job done in %.1fs (pipeline %.1fs), ${out.mesh.triangleCount} triangles".format(p.seconds, seconds))
                withContext(Dispatchers.Main) {
                    originalMesh = out.mesh
                    mesh = out.mesh
                    project = p
                    job = job?.copy(done = true, stageProgress = 1f, status = "המודל מוכן!")
                    if (screen == Screen.Processing) showResult()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                CrashLog.log("Job cancelled")
                job = null
                withContext(Dispatchers.Main) { if (screen == Screen.Processing) back() }
            } catch (e: Throwable) {
                // Includes OutOfMemoryError: report it instead of crashing the app.
                CrashLog.error("reconstruction", e)
                job = job?.copy(error = (e.message ?: e.javaClass.simpleName).take(300))
                if (e is OutOfMemoryError) { engine?.close(); engine = null }
            } finally {
                prefs().edit().remove("active_backend").commit()
                ProcessingService.finished(app)
            }
        }
    }

    /** Opens the finished job's model (from the processing screen, the home card or the notification). */
    fun showResult() {
        if (mesh == null) return
        if (screen == Screen.Processing) backStack.removeAt(backStack.lastIndex)
        navigate(Screen.Result)
        if (job?.done == true) job = null
    }

    /** Closes the model sessions, e.g. before the model files are deleted. */
    fun resetEngine() {
        if (isRunning) return
        engine?.close()
        engine = null
    }

    fun cancel() {
        cancelRequested = true
    }

    val isRunning get() = runningJob?.isActive == true

    private fun projectsDir() = File(app.filesDir, "projects").apply { mkdirs() }

    private fun saveProject(mesh: Mesh, thumb: Bitmap, backend: Backend, quality: Quality, seconds: Double): Project {
        val id = System.currentTimeMillis().toString()
        val p = Project(id, "מודל ${projects.size + 1}", System.currentTimeMillis(), backend.short, quality.label,
            seconds, mesh.vertexCount, mesh.triangleCount)
        val dir = p.dir(app).apply { mkdirs() }
        MeshTools.save(mesh, File(dir, "mesh.bin"))
        File(dir, "thumb.png").outputStream().use {
            Bitmap.createScaledBitmap(thumb, 256, 256, true).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        writeMeta(p)
        scope.launch(Dispatchers.Main) { projects.add(0, p) }
        CreateWidget.refresh(app)
        return p
    }

    private fun writeMeta(p: Project) {
        File(p.dir(app), "meta.json").writeText(JSONObject().apply {
            put("name", p.name); put("created", p.created); put("backend", p.backend)
            put("quality", p.quality); put("seconds", p.seconds)
            put("vertices", p.vertices); put("triangles", p.triangles)
        }.toString())
    }

    private suspend fun loadProjects() {
        val list = projectsDir().listFiles()?.mapNotNull { dir ->
            runCatching {
                val j = JSONObject(File(dir, "meta.json").readText())
                Project(dir.name, j.getString("name"), j.getLong("created"), j.optString("backend"),
                    j.optString("quality"), j.optDouble("seconds"), j.optInt("vertices"), j.optInt("triangles"))
            }.getOrNull()
        }?.sortedByDescending { it.created } ?: emptyList()
        withContext(Dispatchers.Main) { projects.clear(); projects.addAll(list) }
    }

    fun open(p: Project) {
        scope.launch {
            try {
                val m = MeshTools.load(File(p.dir(app), "mesh.bin"))
                withContext(Dispatchers.Main) {
                    originalMesh = m; mesh = m; project = p
                    navigate(Screen.Result)
                }
            } catch (e: Throwable) {
                CrashLog.error("open project ${p.id}", e)
                notice = "לא הצלחתי לפתוח את הפרויקט: ${e.message}"
            }
        }
    }

    fun delete(p: Project) {
        p.dir(app).deleteRecursively()
        projects.remove(p)
        if (project?.id == p.id) { project = null; mesh = null }
        CreateWidget.refresh(app)
    }

    fun rename(p: Project, name: String) {
        val n = p.copy(name = name)
        writeMeta(n)
        val i = projects.indexOfFirst { it.id == p.id }
        if (i >= 0) projects[i] = n
        if (project?.id == p.id) project = n
    }

    /** Saves an edited mesh over the project's stored mesh. */
    fun saveEdits(m: Mesh) {
        val p = project ?: return
        MeshTools.save(m, File(p.dir(app), "mesh.bin"))
        val n = p.copy(vertices = m.vertexCount, triangles = m.triangleCount)
        writeMeta(n)
        val i = projects.indexOfFirst { it.id == p.id }
        if (i >= 0) projects[i] = n
        project = n
        originalMesh = m
    }
}
