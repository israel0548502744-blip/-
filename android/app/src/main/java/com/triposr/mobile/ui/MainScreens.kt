package com.triposr.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.Backend
import com.triposr.mobile.Capabilities
import com.triposr.mobile.CrashLog
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Project
import com.triposr.mobile.Quality
import com.triposr.mobile.Screen
import com.triposr.mobile.Settings
import com.triposr.mobile.Stage
import com.triposr.mobile.Support
import com.triposr.mobile.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Polls DownloadManager while visible. */
@Composable
fun rememberModelStatus(pack: com.triposr.mobile.Pack = ModelStore.MAIN): ModelStore.Status {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(runCatching { ModelStore.status(ctx, pack) }.getOrDefault(ModelStore.Status.Missing)) }
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { ModelStore.status(ctx, pack) }.getOrDefault(ModelStore.Status.Missing)
            delay(if (status is ModelStore.Status.Downloading) 600 else 2000)
        }
    }
    return status
}

/** Floating glass navigation bar with a raised "create" orb in the middle. */
@Composable
fun BottomNav(current: Screen) {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().height(70.dp).glass(RoundedCornerShape(35.dp), strong = true).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.Home, "בית", current == Screen.Home) { AppState.navigateRoot(Screen.Home) }
            NavItem(Icons.Rounded.GridView, "גלריה", current == Screen.Projects) { AppState.navigateRoot(Screen.Projects) }
            Spacer(Modifier.width(72.dp))
            NavItem(Icons.Rounded.Settings, "הגדרות", current == Screen.Settings) { AppState.navigateRoot(Screen.Settings) }
            NavItem(Icons.Rounded.BugReport, "דוחות", current == Screen.Diagnostics) { AppState.navigateRoot(Screen.Diagnostics) }
        }
        Column(Modifier.align(Alignment.TopCenter).offset(y = (-14).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(66.dp).shadow(20.dp, CircleShape, ambientColor = C.Magenta, spotColor = C.Magenta)
                    .clip(CircleShape).background(Brush.linearGradient(listOf(C.Purple, C.Magenta, C.Pink)))
                    .border(1.5.dp, Color(0x55FFFFFF), CircleShape)
                    .pressable { AppState.startNew() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "צור", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) C.Violet else C.Faint, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) C.Text else C.Faint, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Box(Modifier.padding(top = 3.dp).size(width = 14.dp, height = 3.dp).clip(CircleShape)
            .background(if (selected) C.Violet else Color.Transparent))
    }
}

@Composable
fun Logo(size: Int = 56) {
    Box(
        Modifier.size(size.dp).shadow(16.dp, RoundedCornerShape((size / 3.2).dp), ambientColor = C.Purple, spotColor = C.Purple)
            .clip(RoundedCornerShape((size / 3.2).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6D5BFF), C.Magenta, Color(0xFF52D6FF))))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape((size / 3.2).dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.ViewInAr, null, tint = Color.White, modifier = Modifier.size((size * 0.56).dp)) }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val status = rememberModelStatus()
    val npuStatus = rememberModelStatus(ModelStore.NPU)
    LaunchedEffect(status, npuStatus) {
        if (status is ModelStore.Status.Ready && npuStatus !is ModelStore.Status.Downloading && !Capabilities.probed(ctx)) startProbe(ctx)
    }
    var i = 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Logo(64)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.enter(i++)) {
            GradientText("TripoSR 3D", 36.sp)
            Spacer(Modifier.height(4.dp))
            Text("מתמונה אחת למודל תלת־ממד, ישירות בטלפון", color = C.Sub, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        StatusPill(status, Modifier.enter(i++))

        CrashCard()
        AppState.notice?.let { NoticeCard(it) { AppState.notice = null } }
        UpdateCard()
        if (status !is ModelStore.Status.Ready) ModelCard(status)
        else NpuPackCard(npuStatus)

        GradientButton("צור מודל 3D", Modifier.fillMaxWidth().enter(i++), sparkles = true,
            enabled = status is ModelStore.Status.Ready) { AppState.startNew() }

        val job = AppState.job
        when {
            job != null && AppState.isRunning -> FeatureCard(Icons.Rounded.ViewInAr, "יוצר מודל…", job.status,
                Modifier.enter(i++), onClick = { AppState.navigate(Screen.Processing) }) {
                PreviewPanel(AppState.framed ?: AppState.input, job.overall, "${(job.overall * 100).toInt()}%")
            }
            job?.done == true && AppState.mesh != null -> FeatureCard(Icons.Rounded.CheckCircle, "המודל מוכן!", "לחץ לצפייה",
                Modifier.enter(i++), onClick = { AppState.showResult() }) {
                PreviewPanel(AppState.framed, 1f, "100%")
            }
            else -> FeatureCard(Icons.Rounded.AddPhotoAlternate, "תמונה לתלת־ממד", "גלריה, מצלמה או קובץ",
                Modifier.enter(i++), onClick = { AppState.startNew() }) {
                val last = AppState.projects.firstOrNull()
                PreviewPanel(last?.let { rememberThumb(it) }, if (last != null) 1f else 0f, last?.name ?: "התחל")
            }
        }
        FeatureCard(Icons.Rounded.GridView, "הגלריה שלי", "${AppState.projects.size} מודלים שמורים",
            Modifier.enter(i++), onClick = { AppState.navigateRoot(Screen.Projects) }) {
            ThumbStack(AppState.projects.take(3))
        }
        FeatureCard(Icons.Rounded.Memory, "מעבד הטלפון", processorSummary(ctx),
            Modifier.enter(i++), onClick = { AppState.navigateRoot(Screen.Settings) }) {
            ProcessorPanel()
        }

        if (AppState.projects.size > 3) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().enter(i++)) {
                Text("יצירות אחרונות", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("הכל", color = C.Violet, fontSize = 14.sp, modifier = Modifier.pressable { AppState.navigateRoot(Screen.Projects) })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                items(AppState.projects.take(10), key = { it.id }) { p -> ProjectThumb(p, Modifier.width(132.dp)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun processorSummary(ctx: android.content.Context): String {
    if (ProbeUi.running) return "בודק את המעבדים…"
    if (!Capabilities.probed(ctx)) return "ייבדק אחרי הורדת המודל"
    return "פועל על ${Capabilities.best(ctx).label}"
}

@Composable
private fun StatusPill(status: ModelStore.Status, modifier: Modifier) {
    val (text, color) = when (status) {
        ModelStore.Status.Ready -> (if (ProbeUi.running) "בודק את המעבדים…" else "המודל מוכן · עובד בלי אינטרנט") to C.Green
        is ModelStore.Status.Downloading -> "מוריד את המודל ${(status.done * 100 / status.total)}%" to C.Violet
        ModelStore.Status.Missing -> "צריך להוריד את המודל פעם אחת" to C.Amber
        is ModelStore.Status.Failed -> "הורדת המודל נכשלה" to C.Red
    }
    Row(modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = C.Text, fontSize = 13.sp)
    }
}

/** Card in the style of the reference: icon tile + title on one side, a preview panel on the other. */
@Composable
private fun FeatureCard(
    icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier,
    onClick: () -> Unit, preview: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().height(124.dp).glass(RoundedCornerShape(26.dp)).pressable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.9f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, 46.dp)
            Column {
                Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(subtitle, color = C.Sub, fontSize = 12.sp, maxLines = 1)
            }
        }
        Box(Modifier.weight(1.1f).fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color(0x33000000))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(18.dp))) { preview() }
    }
}

@Composable
private fun PreviewPanel(image: android.graphics.Bitmap?, progress: Float, label: String) {
    Box(Modifier.fillMaxSize()) {
        if (image != null) Image(image.asImageBitmap(), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.92f))
        else Icon(Icons.Rounded.AddPhotoAlternate, null, tint = C.Faint, modifier = Modifier.size(36.dp).align(Alignment.Center))
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0B0720)))).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowBar(progress, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(label, color = C.Text, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ThumbStack(projects: List<Project>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (projects.isEmpty()) Text("עוד אין מודלים", color = C.Faint, fontSize = 12.sp)
        projects.reversed().forEachIndexed { idx, p ->
            val k = projects.size - 1 - idx
            val bmp = rememberThumb(p)
            Box(Modifier.offset(x = (k * 22 - 22).dp).size(64.dp).shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)).background(C.CardHigh).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ProcessorPanel() {
    val ctx = LocalContext.current
    val results = remember(ProbeUi.tick, ProbeUi.running) { Capabilities.results(ctx) }
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        for (b in Backend.entries) {
            val s = results[b]?.support ?: Support.UNKNOWN
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(when (s) {
                    Support.OK -> C.Green; Support.UNKNOWN -> C.Faint; else -> C.Red
                }))
                Spacer(Modifier.width(8.dp))
                Text(b.short, color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
                Text(s.label, color = C.Sub, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun rememberThumb(p: Project): android.graphics.Bitmap? {
    val ctx = LocalContext.current
    return remember(p.id) { p.thumb(ctx) }
}

@Composable
fun ProjectThumb(p: Project, modifier: Modifier = Modifier) {
    val bmp = rememberThumb(p)
    Column(modifier.glass(RoundedCornerShape(22.dp)).pressable { AppState.open(p) }.padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x33000000))) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp))
        Text(p.date, color = C.Faint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Section {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Warning, null, tint = C.Amber)
            Spacer(Modifier.width(10.dp))
            Text(text, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, "סגור", tint = C.Sub, modifier = Modifier.pressable(onClick = onDismiss))
        }
    }
}

@Composable
fun CrashCard() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pendingReport) }
    val r = report ?: return
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BugReport, null, tint = C.Red)
            Spacer(Modifier.width(10.dp))
            Text("האפליקציה נסגרה בפעם הקודמת בגלל תקלה", color = C.Text, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
        }
        Text("העתק את הדוח והדבק אותו בצ'אט כדי שאוכל לתקן.", color = C.Sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("העתק דוח", Modifier.weight(1f), icon = Icons.Rounded.ContentCopy) {
                Actions.copy(ctx, r, "דוח הקריסה")
            }
            SoftButton("סגור", Modifier.weight(0.6f)) { CrashLog.dismissPending(); report = null }
        }
    }
}

object UpdateFlow {
    var downloadId by mutableStateOf<Long?>(null)
    var progress by mutableFloatStateOf(0f)
}

@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val u = AppState.update ?: return
    val id = UpdateFlow.downloadId
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        while (true) {
            UpdateFlow.progress = Updater.progress(ctx, id)
            if (UpdateFlow.progress >= 1f) { Updater.install(ctx, u); break }
            if (UpdateFlow.progress < 0f) break
            delay(500)
        }
    }
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.SystemUpdate, 40.dp, Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFF14B8A6))))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("גרסה חדשה זמינה", color = C.Text, fontWeight = FontWeight.Bold)
                Text("build ${u.build} · ${u.sizeMb} MB · המודל לא יורד שוב", color = C.Sub, fontSize = 12.sp)
            }
        }
        when {
            id == null -> GradientButton("עדכן עכשיו", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) {
                UpdateFlow.downloadId = Updater.download(ctx, u)
            }
            UpdateFlow.progress < 0f -> SoftButton("ההורדה נכשלה, נסה שוב", Modifier.fillMaxWidth()) { UpdateFlow.downloadId = null }
            UpdateFlow.progress >= 1f -> GradientButton("התקן", Modifier.fillMaxWidth()) { Updater.install(ctx, u) }
            else -> GlowBar(UpdateFlow.progress, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ModelCard(status: ModelStore.Status, compact: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    fun startDownload() {
        error = null
        scope.launch {
            runCatching { ModelStore.startDownload(ctx) }
                .onFailure { CrashLog.error("model download", it); error = "לא הצלחתי להתחיל הורדה: ${it.message}" }
        }
    }
    when (status) {
        ModelStore.Status.Ready -> if (compact) Unit
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("המודל מותקן (${ModelStore.sizeOnDisk(ctx) shr 20} MB)", color = C.Green)
            }
        ModelStore.Status.Missing -> Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.CloudDownload, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("הורדת מודל ה-AI", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("פעם אחת, כ-1.6GB. ממשיך ברקע, מומלץ ב-Wi-Fi", color = C.Sub, fontSize = 12.sp)
                }
            }
            GradientButton("הורד מודל", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) { startDownload() }
            error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
        }
        is ModelStore.Status.Downloading -> Section {
            val pct = status.done.toFloat() / status.total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מוריד את המודל", color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GradientText("${(pct * 100).toInt()}%", 20.sp, brush = C.Primary)
            }
            GlowBar(pct, Modifier.fillMaxWidth(), C.Primary)
            Text("${status.done shr 20} / ${status.total shr 20} MB · אפשר לצאת מהאפליקציה", color = C.Sub, fontSize = 12.sp)
            SoftButton("בטל הורדה", Modifier.fillMaxWidth()) { ModelStore.cancel(ctx) }
        }
        is ModelStore.Status.Failed -> Section(title = "הורדת המודל נכשלה") {
            Text(status.message, color = C.Red, fontSize = 13.sp)
            GradientButton("נסה שוב", Modifier.fillMaxWidth()) { startDownload() }
        }
    }
}

@Composable
fun CreateScreen(onGallery: () -> Unit, onCamera: () -> Unit, onFiles: () -> Unit) {
    val ctx = LocalContext.current
    val status = rememberModelStatus()
    var backend by remember { mutableStateOf(Settings.backendChoice(ctx)) }
    var quality by remember { mutableStateOf(Settings.quality(ctx)) }
    var removeBg by remember { mutableStateOf(Settings.removeBackground(ctx)) }
    var smooth by remember { mutableStateOf(Settings.smooth(ctx)) }
    var sourceMenu by remember { mutableStateOf(false) }
    val image = AppState.input

    Column(Modifier.fillMaxSize()) {
        TopBar("תמונה לתלת־ממד", onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.enter(0)) {
                Box(
                    Modifier.fillMaxWidth().height(280.dp).glass(RoundedCornerShape(28.dp)).pressable { sourceMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) {
                        Image(image.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                        Row(Modifier.align(Alignment.TopStart).padding(12.dp).glass(CircleShape, strong = true)
                            .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = C.Text, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("החלף", color = C.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(Icons.Rounded.AddPhotoAlternate, 64.dp, C.Primary)
                        Spacer(Modifier.height(14.dp))
                        Text("העלה תמונה", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("גלריה · מצלמה · קבצים", color = C.Sub, fontSize = 13.sp)
                    }
                }
                DropdownMenu(sourceMenu, { sourceMenu = false }, containerColor = C.CardHigh,
                    shape = RoundedCornerShape(18.dp)) {
                    SourceItem(Icons.Rounded.PhotoLibrary, "גלריה") { sourceMenu = false; onGallery() }
                    SourceItem(Icons.Rounded.PhotoCamera, "מצלמה") { sourceMenu = false; onCamera() }
                    SourceItem(Icons.Rounded.FolderOpen, "קבצים") { sourceMenu = false; onFiles() }
                }
            }

            Row(Modifier.horizontalScroll(rememberScrollState()).enter(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tip in listOf("אובייקט אחד במרכז", "תאורה טובה", "רקע פשוט", "האובייקט שלם", "זווית מעט מלמעלה")) {
                    Chip(tip, C.Sub)
                }
            }

            Section(Modifier.enter(2), title = "מעבד") {
                BackendPicker(backend) { backend = it; Settings.setBackendChoice(ctx, it) }
            }
            Section(Modifier.enter(3), title = "רמת פירוט") {
                Segmented(Quality.entries.toList(), quality, { it.label }, { quality = it; Settings.setQuality(ctx, it) })
                Text("${quality.resolution}³ · ${quality.hint}", color = C.Sub, fontSize = 12.sp)
            }
            Section(Modifier.enter(4)) {
                ToggleRow("הסרת רקע אוטומטית", "מומלץ, אלא אם התמונה כבר בלי רקע", removeBg) {
                    removeBg = it; Settings.setRemoveBackground(ctx, it)
                }
                ToggleRow("החלקת משטח", "מסיר מדרגות קטנות ושומר על הצורה", smooth) {
                    smooth = it; Settings.setSmooth(ctx, it)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (status !is ModelStore.Status.Ready) {
                Text("קודם צריך להוריד את המודל (במסך הבית)", color = C.Amber, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center)
            }
            GradientButton("צור מודל 3D", Modifier.fillMaxWidth(), sparkles = true,
                enabled = image != null && status is ModelStore.Status.Ready && !AppState.isRunning) {
                AppState.start(backend, quality, removeBg, smooth)
            }
        }
    }
}

@Composable
private fun SourceItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem({ Text(label, color = C.Text, fontSize = 15.sp) }, onClick,
        leadingIcon = { Icon(icon, null, tint = C.Violet) })
}

@Composable
fun ProcessingScreen() {
    val ctx = LocalContext.current
    val job = AppState.job
    if (job == null) {
        LaunchedEffect(Unit) { AppState.back() }
        return
    }
    val img = AppState.framed ?: AppState.input
    val animate = LocalAnimations.current
    val scan = if (animate && job.error == null) rememberInfiniteTransition(label = "scan")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "s").value else 0.5f

    Column(Modifier.fillMaxSize()) {
        TopBar(if (job.error != null) "משהו השתבש" else "יוצר את המודל שלך", onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(if (job.error != null) 0f else job.overall, Modifier.size(250.dp).enter(0), stroke = 9.dp) {
                Box(Modifier.size(186.dp).clip(CircleShape).background(Color(0x33000000)), contentAlignment = Alignment.TopCenter) {
                    if (img != null) Image(img.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (job.error == null) Box(Modifier.fillMaxWidth().height(3.dp).offset(y = (186 * scan).dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, C.Violet, C.Orange, Color.Transparent))))
                }
            }

            if (job.error != null) {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = C.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("היצירה נכשלה", color = C.Text, fontWeight = FontWeight.Bold)
                    }
                    Text(job.error, color = C.Sub, fontSize = 14.sp)
                    GradientButton("העתק פרטי שגיאה", Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentCopy) {
                        Actions.copy(ctx, CrashLog.lastError ?: CrashLog.diagnostics(), "פרטי השגיאה")
                    }
                    if (job.backend != Backend.CPU) {
                        val next = Backend.entries[job.backend.ordinal + 1]
                        SoftButton("נסה שוב עם ${next.short}", Modifier.fillMaxWidth()) {
                            Settings.setBackendChoice(ctx, next)
                            AppState.back()
                            AppState.start(next, Settings.quality(ctx), Settings.removeBackground(ctx), Settings.smooth(ctx))
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.enter(1)) {
                    GradientText("${(job.overall * 100).toInt()}%", 46.sp, brush = C.Primary)
                    Text(job.status, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    val sec = if (LocalAnimations.current) rememberElapsed(job.startedAt) else 0L
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("רץ על ${job.backend.label}", C.Violet)
                        Chip("%d:%02d".format(sec / 60, sec % 60), C.Sub)
                    }
                }
            }

            Section(Modifier.enter(2)) {
                for (s in Stage.entries) {
                    val st = when {
                        job.done || s.ordinal < job.stage.ordinal -> StepState.DONE
                        s == job.stage && job.error == null -> StepState.ACTIVE
                        else -> StepState.PENDING
                    }
                    StepRow(s.label, st, if (st == StepState.ACTIVE && s.ordinal >= Stage.FIELD.ordinal)
                        "${(job.stageProgress * 100).toInt()}%" else null)
                }
            }
            if (job.error == null) Text("אפשר לצאת מהאפליקציה: העבודה ממשיכה ברקע ותקבל התראה כשהמודל מוכן.",
                color = C.Faint, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (job.error == null) SoftButton("ביטול", Modifier.fillMaxWidth()) { AppState.cancel() }
            else SoftButton("חזרה", Modifier.fillMaxWidth()) { AppState.job = null; AppState.back() }
        }
    }
}

object ProbeUi {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")
    var tick by mutableIntStateOf(0)
}

/** Runs the device test once in the background (after the model is downloaded). */
fun startProbe(ctx: android.content.Context) {
    if (ProbeUi.running || AppState.isRunning || !ModelStore.isReady(ctx)) return
    ProbeUi.running = true
    AppState.scope.launch {
        runCatching { Capabilities.probe(ctx) { ProbeUi.status = it } }
            .onFailure { CrashLog.error("device test", it) }
        ProbeUi.running = false
        ProbeUi.tick++
    }
}

@Composable
fun BackendPicker(choice: Backend?, onChoice: (Backend?) -> Unit) {
    val ctx = LocalContext.current
    val results = remember(ProbeUi.tick, ProbeUi.running) { Capabilities.results(ctx) }
    val options = listOf<Backend?>(null) + Backend.entries
    Segmented(options, choice, { it?.short ?: "אוטומטי" }, onChoice,
        disabled = { it != null && results[it]?.support in listOf(Support.UNSUPPORTED, Support.FAILED, Support.CRASHED) })
    if (choice == null) {
        Text(if (Capabilities.probed(ctx)) "ייבחר אוטומטית: ${Capabilities.best(ctx).label}, המהיר שעבד בבדיקה"
            else "בפעם הראשונה האפליקציה תבדוק אילו מעבדים עובדים ותבחר את המהיר", color = C.Sub, fontSize = 12.sp)
    }
    if (ProbeUi.running) Text(ProbeUi.status.ifEmpty { "בודק את המעבדים…" }, color = C.Violet, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        for (b in Backend.entries) {
            val r = results[b] ?: continue
            Chip("${b.short}: ${r.support.label}", when (r.support) {
                Support.OK -> C.Green
                Support.UNKNOWN -> C.Sub
                else -> C.Amber
            })
        }
    }
    if (choice != null && results[choice]?.support != Support.OK && results[choice]?.support != Support.UNKNOWN) {
        Text("${choice.short} לא עובד בטלפון הזה, היצירה עלולה להיכשל. מומלץ אוטומטי.", color = C.Amber, fontSize = 12.sp)
    }
}

/**
 * Offers the NPU pack (models pre-compiled for this phone's Snapdragon) once the main model is
 * installed. Hidden on other chips and while the pack isn't published yet.
 */
@Composable
fun NpuPackCard(status: ModelStore.Status, alwaysShow: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (com.triposr.mobile.NpuPack.chip() == null) {
        if (alwaysShow) Text("אין חבילת NPU לשבב הזה (${android.os.Build.SOC_MODEL}). GPU/CPU עובדים כרגיל.", color = C.Sub, fontSize = 13.sp)
        return
    }
    var size by remember { mutableStateOf<Long?>(null) }
    var checked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        size = runCatching { ModelStore.availableSize(ModelStore.NPU) }.getOrNull()
        checked = true
    }
    when (status) {
        ModelStore.Status.Ready -> if (alwaysShow) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("חבילת NPU מותקנת (${ModelStore.sizeOnDisk(ctx, ModelStore.NPU) shr 20} MB)", color = C.Green)
        }
        is ModelStore.Status.Downloading -> Section {
            val pct = status.done.toFloat() / status.total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מוריד חבילת NPU", color = C.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GradientText("${(pct * 100).toInt()}%", 20.sp, brush = C.Primary)
            }
            GlowBar(pct, Modifier.fillMaxWidth(), C.Primary)
            SoftButton("בטל", Modifier.fillMaxWidth()) { ModelStore.cancel(ctx, ModelStore.NPU) }
        }
        else -> {
            val s = size
            if (s == null) {
                if (alwaysShow && checked) Text("חבילת NPU לטלפון הזה עוד לא פורסמה.", color = C.Sub, fontSize = 13.sp)
                return
            }
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Memory, 44.dp, C.Primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("האצה עם ה-NPU", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("מודל שהוכן מראש לשבב של הטלפון · ${s shr 20} MB", color = C.Sub, fontSize = 12.sp)
                    }
                }
                if (status is ModelStore.Status.Failed) Text(status.message, color = C.Red, fontSize = 13.sp)
                GradientButton("הורד חבילת NPU", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) {
                    error = null
                    scope.launch {
                        runCatching { ModelStore.startDownload(ctx, ModelStore.NPU) }
                            .onFailure { CrashLog.error("NPU pack download", it); error = it.message }
                    }
                }
                error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
            }
        }
    }
}

/** Seconds since [start], ticking every second. */
@Composable
fun rememberElapsed(start: Long): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return (now - start) / 1000
}
