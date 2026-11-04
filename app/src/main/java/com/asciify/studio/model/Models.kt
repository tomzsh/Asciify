package com.asciify.studio.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class VideoInfo(
    val uri: String, val name: String, val width: Int, val height: Int,
    val durationMs: Long, val frameRate: Float, val hasAudio: Boolean,
)

enum class ColorMode { MONO, ORIGINAL, GRADIENT }
enum class ProcessingMode { AUTO, GPU, CPU }
enum class RenderMode { ASCII, DITHER }
enum class DitherPattern { BAYER_2, BAYER_4, BAYER_8, BAYER_16, DOTS, DIAGONAL, CROSSHATCH, STIPPLE }

data class AsciiSettings(
    val styleId: String = "classic", val columns: Int = 100,
    val contrast: Float = 1.15f, val brightness: Float = 0f,
    val invert: Boolean = false, val originalColor: Boolean = false,
    val foregroundColor: Int? = null, val backgroundColor: Int? = null,
    val gamma: Float = 1f, val ditherColumns: Int = 160,
    val ditherAmount: Float = 1f, val ditherTones: Int = 0,
)

data class RenderPalette(val foreground: Int, val background: Int, val mode: ColorMode)

fun AsciiSettings.palette(): RenderPalette {
    val style = Styles.find(styleId)
    return RenderPalette(foregroundColor ?: style.foreground, backgroundColor ?: style.background,
        when { originalColor -> ColorMode.ORIGINAL; foregroundColor != null -> ColorMode.MONO; else -> style.colorMode })
}

data class ExportSettings(
    val height: Int = 720, val fps: Int = 30, val bitrateMbps: Int = 8,
    val keepAudio: Boolean = true, val mode: ProcessingMode = ProcessingMode.AUTO,
)

data class ExportPlan(
    val width: Int, val height: Int, val fps: Float, val bitrate: Int,
    val startMs: Long, val endMs: Long, val includeAudio: Boolean,
) {
    val durationMs get() = endMs - startMs
    val frameCount get() = ceil(durationMs * fps / 1000.0).toInt()
    val estimatedBytes get() = durationMs * (bitrate.toLong() + if (includeAudio) 128_000L else 0L) / 8000L
    fun frameTimeUs(index: Int): Long = (index * 1_000_000.0 / fps).toLong()

    companion object {
        fun create(video: VideoInfo, settings: ExportSettings, startMs: Long, endMs: Long): ExportPlan {
            require(video.width > 0 && video.height > 0 && video.durationMs > 0) { "This video has no readable frames." }
            require(startMs >= 0 && endMs <= video.durationMs && endMs - startMs >= 100) { "Select at least 0.1 seconds within the video." }
            require(settings.bitrateMbps in 1..30) { "Bitrate must be between 1 and 30 Mbps." }
            require(settings.fps in 1..60) { "Frame rate must be between 1 and 60 FPS." }
            require(settings.height == 0 || settings.height in 144..2160) { "Choose a supported output resolution." }
            val scale = if (settings.height == 0) 1.0 else min(1.0, settings.height.toDouble() / video.height)
            val width = max(2, floor(video.width * scale / 2).toInt() * 2)
            val height = max(2, floor(video.height * scale / 2).toInt() * 2)
            val sourceFps = video.frameRate.takeIf { it.isFinite() && it > 0 } ?: settings.fps.toFloat()
            return ExportPlan(width, height, min(settings.fps.toFloat(), sourceFps),
                settings.bitrateMbps * 1_000_000, startMs, endMs, settings.keepAudio && video.hasAudio)
        }
    }
}

object AsciiMath {
    fun luminance(r: Int, g: Int, b: Int): Float = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    fun adjust(luminance: Float, contrast: Float, brightness: Float): Float =
        ((luminance - 0.5f) * contrast + 0.5f + brightness).coerceIn(0f, 1f)
    fun glyphIndex(luminance: Float, count: Int, invert: Boolean): Int {
        val luma = if (invert) 1f - luminance else luminance
        return (luma.coerceIn(0f, 1f) * (count - 1)).roundToInt()
    }
    fun rows(width: Int, height: Int, columns: Int) = max(1, (height.toFloat() / width * columns * 0.6f).roundToInt())
}
