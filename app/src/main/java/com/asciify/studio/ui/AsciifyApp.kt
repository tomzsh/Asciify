package com.asciify.studio.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.asciify.studio.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asciify.studio.StudioViewModel
import com.asciify.studio.data.ExportRecord
import com.asciify.studio.engine.demoFrame
import com.asciify.studio.model.Styles

@Composable fun AsciifyApp(vm: StudioViewModel) {
    val context = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val thumbnail by vm.thumbnail.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val state by vm.exportState.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val sample = remember { demoFrame() }
    val preview = thumbnail ?: sample
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.import(it); page = 0
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.startExport(); sheet = false; page = 2
    }
    val import: () -> Unit = { picker.launch(arrayOf("video/*")) }
    fun beginExport() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else { vm.startExport(); sheet = false; page = 2 }
    }
    fun external(record: ExportRecord, share: Boolean) {
        try {
            val uri = Uri.parse(record.uri)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { } ?: error("This video is no longer available in the gallery.")
            val intent = if (share) Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "Asciify video", uri)
            } else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, if (share) "Share your creation" else "Play your creation"))
        } catch (error: Exception) { vm.message(error.localizedMessage ?: "No app is available to open this video.") }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    BackHandler(enabled = page != 0 && !sheet) { page = 0 }
    Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, null, tint = Mint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("asciify", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = import, enabled = !loading, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Import")
                }
                IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, "About Asciify", tint = Muted) }
            }
            HorizontalDivider(color = Line.copy(alpha = 0.6f))
        }
    }, bottomBar = {
        Column {
            if (page != 2) {
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.running) "Creating your video" else if (session.video == null) "Your footage, reimagined." else "Ready when you are.", style = MaterialTheme.typography.labelLarge)
                        Text(if (state.running) "${state.progress}% · tap to view" else if (session.video == null) "Local. Private. No watermark." else "${timeLabel(session.endMs - session.startMs)} · MP4 export", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Button(onClick = { if (state.running) page = 2 else if (session.video == null) import() else sheet = true }, enabled = !loading,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                        Icon(if (state.running) Icons.Outlined.HourglassTop else if (session.video == null) Icons.Outlined.Add else Icons.Outlined.NorthEast, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (state.running) "View" else if (session.video == null) "Add video" else "Export")
                    }
                }
            }
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                val icons = listOf(Icons.Outlined.Tune, Icons.Outlined.GridView, Icons.Outlined.VideoLibrary)
                listOf("Studio", "Styles", "Exports").forEachIndexed { index, title ->
                    NavigationBarItem(selected = page == index, onClick = { page = index }, label = { Text(title) },
                        icon = { Icon(icons[index], null) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Mint, selectedTextColor = Mint, indicatorColor = PanelRaised, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                when (page) {
                    0 -> StudioScreen(session, preview, favorites, import, vm::ascii, vm::favorite, vm::trim) { page = 1 }
                    1 -> StyleGallery(session.ascii.styleId, favorites, preview, { vm.ascii(session.ascii.copy(styleId = it)) }, vm::favorite)
                    2 -> ExportsScreen(state, records, vm::cancelExport, { external(it, false) }, { external(it, true) }, vm::forget) { page = 0 }
                }
            }
            if (loading) Surface(color = PanelRaised, shape = MaterialTheme.shapes.large, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp), shadowElevation = 8.dp) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Reading your video…")
                }
            }
        }
    }
    if (sheet && session.video != null) ExportSheet(session, vm::exportSettings, { sheet = false }, ::beginExport)
    state.error?.let { error -> AlertDialog(onDismissRequest = vm::dismissExport,
        icon = { Icon(Icons.Outlined.ErrorOutline, null) }, title = { Text("Export needs another try") },
        text = { Text(error) }, confirmButton = { TextButton(onClick = { vm.dismissExport(); sheet = true }) { Text("Export settings") } },
        dismissButton = { TextButton(onClick = vm::dismissExport) { Text("Close") } }) }
    if (about) AlertDialog(onDismissRequest = { about = false }, icon = { Icon(Icons.Outlined.Code, null, tint = Mint) },
        title = { Text("Made of characters.") }, text = {
            Text("Asciify turns your videos into ASCII and dithered art, entirely on your Android device.\n\n${Styles.all.size} styles. GPU-first processing with CPU fallback. H.264 MP4 export, adjustable FPS and bitrate, trim, original audio, custom colors, and no watermark.\n\nYour footage stays yours. No accounts, uploads, analytics, or internet permission.\n\nAndroid 10+ · v1.1.0")
        }, confirmButton = { TextButton(onClick = { about = false }) { Text("Keep creating") } })
}
