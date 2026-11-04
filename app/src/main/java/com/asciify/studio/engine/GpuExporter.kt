@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.asciify.studio.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.asciify.studio.model.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GpuExporter {
    suspend fun export(context: Context, video: VideoInfo, ascii: AsciiSettings, plan: ExportPlan,
        output: File, progress: (Int) -> Unit) = withContext(Dispatchers.Main.immediate) {
        val media = MediaItem.Builder().setUri(video.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(plan.startMs).setEndPositionMs(plan.endMs).build()).build()
        val item = EditedMediaItem.Builder(media).setRemoveAudio(!plan.includeAudio).setEffects(
            Effects(emptyList(), listOf(
                FrameDropEffect.createDefaultFrameDropEffect(plan.fps),
                Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT),
                AsciiEffect(ascii),
            ))).build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        val encoder = DefaultEncoderFactory.Builder(context).setEnableFallback(false)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.bitrate).build()).build()
        var transformer: Transformer? = null
        coroutineScope {
            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    transformer?.let { if (it.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress) }
                    delay(250)
                }
            }
            try {
                suspendCancellableCoroutine { continuation ->
                    val instance = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        }).build()
                    transformer = instance
                    continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { instance.cancel() } }
                    instance.start(composition, output.absolutePath)
                }
            } finally { poller.cancel() }
        }
    }
}
