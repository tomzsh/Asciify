package com.asciify.studio.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.SystemClock
import com.asciify.studio.model.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min

/** CPU glyph rendering and YUV conversion, independent of OpenGL and the GPU effect pipeline. */
object CpuExporter {
    suspend fun export(context: Context, video: VideoInfo, ascii: AsciiSettings, plan: ExportPlan,
        output: File, progress: (Int) -> Unit) = withContext(Dispatchers.Default) {
        val uri = Uri.parse(video.uri)
        val retriever = MediaMetadataRetriever()
        val audio = MediaExtractor()
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var startedCodec = false
        var startedMuxer = false
        var bitmap: Bitmap? = null
        val renderer = CpuAsciiRenderer(ascii)
        try {
            retriever.setDataSource(context, uri)
            var audioIndex = -1
            if (plan.includeAudio) {
                audio.setDataSource(context, uri, null)
                audioIndex = (0 until audio.trackCount).firstOrNull {
                    audio.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("The source audio track could not be read. Turn off Keep original audio and retry.")
            }
            val audioFormat = if (audioIndex >= 0) audio.getTrackFormat(audioIndex) else null
            require(audioFormat == null || audioFormat.getString(MediaFormat.KEY_MIME) in listOf("audio/mp4a-latm", "audio/mpeg")) {
                "CPU mode cannot copy this audio format to MP4. Turn off Keep original audio, or use GPU mode to convert it to AAC."
            }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, plan.width, plan.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, plan.bitrate)
                setFloat(MediaFormat.KEY_FRAME_RATE, plan.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            val encoderName = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
                .sortedBy { if (it.isSoftwareOnly) 0 else 1 }
                .firstOrNull {
                    val caps = it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) &&
                        caps.isFormatSupported(format)
                }?.name ?: error("No CPU-compatible H.264 encoder supports these settings. Try a lower resolution, FPS, or bitrate.")
            val encoder = MediaCodec.createByCodecName(encoderName)
            codec = encoder
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start(); startedCodec = true
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            var videoTrack = -1
            val audioTrack = audioFormat?.let { writer.addTrack(it) } ?: -1
            val bufferInfo = MediaCodec.BufferInfo()
            var outputEos = false
            var lastActivity = SystemClock.elapsedRealtime()
            suspend fun drain(wait: Boolean = false) {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val index = encoder.dequeueOutputBuffer(bufferInfo, if (wait) 10_000 else 0)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!startedMuxer) { "Encoder changed format twice." }
                            videoTrack = writer.addTrack(encoder.outputFormat)
                            writer.start(); startedMuxer = true
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            check(SystemClock.elapsedRealtime() - lastActivity < 30_000) { "The CPU encoder stopped responding. Try a smaller resolution." }
                            if (!wait) return
                        }
                        else -> if (index >= 0) {
                            lastActivity = SystemClock.elapsedRealtime()
                            val buffer = requireNotNull(encoder.getOutputBuffer(index))
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufferInfo.size > 0) {
                                check(startedMuxer)
                                buffer.position(bufferInfo.offset); buffer.limit(bufferInfo.offset + bufferInfo.size)
                                writer.writeSampleData(videoTrack, buffer, bufferInfo)
                            }
                            outputEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            encoder.releaseOutputBuffer(index, false)
                            if (outputEos) return
                        }
                    }
                    if (!wait) continue
                    if (outputEos) return
                }
            }
            suspend fun inputSlot(): Int {
                val deadline = SystemClock.elapsedRealtime() + 30_000
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) return index
                    drain()
                    check(SystemClock.elapsedRealtime() < deadline) { "The CPU encoder did not accept a frame." }
                }
            }
            bitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(plan.width * plan.height)
            for (frame in 0 until plan.frameCount) {
                ensureActive()
                val timestamp = plan.startMs * 1000 + plan.frameTimeUs(frame)
                val source = retriever.getScaledFrameAtTime(timestamp, MediaMetadataRetriever.OPTION_CLOSEST, plan.width, plan.height)
                    ?: error("A video frame could not be decoded at ${timestamp / 1000} ms.")
                try { renderer.render(source, plan.width, plan.height, bitmap) } finally { source.recycle() }
                bitmap.getPixels(pixels, 0, plan.width, 0, 0, plan.width, plan.height)
                val inputIndex = inputSlot()
                val image = encoder.getInputImage(inputIndex)
                    ?: error("This encoder does not provide CPU image buffers. Try another resolution or GPU mode.")
                try { writeYuv(pixels, image, plan.width, plan.height) } finally { image.close() }
                encoder.queueInputBuffer(inputIndex, 0, plan.width * plan.height * 3 / 2, plan.frameTimeUs(frame), 0)
                drain()
                progress(((frame + 1) * 95f / plan.frameCount).toInt())
            }
            val eosIndex = inputSlot()
            encoder.queueInputBuffer(eosIndex, 0, 0, plan.durationMs * 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(wait = true)
            check(outputEos && startedMuxer) { "The CPU encoder produced no video." }
            if (audioIndex >= 0) {
                audio.selectTrack(audioIndex)
                audio.seekTo(plan.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val capacity = maxOf(1_048_576, audioFormat?.let { if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0 } ?: 0)
                require(capacity <= 16_777_216) { "This audio track requires an unusually large buffer. Disable audio and retry." }
                val audioBuffer = ByteBuffer.allocateDirect(capacity)
                while (audio.sampleTime >= 0 && audio.sampleTime < plan.endMs * 1000) {
                    ensureActive()
                    if (audio.sampleTime >= plan.startMs * 1000) {
                        audioBuffer.clear()
                        val size = audio.readSampleData(audioBuffer, 0)
                        if (size < 0) break
                        check(size <= capacity) { "An audio packet is too large. Disable audio and retry." }
                        bufferInfo.set(0, size, audio.sampleTime - plan.startMs * 1000,
                            if (audio.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        audioBuffer.position(0); audioBuffer.limit(size)
                        writer.writeSampleData(audioTrack, audioBuffer, bufferInfo)
                    }
                    if (!audio.advance()) break
                }
            }
            writer.stop(); startedMuxer = false
            progress(100)
        } finally {
            renderer.close(); bitmap?.recycle()
            runCatching { retriever.release() }; runCatching { audio.release() }
            if (startedCodec) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (startedMuxer) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun writeYuv(pixels: IntArray, image: Image, width: Int, height: Int) {
        val y = image.planes[0]; val u = image.planes[1]; val v = image.planes[2]
        fun rgb(pixel: Int, shift: Int) = pixel shr shift and 255
        for (row in 0 until height) for (col in 0 until width) {
            val p = pixels[row * width + col]
            val r = rgb(p, 16); val g = rgb(p, 8); val b = rgb(p, 0)
            y.buffer.put(row * y.rowStride + col * y.pixelStride, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235).toByte())
            if (row % 2 == 0 && col % 2 == 0) {
                var red = 0; var green = 0; var blue = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val pixel = pixels[(row + dy) * width + col + dx]
                    red += rgb(pixel, 16); green += rgb(pixel, 8); blue += rgb(pixel, 0)
                }
                red /= 4; green /= 4; blue /= 4
                val uvRow = row / 2; val uvCol = col / 2
                u.buffer.put(uvRow * u.rowStride + uvCol * u.pixelStride, (((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128).coerceIn(16, 240).toByte())
                v.buffer.put(uvRow * v.rowStride + uvCol * v.pixelStride, (((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128).coerceIn(16, 240).toByte())
            }
        }
    }
}
