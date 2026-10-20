package com.triposr.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThreeDRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.triposr.mobile.AppState
import com.triposr.mobile.Backend
import com.triposr.mobile.BuildConfig
import com.triposr.mobile.CrashLog
import com.triposr.mobile.ExportFormat
import com.triposr.mobile.MeshTools
import com.triposr.mobile.MeshView
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Project
import com.triposr.mobile.Quality
import com.triposr.mobile.Screen
import com.triposr.mobile.Settings
import com.triposr.mobile.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mesh = AppState.mesh
    val project = AppState.project
    if (mesh == null || project == null) {
        LaunchedEffect(Unit) { AppState.back() }
        return
    }
    var colors by remember { mutableStateOf(true) }
    var wire by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(true) }
    var rotate by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<MeshView?>(null) }
    var format by remember { mutableStateOf(ExportFormat.GLB) }
    var busy by remember { mutableStateOf(false) }
    var smoothIt by remember { mutableFloatStateOf(0f) }
    var reduce by remember { mutableFloatStateOf(0f) }
    var edited by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    fun export(fmt: ExportFormat = format, action: (android.net.Uri) -> Unit) {
        busy = true
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { Actions.exportToCache(ctx, mesh, fmt, project.name) }
                action(uri)
            } catch (e: Throwable) {
                CrashLog.error("export", e); Actions.toast(ctx, "הייצוא נכשל: ${e.message}")
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("המודל מוכן!", onBack = { AppState.back() }) {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "שנה שם", tint = C.Text) }
            IconButton(onClick = { export { Actions.share(ctx, it, format) } }) { Icon(Icons.Rounded.Share, "שתף", tint = C.Text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(24.dp)).background(C.Bg)
                .border(1.dp, C.Stroke, RoundedCornerShape(24.dp))) {
                AndroidView(
                    factory = { MeshView(it).also { v -> view = v } },
                    modifier = Modifier.fillMaxSize(),
                    update = { v ->
                        v.setMesh(mesh)
                        v.setOptions(colors, wire, light, grid, rotate)
                    },
                    onRelease = { it.onPause() },
                )
                Column(Modifier.align(Alignment.TopStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewToggle(Icons.Rounded.Grid4x4, "רשת", wire) { wire = !wire }
                    ViewToggle(Icons.Rounded.Palette, "צבע", colors) { colors = !colors }
                    ViewToggle(Icons.Rounded.Lightbulb, "תאורה", light) { light = !light }
                    ViewToggle(Icons.Rounded.GridOn, "רצפה", grid) { grid = !grid }
                    ViewToggle(Icons.Rounded.ThreeDRotation, "סיבוב", rotate) { rotate = !rotate }
                    ViewToggle(Icons.Rounded.CenterFocusStrong, "איפוס", false) { view?.resetView() }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(project.name, C.Text)
                Chip("${"%,d".format(mesh.triangleCount)} פוליגונים")
                Chip("${"%,d".format(mesh.vertexCount)} קודקודים")
                Chip("%.1f שניות".format(project.seconds), C.Green)
                Chip(project.backend, C.Orange)
            }

            Section(title = "עריכה") {
                Text("החלקה: ${smoothIt.toInt()}", color = C.Sub, fontSize = 13.sp)
                Slider(smoothIt, { smoothIt = it }, valueRange = 0f..10f, steps = 9, colors = sliderColors())
                Text("פישוט (פחות פוליגונים): ${(reduce * 100).toInt()}%", color = C.Sub, fontSize = 13.sp)
                Slider(reduce, { reduce = it }, valueRange = 0f..0.9f, colors = sliderColors())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("החל", Modifier.weight(1f), Icons.Rounded.Tune, enabled = !busy) {
                        busy = true
                        scope.launch {
                            val base = AppState.originalMesh ?: mesh
                            val m = withContext(Dispatchers.Default) { MeshTools.smooth(MeshTools.simplify(base, reduce), smoothIt.toInt()) }
                            AppState.mesh = m; edited = true; busy = false
                        }
                    }
                    SoftButton("בטל", Modifier.weight(0.6f), enabled = edited) {
                        AppState.mesh = AppState.originalMesh; edited = false; smoothIt = 0f; reduce = 0f
                    }
                }
                if (edited) SoftButton("שמור שינויים בפרויקט", Modifier.fillMaxWidth(), color = C.Green) {
                    scope.launch(Dispatchers.IO) { AppState.saveEdits(mesh) }
                    edited = false; smoothIt = 0f; reduce = 0f
                    Actions.toast(ctx, "נשמר")
                }
            }

            Section(title = "ייצוא") {
                Segmented(ExportFormat.entries.toList(), format, { it.label }, { format = it })
                Text(when (format) {
                    ExportFormat.GLB -> "מומלץ: עם צבעים, נפתח בכל צופה 3D ובאנדרואיד"
                    ExportFormat.OBJ -> "Blender, Maya ותוכנות עריכה (צבע לכל קודקוד)"
                    ExportFormat.STL -> "להדפסה תלת־ממדית (בלי צבע)"
                    ExportFormat.PLY -> "סריקות ו-MeshLab, עם צבעים"
                }, color = C.Sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("שתף מודל", Modifier.weight(1f), Icons.Rounded.Share, enabled = !busy) {
                        export { Actions.share(ctx, it, format) }
                    }
                    SoftButton("שמור", Modifier.weight(0.8f), Icons.Rounded.Download, enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                val path = withContext(Dispatchers.IO) { Actions.saveToDownloads(ctx, mesh, format, project.name) }
                                Actions.toast(ctx, "נשמר: $path")
                            } catch (e: Throwable) {
                                CrashLog.error("save", e); Actions.toast(ctx, "השמירה נכשלה: ${e.message}")
                            } finally { busy = false }
                        }
                    }
                }
                SoftButton("פתח בצופה חיצוני / AR", Modifier.fillMaxWidth(), Icons.AutoMirrored.Rounded.OpenInNew, enabled = !busy) {
                    export(ExportFormat.GLB) { Actions.openExternal(ctx, it) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (renaming) RenameDialog(project, onDone = { renaming = false })
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = C.Violet, activeTrackColor = C.Purple, inactiveTrackColor = C.Stroke)

@Composable
private fun ViewToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) C.Purple.copy(alpha = 0.85f) else C.Card.copy(alpha = 0.85f))
            .border(1.dp, if (on) C.Violet else C.Stroke, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(label, color = C.Sub, fontSize = 10.sp)
    }
}

@Composable
private fun RenameDialog(p: Project, onDone: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = C.Card,
        title = { Text("שם המודל", color = C.Text) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton({ if (name.isNotBlank()) AppState.rename(p, name.trim()); onDone() }) { Text("שמור", color = C.Violet) } },
        dismissButton = { TextButton(onDone) { Text("ביטול", color = C.Sub) } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Violet, unfocusedBorderColor = C.Stroke, focusedTextColor = C.Text,
    unfocusedTextColor = C.Text, cursorColor = C.Violet, focusedContainerColor = C.Bg, unfocusedContainerColor = C.Bg)

@Composable
fun ProjectsScreen() {
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Project?>(null) }
    val list = AppState.projects.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        TopBar("הפרויקטים שלי")
        OutlinedTextField(query, { query = it }, singleLine = true, colors = fieldColors(),
            placeholder = { Text("חיפוש פרויקטים…", color = C.Sub) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = C.Sub) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Logo(72)
                Spacer(Modifier.height(16.dp))
                Text(if (AppState.projects.isEmpty()) "עוד אין מודלים" else "לא נמצאו תוצאות", color = C.Text,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("כל מודל שתיצור יישמר כאן", color = C.Sub, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                GradientButton("צור מודל חדש") { AppState.navigate(Screen.Create) }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                items(list, key = { it.id }) { p ->
                    Box {
                        ProjectThumb(p, Modifier.fillMaxWidth())
                        Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.55f)).clickable { toDelete = p },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Delete, "מחק", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = C.Card,
            title = { Text("למחוק את \"${p.name}\"?", color = C.Text) },
            confirmButton = { TextButton({ AppState.delete(p); toDelete = null }) { Text("מחק", color = C.Red) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("ביטול", color = C.Sub) } },
        )
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = rememberModelStatus()
    var backend by remember { mutableStateOf(Settings.backend(ctx)) }
    var quality by remember { mutableStateOf(Settings.quality(ctx)) }
    var removeBg by remember { mutableStateOf(Settings.removeBackground(ctx)) }
    var smooth by remember { mutableStateOf(Settings.smooth(ctx)) }
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var crashTick by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar("הגדרות")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(title = "מודל ה-AI") {
                ModelCard(status, compact = false)
                if (status is ModelStore.Status.Ready) SoftButton("מחק מודל (משחרר ${ModelStore.sizeOnDisk(ctx) shr 20} MB)",
                    Modifier.fillMaxWidth(), Icons.Rounded.Delete, color = C.Red) { confirmDelete = true }
            }
            Section(title = "ברירות מחדל") {
                Text("מעבד", color = C.Sub, fontSize = 13.sp)
                Segmented(Backend.entries.toList(), backend, { it.short }, { backend = it; Settings.setBackend(ctx, it) })
                Text("רמת פירוט", color = C.Sub, fontSize = 13.sp)
                Segmented(Quality.entries.toList(), quality, { it.label }, { quality = it; Settings.setQuality(ctx, it) })
                ToggleRow("הסרת רקע אוטומטית", null, removeBg) { removeBg = it; Settings.setRemoveBackground(ctx, it) }
                ToggleRow("החלקת משטח", null, smooth) { smooth = it; Settings.setSmooth(ctx, it) }
            }
            val crashed = remember(crashTick) { Backend.entries.filter { AppState.crashedBefore(it) } }
            if (crashed.isNotEmpty()) Section(title = "מעבדים שנסגרו בעבר") {
                Text("האפליקציה זוכרת מעבד שבזמן העבודה עליו היא נסגרה, ומחליפה אותו אוטומטית. אפשר לאפס ולנסות שוב.",
                    color = C.Sub, fontSize = 13.sp)
                crashed.forEach { b ->
                    SoftButton("אפס ונסה שוב את ${b.label}", Modifier.fillMaxWidth()) { AppState.clearCrashed(b); crashTick++ }
                }
            }
            Section(title = "עדכונים") {
                Text("גרסה ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = C.Text)
                SoftButton(if (checking) "בודק…" else "בדוק עדכונים", Modifier.fillMaxWidth(), enabled = !checking) {
                    checking = true
                    scope.launch {
                        updateMsg = runCatching { Updater.check() }.fold(
                            { u -> AppState.update = u; if (u == null) "אתה בגרסה האחרונה" else null },
                            { "הבדיקה נכשלה: ${it.message}" })
                        checking = false
                    }
                }
                updateMsg?.let { Text(it, color = C.Sub, fontSize = 13.sp) }
                UpdateCard()
            }
            Section(title = "דוחות ותקלות") {
                Text("דוח מצב מלא של המכשיר, האפליקציה והקריסה האחרונה — להעתקה ולהדבקה בצ'אט.", color = C.Sub, fontSize = 13.sp)
                GradientButton("פתח דוח מצב וקריסות", Modifier.fillMaxWidth()) { AppState.navigate(Screen.Diagnostics) }
            }
            Section(title = "אודות") {
                Text("TripoSR (VAST AI & Stability AI, רישיון MIT) רץ מקומית עם ONNX Runtime ו-Qualcomm QNN. " +
                    "הסרת רקע: U2-Net (Apache-2.0).", color = C.Sub, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = C.Card,
        title = { Text("למחוק את המודל?", color = C.Text) },
        text = { Text("תצטרך להוריד אותו שוב (1.7GB) כדי ליצור מודלים חדשים. הפרויקטים השמורים נשארים.", color = C.Sub) },
        confirmButton = { TextButton({ ModelStore.delete(ctx); confirmDelete = false }) { Text("מחק", color = C.Red) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("ביטול", color = C.Sub) } },
    )
}

@Composable
fun DiagnosticsScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val report = remember(tick) { CrashLog.diagnostics() }
    val crash = remember(tick) { CrashLog.pendingReport }
    val lastError = remember(tick) { CrashLog.lastError }
    Column(Modifier.fillMaxSize()) {
        TopBar("דוח מצב וקריסות")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (crash != null) ReportSection("קריסה מהפעלה קודמת", crash, C.Red)
            if (lastError != null) ReportSection("שגיאה אחרונה", lastError, C.Orange)
            if (crash == null && lastError == null) Section {
                Text("לא נרשמו קריסות או שגיאות 🎉", color = C.Green, fontWeight = FontWeight.SemiBold)
            }
            ReportSection("דוח מצב מלא", report, C.Violet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("רענן", Modifier.weight(1f)) { tick++ }
                SoftButton("נקה הכל", Modifier.weight(1f), color = C.Red) { CrashLog.clear(); tick++ }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportSection(title: String, text: String, color: Color) {
    val ctx = LocalContext.current
    Section {
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("העתק", Modifier.weight(1f), Icons.Rounded.ContentCopy) { Actions.copy(ctx, text) }
            SoftButton("שתף", Modifier.weight(1f), Icons.Rounded.Share) { Actions.shareText(ctx, text) }
        }
        // Logs are English/code: show them left-to-right in a monospace font.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            SelectionContainer {
                Text(text.take(12000), color = C.Sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp))
                        .background(C.Bg).verticalScroll(rememberScrollState()).padding(10.dp))
            }
        }
    }
}
