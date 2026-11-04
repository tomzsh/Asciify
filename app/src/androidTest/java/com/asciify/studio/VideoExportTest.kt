@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.asciify.studio

import android.content.Intent
import android.media.*
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asciify.studio.data.*
import com.asciify.studio.engine.*
import com.asciify.studio.export.ExportService
import com.asciify.studio.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoExportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pink = 0xFFFF4F8B.toInt()
    private val navy = 0xFF080E1B.toInt()
    private fun fixture(portrait: Boolean = false): VideoInfo {
        val name = if (portrait) "portrait.mp4" else "source.mp4"
        val file = File(context.cacheDir, "test-$name")
        instrumentation.context.assets.open("fixtures/$name").use { source -> file.outputStream().use { source.copyTo(it) } }
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.absolutePath)
            val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            assertEquals("The portrait fixture must contain 90°/270° rotation metadata", if (portrait) 90 else 0, rotation % 180)
        } finally { metadata.release() }
        return VideoInfo(Uri.fromFile(file).toString(), file.name, if (portrait) 240 else 320, if (portrait) 320 else 240, 2000, 30f, true)
    }
    private fun verify(file: File, expectedFrames: Int, expectedAudio: Boolean, width: Int = 320, height: Int = 240) {
        assertTrue("MP4 must contain encoded samples", file.length() > 1000)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            val videoIndex = tracks.indexOfFirst { it.getString(MediaFormat.KEY_MIME) == "video/avc" }
            assertTrue("H.264 video track", videoIndex >= 0)
            val format = tracks[videoIndex]
            val rotation = format.getInteger(MediaFormat.KEY_ROTATION, 0)
            val codedWidth = format.getInteger(MediaFormat.KEY_WIDTH)
            val codedHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
            assertEquals(width, if (rotation % 180 == 0) codedWidth else codedHeight)
            assertEquals(height, if (rotation % 180 == 0) codedHeight else codedWidth)
            assertEquals(expectedAudio, tracks.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true })
            extractor.selectTrack(videoIndex)
            var frames = 0
            var last = -1L
            do {
                val time = extractor.sampleTime
                if (time < 0) break
                assertTrue("Frame times must increase", time > last)
                assertTrue("Trim timestamps start near zero", frames > 0 || time < 100_000)
                last = time; frames++
            } while (extractor.advance())
            assertTrue("Frame dropping must honor requested rate: $frames", frames in (expectedFrames - 1)..(expectedFrames + 1))
            assertTrue("Trim duration must stay near one second: $last", last in 800_000..1_100_000)
        } finally { extractor.release() }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(400_000, MediaMetadataRetriever.OPTION_CLOSEST)
            assertNotNull("Output must decode", frame)
            assertEquals(width, frame!!.width)
            assertEquals(height, frame.height)
            val pixels = IntArray(frame.width * frame.height)
            frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
            assertTrue("ASCII output must contain visible glyphs", pixels.count { (it and 0xFFFFFF) > 0x202020 } > pixels.size / 100)
            if (width < height) {
                val bandSize = width * (height / 10)
                fun bright(pixel: Int) = ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)) > 150
                assertTrue("Portrait content must reach the top without added letterboxing", pixels.take(bandSize).count(::bright) > bandSize / 100)
                assertTrue("Portrait content must reach the bottom without added letterboxing", pixels.takeLast(bandSize).count(::bright) > bandSize / 100)
            }
            frame.recycle()
        } finally { retriever.release() }
    }
    private fun verifySelectedColors(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = requireNotNull(retriever.getFrameAtTime(400_000, MediaMetadataRetriever.OPTION_CLOSEST))
            val pixels = IntArray(frame.width * frame.height)
            frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
            fun r(p: Int) = p shr 16 and 255
            fun g(p: Int) = p shr 8 and 255
            fun b(p: Int) = p and 255
            val ink = pixels.filter { r(it) + g(it) + b(it) > 180 }
            assertTrue("Selected pink character color must be visible", ink.size > pixels.size / 100)
            // H.264 4:2:0 mixes chroma at tiny glyph edges. Check aggregate hue rather
            // than demanding that every antialiased character pixel keeps its RGB value.
            val meanRed = ink.map(::r).average()
            val meanGreen = ink.map(::g).average()
            val meanBlue = ink.map(::b).average()
            assertTrue("Character color must override the style's default/source palette", meanRed > meanGreen + 40 && meanRed > meanBlue + 20)
            val background = pixels.filter { r(it) + g(it) + b(it) < 100 }
            assertTrue("Selected navy background must be retained", background.isNotEmpty() && background.sumOf { b(it) - r(it) }.toDouble() / background.size > 8)
            frame.recycle()
        } finally { retriever.release() }
    }
    @Test fun cpuExportsTrimmedAsciiWithOriginalAudio() = runBlocking {
        val video = fixture()
        val plan = ExportPlan.create(video, ExportSettings(height = 240, fps = 12, bitrateMbps = 1), 250, 1250)
        val output = File(context.cacheDir, "test-cpu.mp4")
        withTimeout(90_000) { CpuExporter.export(context, video, AsciiSettings(styleId = "neon", columns = 48, foregroundColor = pink, backgroundColor = navy), plan, output) {} }
        verify(output, 12, true)
        verifySelectedColors(output)
    }
    @Test fun gpuExportsTrimmedAsciiAtSelectedFrameRate() = runBlocking {
        val video = fixture()
        val plan = ExportPlan.create(video, ExportSettings(height = 240, fps = 15, bitrateMbps = 1, keepAudio = false), 250, 1250)
        val output = File(context.cacheDir, "test-gpu.mp4").apply { delete() }
        withTimeout(90_000) { GpuExporter.export(context, video, AsciiSettings(styleId = "color", columns = 48, foregroundColor = pink, backgroundColor = navy), plan, output) {} }
        verify(output, 15, false)
        verifySelectedColors(output)
    }
    @Test fun cpuCancellationStopsBeforeCompletion() = runBlocking {
        val video = fixture()
        val plan = ExportPlan.create(video, ExportSettings(height = 240, fps = 30, bitrateMbps = 1, keepAudio = false), 0, 2000)
        var lastProgress = 0
        val job = launch {
            CpuExporter.export(context, video, AsciiSettings(), plan, File(context.cacheDir, "test-canceled.mp4")) {
                lastProgress = it
                if (it >= 10) throw CancellationException("Test cancellation")
            }
        }
        withTimeout(60_000) { job.join() }
        assertTrue(job.isCancelled)
        assertTrue(lastProgress in 10..20)
    }
    @Test fun cpuKeepsPortraitDisplayOrientation() = runBlocking {
        val video = fixture(portrait = true)
        val plan = ExportPlan.create(video, ExportSettings(height = 320, fps = 12, bitrateMbps = 1, keepAudio = false), 250, 1250)
        val output = File(context.cacheDir, "test-cpu-portrait.mp4")
        withTimeout(90_000) { CpuExporter.export(context, video, AsciiSettings(columns = 48), plan, output) {} }
        verify(output, 12, false, 240, 320)
    }
    @Test fun gpuKeepsPortraitDisplayOrientation() = runBlocking {
        val video = fixture(portrait = true)
        val plan = ExportPlan.create(video, ExportSettings(height = 320, fps = 15, bitrateMbps = 1, keepAudio = false), 250, 1250)
        val output = File(context.cacheDir, "test-gpu-portrait.mp4").apply { delete() }
        withTimeout(90_000) { GpuExporter.export(context, video, AsciiSettings(columns = 48), plan, output) {} }
        verify(output, 15, false, 240, 320)
    }
    @Test fun exportServicePublishesPlayableMp4AndHistory() = runBlocking {
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val application = context.applicationContext as AsciifyApplication
        val request = ExportRequest(fixture(), AsciiSettings(styleId = "amber", columns = 48, foregroundColor = pink, backgroundColor = navy),
            ExportSettings(height = 240, fps = 12, bitrateMbps = 1, mode = ProcessingMode.CPU), 250, 1250)
        withContext(Dispatchers.Main) {
            ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java).putExtra(ExportService.REQUEST, request.json()))
        }
        val record = withTimeout(90_000) {
            while (true) {
                application.exports.state.value.error?.let { error(it) }
                val result = application.exports.state.value.completed
                if (result != null) return@withTimeout result
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") error("No result")
        }
        val copy = File(context.cacheDir, "test-published.mp4")
        context.contentResolver.openInputStream(Uri.parse(record.uri))!!.use { source -> copy.outputStream().use { source.copyTo(it) } }
        verify(copy, 12, true)
        verifySelectedColors(copy)
        assertTrue(application.exports.records.value.any { it.uri == record.uri })
        context.contentResolver.delete(Uri.parse(record.uri), null, null)
        withContext(Dispatchers.Main) { application.exports.forget(record.uri); application.exports.dismiss() }
    }
}
