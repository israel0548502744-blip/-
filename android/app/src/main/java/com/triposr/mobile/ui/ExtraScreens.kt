package com.triposr.mobile.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triposr.mobile.AppState
import com.triposr.mobile.CrashLog
import com.triposr.mobile.Creations
import com.triposr.mobile.Enhance
import com.triposr.mobile.Extras
import com.triposr.mobile.Generator
import com.triposr.mobile.ModelStore
import com.triposr.mobile.Settings
import kotlinx.coroutines.launch

/** Lithophane, relief and QR plates: printable products that don't depend on 3D reconstruction. */
@Composable
fun ExtraScreen() {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(Creations.Kind.LITHOPHANE) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var text by remember { mutableStateOf("https://") }
    var frame by remember { mutableStateOf(true) }
    var invert by remember { mutableStateOf(false) }
    var maxMm by remember { mutableFloatStateOf(3f) }
    var reliefMm by remember { mutableFloatStateOf(8f) }
    var flatBg by remember { mutableStateOf(true) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) image = Actions.loadBitmap(ctx, uri) ?: run { Actions.toast(ctx, "לא הצלחתי לפתוח את התמונה"); null }
    }
    val busy = Creations.busy

    Column(Modifier.fillMaxSize()) {
        TopBar("יצירות להדפסה", onBack = { AppState.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Segmented(Creations.Kind.entries.toList(), kind, { it.label }, { kind = it })
            Text(when (kind) {
                Creations.Kind.LITHOPHANE -> "לוח דק שהתמונה מופיעה בו כשמאירים מאחור. מודפס בלבן, הכי פשוט ותמיד מצליח."
                Creations.Kind.RELIEF -> "התמונה בולטת מהלוח לפי העומק שלה, עם הצבעים שלה."
                Creations.Kind.QR -> "לוח עם קוד QR בולט שנסרק בטלפון. החלף פילמנט בגובה 2 מ״מ לשני צבעים."
            }, color = C.Sub, fontSize = 13.sp)

            if (kind == Creations.Kind.QR) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("קישור או טקסט") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                        focusedBorderColor = C.Violet, unfocusedBorderColor = C.Faint, focusedLabelColor = C.Violet, unfocusedLabelColor = C.Sub))
                val qr = remember(text) { runCatching { if (text.isBlank()) null else Creations.qrBitmap(text.trim(), 6) }.getOrNull() }
                Box(Modifier.fillMaxWidth().height(240.dp).glass(RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                    if (qr != null) Image(qr.asImageBitmap(), null, Modifier.size(200.dp).clip(RoundedCornerShape(10.dp)))
                    else Text("הכנס קישור", color = C.Sub)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(260.dp).glass(RoundedCornerShape(26.dp))
                    .pressable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center) {
                    val img = image
                    if (img != null) Image(img.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconTile(Icons.Rounded.AddPhotoAlternate, 56.dp, C.Primary)
                        Spacer(Modifier.height(10.dp))
                        Text("בחר תמונה", color = C.Text, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when (kind) {
                Creations.Kind.LITHOPHANE -> Section(title = "אפשרויות") {
                    ToggleRow("מסגרת", "שוליים עבים שמחזיקים את הלוח ישר", frame) { frame = it }
                    ToggleRow("נגטיב", "הופך בהיר וכהה", invert) { invert = it }
                    Text("עובי מקסימלי: %.1f מ״מ".format(maxMm), color = C.Text, fontSize = 14.sp)
                    Slider(maxMm, { maxMm = (it * 2).toInt() / 2f }, valueRange = 2f..5f, colors = sliderColors())
                    Text("עבה יותר = ניגודיות גבוהה יותר, אבל צריך אור חזק יותר. הדק ביותר 0.8 מ״מ.", color = C.Sub, fontSize = 12.sp)
                }
                Creations.Kind.RELIEF -> {
                    ExtraNeeded(Extras.DEPTH)
                    Section(title = "אפשרויות") {
                        ToggleRow("רקע שטוח", "רק האובייקט בולט", flatBg) { flatBg = it }
                        Text("גובה התבליט: %.0f מ״מ".format(reliefMm), color = C.Text, fontSize = 14.sp)
                        Slider(reliefMm, { reliefMm = it.toInt().toFloat() }, valueRange = 3f..15f, colors = sliderColors())
                    }
                }
                Creations.Kind.QR -> {}
            }
            Text("גודל: 100 מ״מ בצלע הארוכה. אחרי היצירה תמצא ייצוא 3MF/STL להדפסה.", color = C.Faint, fontSize = 12.sp)
            Creations.error?.let { Text(it, color = C.Red, fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (busy != null) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = C.Violet, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(busy, color = C.Sub, fontSize = 13.sp)
            }
            val ready = when (kind) {
                Creations.Kind.QR -> text.isNotBlank() && text.trim() != "https://"
                Creations.Kind.RELIEF -> image != null && Extras.ready(ctx, Extras.DEPTH)
                Creations.Kind.LITHOPHANE -> image != null
            }
            GradientButton("צור ${kind.label}", Modifier.fillMaxWidth(), sparkles = true, enabled = ready && busy == null) {
                Creations.create(kind, image, text, Creations.Options(frame, maxMm, invert, reliefMm, flatBg))
            }
        }
    }
}

/** Inline download card when a creation needs an extra that isn't installed. */
@Composable
private fun ExtraNeeded(e: Extras.Extra) {
    val status = rememberModelStatus(e.pack)
    if (status is ModelStore.Status.Ready) return
    ExtraRow(e, status)
}

/** Settings section: the optional enhancement models, each downloaded only on request. */
@Composable
fun ExtrasSection() {
    Section(title = "שיפורים להורדה (לא חובה)") {
        Text("מודלים נוספים שמשפרים את התוצאה. כל אחד יורד רק אם תבחר בו, ואפשר למחוק בכל רגע.", color = C.Sub, fontSize = 12.sp)
        for (e in Extras.all) ExtraRow(e, rememberModelStatus(e.pack))
    }
}

/** Extra image-to-3D models (Settings). Only on chips with a pack compiled in CI. */
@Composable
fun ModelPacksSection() {
    if (com.triposr.mobile.NpuPack.chip() == null) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pack = ModelStore.TRIPOSG
    val status = rememberModelStatus(pack)
    var size by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { size = runCatching { ModelStore.availableSize(pack) }.getOrNull() }
    Section(title = "מודלים נוספים") {
        Text("אחרי ההורדה אפשר לבחור במסך היצירה איזה מודל יבנה את התלת־ממד.", color = C.Sub, fontSize = 12.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Memory, 36.dp, C.Accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("TripoSG · ניסיוני", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("צורה מדויקת וחדה יותר מ-TripoSR, בלי צבע. רץ על ה-NPU, כדקה למודל.", color = C.Sub, fontSize = 12.sp)
                }
            }
            when (status) {
                ModelStore.Status.Ready ->
                    SoftButton("מחק · ${ModelStore.sizeOnDisk(ctx, pack) shr 20} MB", Modifier.fillMaxWidth(), Icons.Rounded.Delete, color = C.Red) {
                        if (Settings.generator(ctx) == Generator.TRIPOSG) Settings.setGenerator(ctx, Generator.TRIPOSR)
                        ModelStore.delete(ctx, pack)
                    }
                is ModelStore.Status.Downloading -> {
                    GlowBar(status.done.toFloat() / status.total, Modifier.fillMaxWidth(), C.Primary)
                    SoftButton("בטל", Modifier.fillMaxWidth()) { ModelStore.cancel(ctx, pack) }
                }
                else -> {
                    if (status is ModelStore.Status.Failed) Text(status.message, color = C.Red, fontSize = 12.sp)
                    if (size == null) Text("החבילה עוד לא פורסמה לשבב הזה", color = C.Faint, fontSize = 12.sp)
                    SoftButton("הורד" + (size?.let { " · ${it shr 20} MB" } ?: ""), Modifier.fillMaxWidth(),
                        Icons.Rounded.CloudDownload, enabled = size != null) {
                        err = null
                        scope.launch {
                            runCatching { ModelStore.startDownload(ctx, pack) }
                                .onFailure { CrashLog.error("TripoSG download", it); err = it.message }
                        }
                    }
                }
            }
            err?.let { Text(it, color = C.Red, fontSize = 12.sp) }
            Text("רישיון: MIT (VAST-AI-Research). חלקים מקוד המודל מבוססים על Tencent Hunyuan.", color = C.Faint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ExtraRow(e: Extras.Extra, status: ModelStore.Status) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf<Long?>(null) }
    var active by remember { mutableStateOf(Extras.active(ctx, e)) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { size = runCatching { ModelStore.availableSize(e.pack) }.getOrNull() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(when (e) {
                Extras.DEPTH -> Icons.Rounded.Terrain
                Extras.TAP -> Icons.Rounded.CheckCircle
                Extras.UPSCALE -> Icons.Rounded.Lightbulb
                else -> Icons.Rounded.AddPhotoAlternate
            }, 36.dp, C.Accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(e.detail, color = C.Sub, fontSize = 12.sp)
            }
        }
        when (status) {
            ModelStore.Status.Ready -> {
                if (e.usesToggle) ToggleRow("פעיל", null, active) { active = it; Extras.setActive(ctx, e, it) }
                SoftButton("מחק · ${ModelStore.sizeOnDisk(ctx, e.pack) shr 20} MB", Modifier.fillMaxWidth(), Icons.Rounded.Delete, color = C.Red) {
                    Enhance.close(); ModelStore.delete(ctx, e.pack)
                }
            }
            is ModelStore.Status.Downloading -> {
                GlowBar(status.done.toFloat() / status.total, Modifier.fillMaxWidth(), C.Primary)
                SoftButton("בטל", Modifier.fillMaxWidth()) { ModelStore.cancel(ctx, e.pack) }
            }
            else -> {
                if (status is ModelStore.Status.Failed) Text(status.message, color = C.Red, fontSize = 12.sp)
                SoftButton("הורד" + (size?.let { " · ${maxOf(1, it shr 20)} MB" } ?: ""), Modifier.fillMaxWidth(),
                    Icons.Rounded.CloudDownload, enabled = size != null) {
                    err = null
                    scope.launch {
                        runCatching { ModelStore.startDownload(ctx, e.pack) }
                            .onFailure { CrashLog.error("extra download ${e.pack.key}", it); err = it.message }
                    }
                }
            }
        }
        err?.let { Text(it, color = C.Red, fontSize = 12.sp) }
        Text("רישיון: ${e.license}", color = C.Faint, fontSize = 11.sp, textAlign = TextAlign.Start)
    }
}
