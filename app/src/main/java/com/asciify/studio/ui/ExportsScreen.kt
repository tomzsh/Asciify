package com.asciify.studio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import com.asciify.studio.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asciify.studio.data.*
import java.text.SimpleDateFormat
import java.util.*

@Composable fun ExportsScreen(state: ExportState, records: List<ExportRecord>, onCancel: () -> Unit,
    onOpen: (ExportRecord) -> Unit, onShare: (ExportRecord) -> Unit, onForget: (String) -> Unit, onStudio: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("MADE OF CHARACTERS", Mint)
                Text("Your creations.", style = MaterialTheme.typography.headlineLarge)
                Text("Ready to save, share, and make someone look twice.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.running) item {
            StudioCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Memory, null, tint = Mint)
                    Column(Modifier.weight(1f)) {
                        Text("Rendering your creation", style = MaterialTheme.typography.titleMedium)
                        Text(state.stage, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${state.progress}%", color = Mint, style = MaterialTheme.typography.titleMedium)
                }
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth(), trackColor = Line)
                Text("You can keep editing or leave the app while this finishes.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel export") }
            }
        }
        if (!state.running && state.stage.isNotBlank()) item { Text(state.stage, style = MaterialTheme.typography.bodySmall, color = Muted) }
        if (records.isEmpty() && !state.running) item {
            StudioCard(Modifier.padding(top = 24.dp)) {
                Icon(Icons.Outlined.MovieCreation, null, Modifier.size(40.dp), tint = Mint)
                Text("A blank canvas, for now.", style = MaterialTheme.typography.titleLarge)
                Text("Import a video, find your style, and export your first creation. It will appear here.", color = Muted)
                Button(onClick = onStudio) { Text("Go to studio"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
            }
        }
        if (records.isNotEmpty()) item { Eyebrow("${records.size} ${if (records.size == 1) "CREATION" else "CREATIONS"}  /  SAVED ON THIS DEVICE") }
        items(records, key = { it.uri }) { record ->
            var menu by remember { mutableStateOf(false) }
            StudioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TaskAlt, null, tint = Mint, modifier = Modifier.padding(end = 12.dp).size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.style, style = MaterialTheme.typography.titleMedium)
                        Text(SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(record.created)), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "More options for ${record.name}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Remove from list") }, onClick = { menu = false; onForget(record.uri) },
                                leadingIcon = { Icon(Icons.Outlined.PlaylistRemove, null) })
                        }
                    }
                }
                Text("${record.width} × ${record.height}  ·  ${fpsLabel(record.fps)} fps  ·  ${sizeLabel(record.bytes)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallTag("MP4"); SmallTag(record.processor)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onOpen(record) }) { Text("Play") }
                    FilledTonalButton(onClick = { onShare(record) }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share") }
                }
            }
        }
    }
}
