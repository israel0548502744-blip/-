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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import com.triposr.mobile.CrashLog
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Project
import com.triposr.mobile.Quality
import com.triposr.mobile.Screen
import com.triposr.mobile.Settings
import com.triposr.mobile.Stage
import com.triposr.mobile.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Polls DownloadManager while visible. */
@Composable
fun rememberModelStatus(): ModelStore.Status {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(ModelStore.status(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            status = runCatching { ModelStore.status(ctx) }.getOrDefault(ModelStore.Status.Missing)
            delay(if (status is ModelStore.Status.Downloading) 600 else 2000)
        }
    }
    return status
}

@Composable
fun BottomNav(current: Screen) {
    Row(
        Modifier.fillMaxWidth().background(C.Card).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Rounded.Home, "בית", current == Screen.Home) { AppState.navigateRoot(Screen.Home) }
        NavItem(Icons.Rounded.GridView, "פרויקטים", current == Screen.Projects) { AppState.navigateRoot(Screen.Projects) }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(C.Accent).clickable { AppState.navigate(Screen.Create) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "צור", tint = Color.White, modifier = Modifier.size(30.dp)) }
        NavItem(Icons.Rounded.Settings, "הגדרות", current == Screen.Settings) { AppState.navigateRoot(Screen.Settings) }
        NavItem(Icons.Rounded.BugReport, "דוחות", current == Screen.Diagnostics) { AppState.navigateRoot(Screen.Diagnostics) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) C.Violet else C.Sub)
        Text(label, color = if (selected) C.Violet else C.Sub, fontSize = 11.sp)
    }
}

@Composable
fun Logo(size: Int = 64) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5).dp))
            .background(Brush.linearGradient(listOf(C.Purple, C.Violet))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.ViewInAr, null, tint = Color.White, modifier = Modifier.size((size * 0.6).dp)) }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val status = rememberModelStatus()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Logo()
            Spacer(Modifier.width(14.dp))
            Column {
                Text("TripoSR 3D", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("הופכים תמונה למודל תלת־ממד — על הטלפון", color = C.Sub, fontSize = 14.sp)
            }
        }

        CrashCard()
        AppState.notice?.let { NoticeCard(it) { AppState.notice = null } }
        UpdateCard()
        ModelCard(status)

        AppState.job?.takeIf { AppState.isRunning }?.let { job ->
            Section(Modifier.clickable { AppState.navigate(Screen.Processing) }) {
                Text("יצירת מודל בתהליך… ${(job.overall * 100).toInt()}%", color = C.Text, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { job.overall }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = C.Violet, trackColor = C.Stroke)
                Text(job.status, color = C.Sub, fontSize = 13.sp)
            }
        }

        GradientButton("צור מודל חדש", Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome) {
            AppState.navigate(Screen.Create)
        }

        if (AppState.projects.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("אחרונים", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("הכל", color = C.Violet, fontSize = 14.sp, modifier = Modifier.clickable { AppState.navigateRoot(Screen.Projects) })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AppState.projects.take(8), key = { it.id }) { p -> ProjectThumb(p, Modifier.width(130.dp)) }
            }
        }

        Section(title = "מה אפשר לעשות") {
            Feature(Icons.Rounded.Memory, "רץ על ה-NPU / GPU של הטלפון", "בלי שרת ובלי אינטרנט אחרי הורדת המודל")
            Feature(Icons.Rounded.ViewInAr, "תצוגה תלת־ממדית אינטראקטיבית", "סיבוב, זום, רשת, תאורה")
            Feature(Icons.Rounded.FolderOpen, "ייצוא GLB / OBJ / STL / PLY", "להדפסה תלת־ממדית, Blender, משחקים")
            Feature(Icons.Rounded.Share, "שיתוף בלחיצה", "WhatsApp, Telegram, Drive ועוד")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Purple.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.Violet, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = C.Sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun ProjectThumb(p: Project, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(p.id) { p.thumb(ctx)?.asImageBitmap() }
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.Stroke, RoundedCornerShape(16.dp))
        .clickable { AppState.open(p) }.padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(C.CardHigh)) {
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(p.name, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(p.date, color = C.Sub, fontSize = 11.sp)
    }
}

@Composable
fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Section {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Warning, null, tint = C.Orange)
            Spacer(Modifier.width(10.dp))
            Text(text, color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, "סגור", tint = C.Sub, modifier = Modifier.clickable(onClick = onDismiss))
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("העתק דוח קריסה", Modifier.weight(1f), icon = Icons.Rounded.ContentCopy) {
                Actions.copy(ctx, r, "דוח הקריסה")
            }
            SoftButton("סגור", Modifier.weight(0.5f)) { CrashLog.dismissPending(); report = null }
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
            Icon(Icons.Rounded.SystemUpdate, null, tint = C.Green)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("גרסה חדשה זמינה (build ${u.build})", color = C.Text, fontWeight = FontWeight.Bold)
                Text("${u.sizeMb} MB בלבד — המודל לא יורד שוב", color = C.Sub, fontSize = 12.sp)
            }
        }
        when {
            id == null -> GradientButton("עדכן עכשיו", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) {
                UpdateFlow.downloadId = Updater.download(ctx, u)
            }
            UpdateFlow.progress < 0f -> SoftButton("ההורדה נכשלה — נסה שוב", Modifier.fillMaxWidth()) { UpdateFlow.downloadId = null }
            UpdateFlow.progress >= 1f -> GradientButton("התקן", Modifier.fillMaxWidth()) { Updater.install(ctx, u) }
            else -> LinearProgressIndicator(progress = { UpdateFlow.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = C.Green, trackColor = C.Stroke)
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
        ModelStore.Status.Ready -> if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("המודל מותקן ומוכן", color = C.Green, fontSize = 13.sp)
        } else Text("המודל מותקן (${ModelStore.sizeOnDisk(ctx) shr 20} MB)", color = C.Green)
        ModelStore.Status.Missing -> Section(title = "צריך להוריד את המודל פעם אחת") {
            Text("כ-1.7GB. ההורדה ממשיכה גם אם יוצאים מהאפליקציה. מומלץ ב-Wi-Fi.", color = C.Sub, fontSize = 13.sp)
            GradientButton("הורד מודל", Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudDownload) { startDownload() }
            error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
        }
        is ModelStore.Status.Downloading -> Section(title = "מוריד את המודל…") {
            val pct = status.done.toFloat() / status.total
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = C.Violet, trackColor = C.Stroke)
            Row {
                Text("${status.done shr 20} / ${status.total shr 20} MB", color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${(pct * 100).toInt()}%", color = C.Text, fontWeight = FontWeight.Bold)
            }
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
    var backend by remember { mutableStateOf(Settings.backend(ctx)) }
    var quality by remember { mutableStateOf(Settings.quality(ctx)) }
    var removeBg by remember { mutableStateOf(Settings.removeBackground(ctx)) }
    var smooth by remember { mutableStateOf(Settings.smooth(ctx)) }
    val image = AppState.input

    Column(Modifier.fillMaxSize()) {
        TopBar("תמונה לתלת־ממד", onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section {
                Box(
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(C.Bg)
                        .border(1.5.dp, if (image == null) C.Purple.copy(alpha = 0.5f) else C.Stroke, RoundedCornerShape(16.dp))
                        .clickable(onClick = onGallery),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) {
                        Image(image.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                        Text("שנה תמונה", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.PhotoLibrary, null, tint = C.Violet, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("בחר תמונה של אובייקט", color = C.Text, fontWeight = FontWeight.SemiBold)
                        Text("או צלם עכשיו", color = C.Sub, fontSize = 13.sp)
                    }
                }
                if (image != null) Text("התמונה נטענה בהצלחה", color = C.Green, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("גלריה", Modifier.weight(1f), Icons.Rounded.PhotoLibrary, onClick = onGallery)
                    SoftButton("מצלמה", Modifier.weight(1f), Icons.Rounded.PhotoCamera, onClick = onCamera)
                    SoftButton("קבצים", Modifier.weight(1f), Icons.Rounded.FolderOpen, onClick = onFiles)
                }
            }

            Section(title = "טיפים לתוצאה טובה") {
                for (tip in listOf("אובייקט אחד במרכז התמונה", "תאורה טובה ואחידה", "רקע פשוט וחלק",
                        "האובייקט שלם, לא חתוך בשוליים", "צילום מזווית קצת מלמעלה")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = C.Violet, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tip, color = C.Sub, fontSize = 13.sp)
                    }
                }
            }

            Section(title = "מעבד (Hardware)") {
                Segmented(Backend.entries.toList(), backend, { it.short }, { backend = it; Settings.setBackend(ctx, it) })
                val hint = when (backend) {
                    Backend.NPU -> "הכי מהיר — ניסיוני, בפעם הראשונה הכנה של כמה דקות"
                    Backend.GPU -> "מומלץ — עובד מצוין על S25"
                    Backend.CPU -> "הכי יציב, איטי יותר"
                }
                Text(hint, color = C.Sub, fontSize = 12.sp)
                if (AppState.crashedBefore(backend)) Chip("${backend.short} נסגר בפעם הקודמת", C.Orange)
            }

            Section(title = "רמת פירוט") {
                Segmented(Quality.entries.toList(), quality, { "${it.label} ${it.resolution}" }, { quality = it; Settings.setQuality(ctx, it) })
                Text(quality.hint, color = C.Sub, fontSize = 12.sp)
            }

            Section {
                ToggleRow("הסרת רקע אוטומטית", "מומלץ, אלא אם התמונה כבר בלי רקע", removeBg) {
                    removeBg = it; Settings.setRemoveBackground(ctx, it)
                }
                ToggleRow("החלקת משטח", "מסיר מדרגות קטנות ושומר על הצורה", smooth) {
                    smooth = it; Settings.setSmooth(ctx, it)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(16.dp)) {
            if (status !is ModelStore.Status.Ready) {
                Text("קודם צריך להוריד את המודל (במסך הבית)", color = C.Orange, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center)
            }
            GradientButton("צור מודל 3D", Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome,
                enabled = image != null && status is ModelStore.Status.Ready && !AppState.isRunning) {
                AppState.start(backend, quality, removeBg, smooth)
            }
        }
    }
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
    val anim = rememberInfiniteTransition(label = "scan")
    val scan by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "scan")

    Column(Modifier.fillMaxSize()) {
        TopBar(if (job.error != null) "משהו השתבש" else "מעבד תמונה לתלת־ממד…", onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(200.dp).clip(RoundedCornerShape(24.dp)).background(C.Card), contentAlignment = Alignment.TopCenter) {
                if (img != null) Image(img.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (job.error == null) Box(Modifier.fillMaxWidth().height(3.dp).offset(y = (200 * scan).dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, C.Violet, C.Orange, Color.Transparent))))
            }

            if (job.error != null) {
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = C.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("השגיאה", color = C.Text, fontWeight = FontWeight.Bold)
                    }
                    Text(job.error, color = C.Sub, fontSize = 14.sp)
                    GradientButton("העתק פרטי שגיאה", Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentCopy) {
                        Actions.copy(ctx, CrashLog.lastError ?: CrashLog.diagnostics(), "פרטי השגיאה")
                    }
                    if (job.backend != Backend.CPU) {
                        val next = Backend.entries[job.backend.ordinal + 1]
                        SoftButton("נסה שוב עם ${next.short}", Modifier.fillMaxWidth()) {
                            Settings.setBackend(ctx, next)
                            AppState.back()
                            AppState.start(next, Settings.quality(ctx), Settings.removeBackground(ctx), Settings.smooth(ctx))
                        }
                    }
                }
            } else {
                ProgressRing(job.overall, Modifier.size(170.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(job.overall * 100).toInt()}%", color = C.Text, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                        Text(job.backend.short, color = C.Violet, fontSize = 13.sp)
                    }
                }
                Text(job.status, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            Section {
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
            if (job.error == null) Text("אפשר לצאת מהאפליקציה — העבודה ממשיכה ברקע ותקבל התראה כשהמודל מוכן.",
                color = C.Sub, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Box(Modifier.padding(16.dp)) {
            if (job.error == null) SoftButton("ביטול", Modifier.fillMaxWidth()) { AppState.cancel() }
            else SoftButton("חזרה", Modifier.fillMaxWidth()) { AppState.job = null; AppState.back() }
        }
    }
}
