package com.triposr.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0B0D17)
    val Card = Color(0xFF151827)
    val CardHigh = Color(0xFF1E2235)
    val Stroke = Color(0xFF2A2F45)
    val Purple = Color(0xFF7C4DFF)
    val Violet = Color(0xFFA855F7)
    val Orange = Color(0xFFFF8A4C)
    val Text = Color(0xFFF4F4FA)
    val Sub = Color(0xFF9AA0B8)
    val Green = Color(0xFF34D399)
    val Red = Color(0xFFF87171)
    val Accent = Brush.horizontalGradient(listOf(Purple, Orange))
    val AccentSoft = Brush.linearGradient(listOf(Color(0xFF241A55), Color(0xFF151827)))
}

@Composable
fun TripoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Purple, secondary = C.Violet, tertiary = C.Orange,
            background = C.Bg, surface = C.Card, onSurface = C.Text, onBackground = C.Text,
            surfaceVariant = C.CardHigh, outline = C.Stroke,
        ),
    ) {
        // The whole UI is Hebrew, so lay it out right-to-left regardless of the phone language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "חזרה", tint = C.Text) }
        } else Spacer(Modifier.width(16.dp))
        Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Section(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Card)
            .border(1.dp, C.Stroke, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        content()
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(18.dp)).background(C.Accent)
            .alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun SoftButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = C.Text,
    onClick: () -> Unit,
) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(C.CardHigh)
            .border(1.dp, C.Stroke, RoundedCornerShape(16.dp))
            .alpha(if (enabled) 1f else 0.45f).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, disabled: (T) -> Boolean = { false }) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (o in options) {
            val sel = o == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp))
                    .then(if (sel) Modifier.background(Brush.horizontalGradient(listOf(C.Purple, C.Violet))) else Modifier)
                    .then(if (sel) Modifier else Modifier.border(BorderStroke(1.dp, C.Stroke), RoundedCornerShape(11.dp)))
                    .alpha(if (disabled(o)) 0.4f else 1f)
                    .clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), color = if (sel) Color.White else C.Sub, fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = C.Sub, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Purple, checkedThumbColor = Color.White,
                uncheckedTrackColor = C.CardHigh, uncheckedBorderColor = C.Stroke),
        )
    }
}

enum class StepState { DONE, ACTIVE, PENDING }

@Composable
fun StepRow(label: String, state: StepState, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        when (state) {
            StepState.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = C.Green, modifier = Modifier.size(20.dp))
            StepState.ACTIVE -> Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(C.Violet))
            }
            StepState.PENDING -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = C.Stroke, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = when (state) { StepState.DONE -> C.Green; StepState.ACTIVE -> C.Text; else -> C.Sub },
            fontSize = 14.sp, fontWeight = if (state == StepState.ACTIVE) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        if (extra != null) Text(extra, color = C.Sub, fontSize = 12.sp)
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, stroke: Float = 14f, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension - stroke * 2
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(C.Stroke, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(C.Purple, C.Violet, C.Orange, C.Purple)),
                -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(d, d),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun Chip(text: String, color: Color = C.Violet) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp))
}
