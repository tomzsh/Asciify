package com.asciify.studio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asciify.studio.model.*
import kotlin.math.roundToInt

@Composable fun DitherControls(settings: AsciiSettings, onSettings: (AsciiSettings) -> Unit) {
    val style = Styles.find(settings.styleId)
    StudioCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Shape the pattern", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SmallTag("DITHERED", Mint)
        }
        TextButton(onClick = { onSettings(settings.copy(ditherColumns = 160, ditherAmount = 1f, ditherTones = 0)) }) { Text("Reset pattern") }
        LabeledSlider("Pixel detail", "${settings.ditherColumns} columns", settings.ditherColumns.toFloat(), 48f..480f) {
            onSettings(settings.copy(ditherColumns = it.roundToInt()))
        }
        LabeledSlider("Dither amount", "${(settings.ditherAmount * 100).roundToInt()}%", settings.ditherAmount, 0f..1f) {
            onSettings(settings.copy(ditherAmount = it))
        }
        Text("Tone levels", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 2, 4, 8).forEach { tones ->
                FilterChip(selected = settings.ditherTones == tones, onClick = { onSettings(settings.copy(ditherTones = tones)) },
                    label = { Text(if (tones == 0) "Style · ${style.toneLevels}" else "$tones tones") })
            }
        }
        Text("Patterns stay fixed between frames. Increase detail for finer dots, or lower it for a chunkier pixel look.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        if (settings.palette().mode == ColorMode.ORIGINAL) Text("In source-color mode, levels apply to each color channel.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
