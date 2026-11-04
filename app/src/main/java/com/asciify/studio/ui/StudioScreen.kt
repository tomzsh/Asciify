package com.asciify.studio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import com.asciify.studio.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asciify.studio.data.EditorSession
import com.asciify.studio.model.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable fun StudioScreen(session: EditorSession, sample: Bitmap, favorites: Set<String>,
    onImport: () -> Unit, onAscii: (AsciiSettings) -> Unit, onFavorite: (String) -> Unit,
    onTrim: (Long, Long) -> Unit, onBrowseStyles: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ascii = session.ascii
    val style = Styles.find(ascii.styleId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("LESS PIXELS. MORE PERSONALITY.", Mint)
            Text(if (style.renderMode == RenderMode.DITHER) "A different\npoint of view." else "Make every\ncharacter count.", style = MaterialTheme.typography.headlineLarge)
            Text(if (style.renderMode == RenderMode.DITHER) "Turn motion into print, pixels, and pattern." else "Turn your videos into something unexpected.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        VideoPreview(session.video, ascii, sample, session.startMs, session.endMs)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = PanelRaised, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.VideoFile, null, Modifier.padding(12.dp).size(22.dp), tint = Muted)
            }
            Column(Modifier.weight(1f)) {
                Text(session.video?.name ?: "Your next creation starts here", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Text(session.video?.let { "${it.width} × ${it.height}  ·  ${fpsLabel(it.frameRate)} fps  ·  ${timeLabel(it.durationMs)}" }
                    ?: "MP4, MOV, WebM & more · device codec support", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onImport) { Icon(if (session.video == null) Icons.Outlined.Add else Icons.Outlined.SwapHoriz,
                if (session.video == null) "Import video" else "Replace video", tint = Mint) }
        }
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Style", "Color", "Adjust", "Trim").forEachIndexed { index, title ->
                TextButton(onClick = { tab = index }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = if (tab == index) PanelRaised else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (tab == index) Mint else Muted)) { Text(title) }
            }
        }
        when (tab) {
            0 -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Choose your look", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onBrowseStyles) { Text("All ${Styles.all.size} styles"); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 4.dp).size(16.dp)) }
                }
                StyleStrip(ascii.styleId, favorites, sample, { onAscii(ascii.copy(styleId = it)) }, onFavorite)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(2.dp).height(32.dp).background(Mint))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(style.description, style = MaterialTheme.typography.bodySmall, color = Muted)
                        if (style.renderMode == RenderMode.ASCII) Text(style.glyphs, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, color = Mint)
                        else Text("${style.toneLevels} tones by default · colors are yours to choose", style = MaterialTheme.typography.bodySmall, color = Mint)
                    }
                }
                if (style.renderMode == RenderMode.DITHER) DitherControls(ascii, onAscii)
                else LabeledSlider("Character detail", "${ascii.columns} columns", ascii.columns.toFloat(), 24f..240f) { onAscii(ascii.copy(columns = it.roundToInt())) }
            }
            1 -> ColorControls(ascii, onAscii)
            2 -> StudioCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Make it yours", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onAscii(ascii.copy(columns = if (style.renderMode == RenderMode.ASCII) 100 else ascii.columns,
                        contrast = 1.15f, brightness = 0f, gamma = 1f, invert = false)) }) { Text("Reset") }
                }
                if (style.renderMode == RenderMode.ASCII) LabeledSlider("Character detail", "${ascii.columns} columns", ascii.columns.toFloat(), 24f..240f) { onAscii(ascii.copy(columns = it.roundToInt())) }
                LabeledSlider("Contrast", "${(ascii.contrast * 100).roundToInt()}%", ascii.contrast, 0.5f..2.5f) { onAscii(ascii.copy(contrast = it)) }
                LabeledSlider("Brightness", "%+d".format((ascii.brightness * 100).roundToInt()), ascii.brightness, -0.4f..0.4f) { onAscii(ascii.copy(brightness = it)) }
                LabeledSlider("Gamma", "%.2f".format(Locale.US, ascii.gamma), ascii.gamma, 0.5f..2f) { onAscii(ascii.copy(gamma = it)) }
                HorizontalDivider(color = Line)
                ToggleRow(if (style.renderMode == RenderMode.DITHER) "Invert tones" else "Invert characters", "Swap the light and dark areas", ascii.invert) { onAscii(ascii.copy(invert = it)) }
            }
            3 -> TrimPanel(session, onTrim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
            Icon(Icons.Outlined.Memory, null, Modifier.size(16.dp), tint = Muted)
            Text("GPU first. CPU when you need it. Always on-device.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable private fun TrimPanel(session: EditorSession, onTrim: (Long, Long) -> Unit) {
    val video = session.video
    var start by remember(session.startMs) { mutableStateOf(String.format(Locale.US, "%.2f", session.startMs / 1000.0)) }
    var end by remember(session.endMs) { mutableStateOf(String.format(Locale.US, "%.2f", session.endMs / 1000.0)) }
    var error by remember { mutableStateOf<String?>(null) }
    StudioCard {
        Text("Keep the best part", style = MaterialTheme.typography.titleMedium)
        if (video == null) Text("Import a video to set its start and end points.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        else {
            Text("Selected · ${timeLabel(session.endMs - session.startMs)}", color = Mint, style = MaterialTheme.typography.bodySmall)
            RangeSlider(value = session.startMs.toFloat()..session.endMs.toFloat(), onValueChange = { range ->
                if (range.endInclusive - range.start >= 100) onTrim(range.start.toLong(), range.endInclusive.toLong())
            }, valueRange = 0f..video.durationMs.toFloat())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it; error = null }, label = { Text("Start (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = end, onValueChange = { end = it; error = null }, label = { Text("End (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onTrim(0, video.durationMs); error = null }) { Text("Use full video") }
                OutlinedButton(onClick = {
                    val s = start.toDoubleOrNull()?.times(1000)?.toLong()
                    val e = end.toDoubleOrNull()?.times(1000)?.toLong()
                    if (s == null || e == null || s < 0 || e > video.durationMs || e - s < 100) error = "Enter valid times with at least 0.1 seconds selected."
                    else { onTrim(s, e); error = null }
                }) { Text("Apply trim") }
            }
        }
    }
}
