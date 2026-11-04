@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.asciify.studio

import android.graphics.*
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asciify.studio.data.*
import com.asciify.studio.engine.*
import com.asciify.studio.model.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DitherRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture(): VideoInfo {
        val file = File(context.cacheDir, "dither-calibration.mp4")
        instrumentation.context.assets.open("fixtures/dither-calibration.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        return VideoInfo(Uri.fromFile(file).toString(), file.name, 320, 240, 1500, 24f, false)
    }
    private fun frame(file: File, timeUs: Long = 400_000): Bitmap {
        val reader = MediaMetadataRetriever()
        try { reader.setDataSource(file.absolutePath); return requireNotNull(reader.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)) }
        finally { reader.release() }
    }
    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
    private fun averageError(a: Bitmap, b: Bitmap): Double {
        assertEquals(a.width, b.width); assertEquals(a.height, b.height)
        val pa = pixels(a); val pb = pixels(b)
        var error = 0L; var count = 0
        // Sample logical pixel-block centers, including noninteger portrait cell sizes.
        val columns = 80
        val rows = DitherMath.rows(a.width, a.height, columns)
        for (row in 0 until rows) for (column in 0 until columns) {
            val x = ((column + 0.5) * a.width / columns).toInt().coerceAtMost(a.width - 1)
            val y = ((row + 0.5) * a.height / rows).toInt().coerceAtMost(a.height - 1)
            val i = y * a.width + x
            error += abs(Color.red(pa[i]) - Color.red(pb[i])) + abs(Color.green(pa[i]) - Color.green(pb[i])) + abs(Color.blue(pa[i]) - Color.blue(pb[i]))
            count += 3
        }
        return error.toDouble() / count
    }

    @Test fun portraitDitherKeepsOrientationAndPatternCoordinates() = runBlocking {
        val file = File(context.cacheDir, "dither-portrait.mp4")
        instrumentation.context.assets.open("fixtures/dither-portrait.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val video = VideoInfo(Uri.fromFile(file).toString(), file.name, 240, 320, 1500, 24f, false)
        val settings = AsciiSettings(styleId = "dither_bayer4", contrast = 1f, ditherColumns = 80)
        val plan = ExportPlan.create(video, ExportSettings(height = 320, fps = 12, bitrateMbps = 3, keepAudio = false), 0, 1000)
        val source = frame(file)
        val reference = CpuAsciiRenderer(settings).use { it.render(source, 240, 320) }
        try {
            for (gpu in listOf(true, false)) {
                val output = File(context.cacheDir, "dither-portrait-${if (gpu) "gpu" else "cpu"}.mp4").apply { delete() }
                withTimeout(90_000) {
                    if (gpu) GpuExporter.export(context, video, settings, plan, output) {}
                    else CpuExporter.export(context, video, settings, plan, output) {}
                }
                val result = frame(output)
                try {
                    assertEquals(240, result.width); assertEquals(320, result.height)
                    val error = averageError(reference, result)
                    assertTrue("Portrait dither must preserve orientation and pattern (gpu=$gpu, MAE=$error)", error < 18)
                } finally { result.recycle() }
            }
        } finally { source.recycle(); reference.recycle() }
    }
    private fun verifyVideo(file: File) {
        val reader = MediaExtractor()
        try {
            reader.setDataSource(file.absolutePath)
            val track = (0 until reader.trackCount).first { reader.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "video/avc" }
            reader.selectTrack(track)
            var count = 0; var previous = -1L
            while (reader.sampleTime >= 0) {
                assertTrue(reader.sampleTime > previous)
                previous = reader.sampleTime; count++
                if (!reader.advance()) break
            }
            assertEquals(12, count)
            assertTrue(previous in 850_000..1_000_000)
        } finally { reader.release() }
    }

    @Test fun allDitherPresetsExportOnGpuAndCpuWithMatchingFixedPatterns() = runBlocking {
        val video = fixture()
        val source = frame(File(context.cacheDir, "dither-calibration.mp4"))
        val plan = ExportPlan.create(video, ExportSettings(height = 240, fps = 12, bitrateMbps = 3, keepAudio = false), 0, 1000)
        try {
            for (style in Styles.dithered) {
                var settings = AsciiSettings(styleId = style.id, contrast = 1f, ditherColumns = 80)
                if (style.id == "dither_bayer4") settings = settings.copy(foregroundColor = 0xFFFF4F8B.toInt(), backgroundColor = 0xFF080E1B.toInt())
                if (style.id == "dither_bayer16") settings = settings.copy(gamma = 1.2f, ditherAmount = 0.7f, ditherTones = 8)
                if (style.id in listOf("dither_stipple", "dither_color")) settings = settings.copy(invert = true)
                val reference = CpuAsciiRenderer(settings).use { it.render(source, 320, 240) }
                val gpuFile = File(context.cacheDir, "${style.id}-gpu.mp4").apply { delete() }
                val cpuFile = File(context.cacheDir, "${style.id}-cpu.mp4").apply { delete() }
                withTimeout(90_000) { GpuExporter.export(context, video, settings, plan, gpuFile) {} }
                withTimeout(90_000) { CpuExporter.export(context, video, settings, plan, cpuFile) {} }
                verifyVideo(gpuFile); verifyVideo(cpuFile)
                val gpu = frame(gpuFile); val cpu = frame(cpuFile); val last = frame(gpuFile, 800_000)
                try {
                    val gpuError = averageError(reference, gpu)
                    val cpuError = averageError(reference, cpu)
                    val temporalError = averageError(gpu, last)
                    assertTrue("${style.name}: GPU pattern/palette differs from reference ($gpuError)", gpuError < 18)
                    assertTrue("${style.name}: CPU export differs from reference ($cpuError)", cpuError < 18)
                    assertTrue("${style.name}: static footage must not produce animated grain ($temporalError)", temporalError < 6)
                    android.util.Log.i("DitherRenderTest", "${style.id}: GPU MAE=$gpuError CPU MAE=$cpuError temporal MAE=$temporalError")
                } finally { reference.recycle(); gpu.recycle(); cpu.recycle(); last.recycle() }
            }
        } finally { source.recycle() }
    }

    @Test fun monochromeRasterUsesExactlyTwoSelectedInksAndIsDeterministic() {
        val source = demoFrame(320, 240)
        val settings = AsciiSettings(styleId = "dither_stipple", foregroundColor = 0xFFFF4F8B.toInt(), backgroundColor = 0xFF080E1B.toInt(), ditherColumns = 80)
        try {
            CpuAsciiRenderer(settings).use { renderer ->
                val first = renderer.render(source, 320, 240)
                val second = renderer.render(source, 320, 240)
                try {
                    val colors = pixels(first)
                    assertEquals(setOf(settings.foregroundColor, settings.backgroundColor), colors.toSet())
                    assertArrayEquals(colors, pixels(second))
                } finally { first.recycle(); second.recycle() }
            }
        } finally { source.recycle() }
    }

    @Test fun darkInkOnPaperPreservesBlackAndWhiteEndpoints() {
        for (style in Styles.dithered.filter { it.colorMode == ColorMode.MONO }) {
            val settings = AsciiSettings(styleId = style.id)
            val palette = settings.palette()
            val darkInk = DitherMath.inkIsDark(palette)
            val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            try {
                CpuAsciiRenderer(settings).use { renderer ->
                    source.eraseColor(Color.BLACK)
                    val black = renderer.render(source, 64, 64)
                    source.eraseColor(Color.WHITE)
                    val white = renderer.render(source, 64, 64)
                    try {
                        assertEquals(setOf(if (darkInk) palette.foreground else palette.background), pixels(black).toSet())
                        assertEquals(setOf(if (darkInk) palette.background else palette.foreground), pixels(white).toSet())
                    } finally { black.recycle(); white.recycle() }
                }
            } finally { source.recycle() }
        }
    }

    @Test fun renderSettingsRoundTripAndOlderSessionsUseSafeDefaults() {
        val settings = AsciiSettings(styleId = "dither_bayer8", gamma = 1.35f, ditherColumns = 96, ditherAmount = 0.42f, ditherTones = 8)
        val request = ExportRequest(fixture(), settings, ExportSettings(), 0, 1000)
        assertEquals(request, ExportRequest.parse(request.json()))
        val legacy = JSONObject(request.json())
        val ascii = legacy.getJSONObject("ascii")
        listOf("gamma", "ditherColumns", "ditherAmount", "ditherTones").forEach { ascii.remove(it) }
        val restored = ExportRequest.parse(legacy.toString()).ascii
        assertEquals(1f, restored.gamma, 0f); assertEquals(160, restored.ditherColumns)
        assertEquals(1f, restored.ditherAmount, 0f); assertEquals(0, restored.ditherTones)
    }

    @Test fun renderDitherContactSheet() {
        val source = demoFrame(640, 400)
        val sheet = Bitmap.createBitmap(720, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF0C0F10.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        try {
            Styles.dithered.forEachIndexed { index, style ->
                val settings = AsciiSettings(styleId = style.id, ditherColumns = 160)
                val image = CpuAsciiRenderer(settings).use { it.render(source, 344, 220) }
                try {
                    val left = 8f + index % 2 * 360f; val top = 8f + index / 2 * 280f
                    canvas.drawBitmap(image, left, top, null)
                    canvas.drawText(style.name, left + 4, top + 250, text)
                } finally { image.recycle() }
            }
            File(context.cacheDir, "dither-styles.png").outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { source.recycle(); sheet.recycle() }
    }
}
