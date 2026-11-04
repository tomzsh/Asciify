package com.asciify.studio

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asciify.studio.data.*
import com.asciify.studio.export.ExportService
import com.asciify.studio.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AsciifyApplication
    private val _session = MutableStateFlow(app.store.loadSession())
    val session = _session.asStateFlow()
    private val _favorites = MutableStateFlow(app.store.favorites())
    val favorites = _favorites.asStateFlow()
    private val _thumbnail = MutableStateFlow<Bitmap?>(null)
    val thumbnail = _thumbnail.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val exportState = app.exports.state
    val records = app.exports.records
    private var importJob: Job? = null

    init { _session.value.video?.let { import(Uri.parse(it.uri), restore = true) } }

    fun import(uri: Uri, restore: Boolean = false) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _loading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    val extractor = MediaExtractor()
                    try {
                        retriever.setDataSource(app, uri)
                        fun number(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0
                        var width = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
                        var height = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
                        val rotation = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
                        if (rotation == 90 || rotation == 270) { val swap = width; width = height; height = swap }
                        val duration = number(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        require(width > 0 && height > 0 && duration >= 100) { "Choose a readable video at least 0.1 seconds long." }
                        val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: "Untitled video"
                        extractor.setDataSource(app, uri, null)
                        var fps = 30f
                        var hasAudio = false
                        for (index in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(index)
                            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                            if (mime.startsWith("audio/")) hasAudio = true
                            if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                fps = runCatching { format.getNumber(MediaFormat.KEY_FRAME_RATE)?.toFloat() }.getOrNull() ?: 30f
                            }
                        }
                        val thumbnail = runCatching {
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
                        }.getOrNull()
                        VideoInfo(uri.toString(), name, width, height, duration, fps, hasAudio) to thumbnail
                    } finally { retriever.release(); extractor.release() }
                }
                ensureActive()
                val old = _session.value
                val restoredStart = if (restore) old.startMs.coerceIn(0, result.first.durationMs - 100) else 0
                _session.value = old.copy(video = result.first,
                    startMs = restoredStart,
                    endMs = if (restore) old.endMs.coerceIn(restoredStart + 100, result.first.durationMs) else result.first.durationMs)
                _thumbnail.value = result.second
                persist()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                _message.value = if (restore) "Reopen your video to continue. Android no longer grants access to the previous file."
                    else error.localizedMessage ?: "This video cannot be opened. Try another file."
                if (restore) { _session.value = _session.value.copy(video = null, startMs = 0, endMs = 0); persist() }
            } finally { _loading.value = false }
        }
    }
    fun ascii(update: AsciiSettings) { _session.value = _session.value.copy(ascii = update); persist() }
    fun exportSettings(update: ExportSettings) { _session.value = _session.value.copy(export = update); persist() }
    fun trim(startMs: Long, endMs: Long) {
        val video = _session.value.video ?: return
        val start = startMs.coerceIn(0, video.durationMs - 100)
        val end = endMs.coerceIn(start + 100, video.durationMs)
        _session.value = _session.value.copy(startMs = start, endMs = end); persist()
    }
    fun favorite(id: String) {
        _favorites.value = if (id in _favorites.value) _favorites.value - id else _favorites.value + id
        app.store.favorites(_favorites.value)
    }
    fun startExport() {
        if (exportState.value.running) return
        val s = _session.value
        val video = s.video ?: return
        try {
            ExportPlan.create(video, s.export, s.startMs, s.endMs)
            app.exports.update(ExportState(running = true, stage = "Starting export"))
            val request = ExportRequest(video, s.ascii, s.export, s.startMs, s.endMs)
            ContextCompat.startForegroundService(app, Intent(app, ExportService::class.java).putExtra(ExportService.REQUEST, request.json()))
        } catch (error: Exception) { app.exports.update(ExportState(error = error.localizedMessage ?: "Could not start export.")) }
    }
    fun cancelExport() { app.startService(Intent(app, ExportService::class.java).setAction(ExportService.CANCEL)) }
    fun dismissExport() = app.exports.dismiss()
    fun dismissMessage() { _message.value = null }
    fun message(text: String) { _message.value = text }
    fun forget(uri: String) = app.exports.forget(uri)
    private fun persist() = app.store.saveSession(_session.value)
}
