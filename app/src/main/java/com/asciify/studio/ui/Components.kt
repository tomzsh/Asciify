package com.asciify.studio.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable fun Eyebrow(text: String, color: Color = Muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable fun StudioCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(20.dp))
        .background(Panel, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable fun SmallTag(text: String, tint: Color = Muted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = tint,
        modifier = Modifier.background(tint.copy(alpha = 0.09f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
}

@Composable fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, color = Mint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label; stateDescription = valueText })
    }
}

@Composable fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

fun timeLabel(ms: Long): String = String.format(Locale.US, "%02d:%02d.%01d", ms / 60_000, ms % 60_000 / 1000, ms % 1000 / 100)
fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000_000) String.format(Locale.US, "%.1f GB", bytes / 1e9) else String.format(Locale.US, "%.1f MB", bytes / 1e6)
fun fpsLabel(fps: Float): String = if (fps % 1f < 0.01f) "${fps.toInt()}" else String.format(Locale.US, "%.2f", fps)
