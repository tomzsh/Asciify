@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.asciify.studio.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.asciify.studio.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.asciify.studio.engine.*
import com.asciify.studio.model.*
import kotlinx.coroutines.*

@Composable fun VideoPreview(video: VideoInfo?, settings: AsciiSettings, sample: Bitmap, trimStart: Long, trimEnd: Long) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var original by remember { mutableStateOf(false) }
    var position by remember(video?.uri) { mutableLongStateOf(0L) }
    var playing by remember(video?.uri) { mutableStateOf(false) }
    var failed by remember(video?.uri) { mutableStateOf(false) }
    var stillError by remember(video?.uri) { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val player = remember(video?.uri) { if (video != null) ExoPlayer.Builder(context).build() else null }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true; playing = false }
        }
        player?.addListener(listener)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player?.pause() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player?.removeListener(listener); player?.release() }
    }
    LaunchedEffect(player) {
        if (player != null && video != null) {
            player.setVideoEffects(listOf(AsciiEffect(settings)))
            player.setMediaItem(MediaItem.fromUri(video.uri))
            player.prepare()
        }
    }
    LaunchedEffect(settings, original, player) {
        delay(160)
        if (!failed) player?.let {
            it.setVideoEffects(if (original) emptyList() else listOf(AsciiEffect(settings)))
            // An identical-position seek can be optimized away by ExoPlayer. Move 1 ms
            // while paused to make the current frame run through the updated effect.
            if (!it.isPlaying) {
                val position = it.currentPosition
                val refresh = if (it.duration > 1 && position >= it.duration - 1) (position - 1).coerceAtLeast(0) else position + 1
                it.seekTo(refresh)
            }
        }
    }
    LaunchedEffect(player, trimStart, trimEnd) {
        player?.seekTo(trimStart)
        while (isActive) {
            if (player != null && !dragging) {
                position = player.currentPosition.coerceAtLeast(0)
                if (playing && position >= trimEnd && trimEnd > trimStart) player.seekTo(trimStart)
            }
            delay(150)
        }
    }
    val still by produceState<Bitmap?>(null, settings, original, video?.uri, failed, if (failed) position else 0L) {
        if (video == null || failed) {
            delay(120)
            try {
                value = withContext(Dispatchers.Default) {
                var source: Bitmap? = null
                if (video != null) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(video.uri))
                        source = retriever.getScaledFrameAtTime(position * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 720, 720)
                    } finally { runCatching { retriever.release() } }
                }
                val frame = if (video != null) requireNotNull(source) { "This frame could not be decoded." } else sample
                try {
                    if (original) frame.copy(Bitmap.Config.ARGB_8888, false)
                    else CpuAsciiRenderer(settings).use { it.render(frame, frame.width, frame.height) }
                } finally { source?.recycle() }
                }
                stillError = null
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                value = null
                stillError = "This frame cannot be previewed. Reopen the video or try a different position."
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Line, RoundedCornerShape(20.dp)).background(Panel)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(Color.Black), contentAlignment = Alignment.Center) {
            if (player != null && !failed) {
                AndroidView(factory = { PlayerView(it).apply {
                    this.player = player; useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
            } else {
                still?.let { Image(it.asImageBitmap(), "${Styles.find(settings.styleId).name} preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                if (stillError != null) Icon(Icons.Outlined.BrokenImage, "Frame unavailable", tint = Muted, modifier = Modifier.size(40.dp))
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Ink.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(5.dp).background(Mint, CircleShape))
                        Eyebrow(if (video == null) "SAMPLE SCENE" else if (failed) "CPU STILL PREVIEW" else "LIVE PREVIEW", Color(0xFFE4ECDD))
                    }
                }
                FilterChip(selected = original, onClick = { original = !original }, label = { Text("Original") },
                    leadingIcon = { Icon(Icons.Outlined.Compare, null, Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Ink.copy(alpha = 0.85f), selectedContainerColor = Mint, selectedLabelColor = Ink, selectedLeadingIconColor = Ink))
            }
            if (video == null) {
                Text("a different kind of landscape.", color = Color(0xFFE1EACF), style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                player?.let { if (it.isPlaying) it.pause() else { if (it.currentPosition >= trimEnd) it.seekTo(trimStart); it.play() } }
            }, enabled = video != null && !failed) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (playing) "Pause preview" else "Play preview", tint = if (video != null && !failed) Mint else Muted)
            }
            if (video != null) {
                Slider(value = position.toFloat().coerceIn(0f, video.durationMs.toFloat()), onValueChange = { dragging = true; position = it.toLong() },
                    onValueChangeFinished = { player?.seekTo(position); dragging = false }, valueRange = 0f..video.durationMs.toFloat(), modifier = Modifier.weight(1f))
                Text(timeLabel(position), style = MaterialTheme.typography.bodySmall, color = Muted, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                Text("Import a video to bring it to life", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.AutoAwesome, null, tint = Mint, modifier = Modifier.padding(12.dp).size(18.dp))
            }
        }
        if (failed) Text(stillError ?: "Live GPU preview is unavailable. Scrub to preview a frame on CPU. Export can use CPU fallback.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    }
}
