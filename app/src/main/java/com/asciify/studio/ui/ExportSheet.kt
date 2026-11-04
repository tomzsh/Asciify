@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.asciify.studio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.asciify.studio.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asciify.studio.data.EditorSession
import com.asciify.studio.model.*
import kotlin.math.roundToInt

@Composable fun ExportSheet(session: EditorSession, onSettings: (ExportSettings) -> Unit, onDismiss: () -> Unit, onExport: () -> Unit) {
    val settings = session.export
    val video = session.video ?: return
    val planResult = runCatching { ExportPlan.create(video, settings, session.startMs, session.endMs) }
    val plan = planResult.getOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("THE FINAL FRAME", Mint)
                Text("Ready for the world.", style = MaterialTheme.typography.headlineMedium)
                Text("Your characters, in a share-ready MP4.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            StudioCard {
                Text("Resolution", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(480, 720, 1080, 0).forEach { height ->
                        FilterChip(selected = settings.height == height, onClick = { onSettings(settings.copy(height = height)) },
                            label = { Text(if (height == 0) "Original" else "${height}p") })
                    }
                }
                Text("Keeps the source aspect ratio. Smaller videos are never enlarged.", color = Muted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = Line)
                Text("Frame rate", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12, 15, 24, 25, 30, 60).forEach { fps ->
                        FilterChip(selected = settings.fps == fps, onClick = { onSettings(settings.copy(fps = fps)) }, label = { Text("$fps") })
                    }
                }
                if (plan != null) Text("Output: ${fpsLabel(plan.fps)} fps · capped to source frame rate, without interpolation.", color = Muted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = Line)
                LabeledSlider("Video bitrate", "${settings.bitrateMbps} Mbps", settings.bitrateMbps.toFloat(), 1f..30f) {
                    onSettings(settings.copy(bitrateMbps = it.roundToInt()))
                }
                Text("Higher bitrate keeps more character detail and uses more storage.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            StudioCard {
                Text("Processing", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProcessingMode.entries.forEach { mode ->
                        FilterChip(selected = settings.mode == mode, onClick = { onSettings(settings.copy(mode = mode)) },
                            label = { Text(if (mode == ProcessingMode.AUTO) "Auto · recommended" else mode.name) })
                    }
                }
                Text(when (settings.mode) {
                    ProcessingMode.AUTO -> "Uses the GPU first. Retries with CPU rendering if the GPU pipeline is unsupported or fails."
                    ProcessingMode.GPU -> "OpenGL character rendering with Android video encoding. Reports an error if unavailable."
                    ProcessingMode.CPU -> "CPU character rendering and YUV conversion. Slower, with no OpenGL dependency for the effect."
                }, color = Muted, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = Line)
                ToggleRow("Keep original audio", if (video.hasAudio) "Retain sound from the selected section" else "This source has no audio track",
                    settings.keepAudio && video.hasAudio, video.hasAudio) { onSettings(settings.copy(keepAudio = it)) }
            }
            if (plan != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${plan.width} × ${plan.height}  ·  H.264 MP4", style = MaterialTheme.typography.labelLarge)
                        Text("${timeLabel(plan.durationMs)}  ·  estimated ${sizeLabel(plan.estimatedBytes)}", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    SmallTag("NO WATERMARK", Mint)
                }
            }
            planResult.exceptionOrNull()?.let { Text(it.message.orEmpty(), color = MaterialTheme.colorScheme.error) }
            Button(onClick = onExport, enabled = plan != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.FileDownload, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text("Export video")
            }
            Text("Saved to Movies / Asciify. Export continues in the background; Android may limit very long jobs.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
