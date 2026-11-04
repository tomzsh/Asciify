package com.asciify.studio

import com.asciify.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class ProcessingTest {
    private val portrait = VideoInfo("content://test", "portrait.mp4", 1080, 1920, 10_000, 29.97f, true)

    @Test fun portraitOutputPreservesAspectAndEvenDimensions() {
        val plan = ExportPlan.create(portrait, ExportSettings(height = 720), 0, 10_000)
        assertEquals(720, plan.height)
        assertEquals(404, plan.width)
        assertEquals(0, plan.width % 2)
    }
    @Test fun outputNeverUpscalesSource() {
        val plan = ExportPlan.create(portrait.copy(width = 640, height = 360), ExportSettings(height = 1080), 0, 10_000)
        assertEquals(640, plan.width)
        assertEquals(360, plan.height)
    }
    @Test fun fpsDoesNotInventFramesOnGpu() {
        val plan = ExportPlan.create(portrait, ExportSettings(fps = 60), 0, 10_000)
        assertEquals(29.97f, plan.fps, 0.001f)
    }
    @Test fun timestampsAreMonotonicAndStartAtZero() {
        val plan = ExportPlan.create(portrait, ExportSettings(fps = 24), 1250, 5250)
        assertEquals(96, plan.frameCount)
        assertEquals(0L, plan.frameTimeUs(0))
        assertEquals(3_958_333L, plan.frameTimeUs(95))
    }
    @Test fun sizeEstimateIncludesSelectedAudio() {
        val withAudio = ExportPlan.create(portrait, ExportSettings(bitrateMbps = 8), 0, 10_000)
        val muted = ExportPlan.create(portrait, ExportSettings(bitrateMbps = 8, keepAudio = false), 0, 10_000)
        assertEquals(10_160_000L, withAudio.estimatedBytes)
        assertEquals(10_000_000L, muted.estimatedBytes)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyTrimIsRejected() {
        ExportPlan.create(portrait, ExportSettings(), 5000, 5000)
    }
    @Test(expected = IllegalArgumentException::class) fun trimOutsideSourceIsRejected() {
        ExportPlan.create(portrait, ExportSettings(), 0, 11_000)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidBitrateIsRejected() {
        ExportPlan.create(portrait, ExportSettings(bitrateMbps = 0), 0, 10_000)
    }
    @Test fun glyphMappingClampsAndInverts() {
        assertEquals(0, AsciiMath.glyphIndex(0f, 10, false))
        assertEquals(9, AsciiMath.glyphIndex(1f, 10, false))
        assertEquals(9, AsciiMath.glyphIndex(0f, 10, true))
        assertEquals(9, AsciiMath.glyphIndex(2f, 10, false))
        assertEquals(0, AsciiMath.glyphIndex(-2f, 10, false))
    }
    @Test fun luminanceUsesPerceptualWeights() {
        assertEquals(0.2126f, AsciiMath.luminance(255, 0, 0), 0.0001f)
        assertEquals(0.7152f, AsciiMath.luminance(0, 255, 0), 0.0001f)
        assertEquals(1f, AsciiMath.luminance(255, 255, 255), 0.0001f)
    }
    @Test fun contrastNeutralPreservesLuminance() {
        assertEquals(0.3f, AsciiMath.adjust(0.3f, 1f, 0f), 0.0001f)
        assertEquals(1f, AsciiMath.adjust(0.9f, 2f, 0.2f), 0.0001f)
    }
    @Test fun unknownSourceFrameRateUsesSelectedRate() {
        val plan = ExportPlan.create(portrait.copy(frameRate = Float.NaN), ExportSettings(fps = 24), 0, 10_000)
        assertEquals(24f, plan.fps, 0f)
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedFrameRateIsRejected() {
        ExportPlan.create(portrait, ExportSettings(fps = 0), 0, 10_000)
    }
    @Test(expected = IllegalArgumentException::class) fun negativeTrimIsRejected() {
        ExportPlan.create(portrait, ExportSettings(), -100, 1000)
    }
    @Test fun allStylesHaveUniqueIdsAndDrawableGlyphs() {
        assertEquals(28, Styles.all.size)
        assertEquals(18, Styles.ascii.size)
        assertEquals(10, Styles.dithered.size)
        assertTrue(Styles.dithered.all { it.renderMode == RenderMode.DITHER })
        assertEquals(Styles.all.size, Styles.all.map { it.id }.toSet().size)
        assertTrue(Styles.all.all { it.glyphs.length >= 3 && it.glyphs.first() == ' ' })
    }
    @Test fun choosingAnInkColorOverridesGradientAndSourceStyles() {
        val pink = 0xFFFF4F8B.toInt()
        for (style in listOf("neon", "color", "classic")) {
            val palette = AsciiSettings(styleId = style, foregroundColor = pink).palette()
            assertEquals(pink, palette.foreground)
            assertEquals(ColorMode.MONO, palette.mode)
        }
    }
    @Test fun originalVideoColorTakesPriorityOverSelectedInk() {
        val palette = AsciiSettings(originalColor = true, foregroundColor = 0xFFFF4F8B.toInt()).palette()
        assertEquals(ColorMode.ORIGINAL, palette.mode)
    }
    @Test fun backgroundSelectionDoesNotChangeCharacterColorMode() {
        val background = 0xFF183050.toInt()
        val palette = AsciiSettings(styleId = "neon", backgroundColor = background).palette()
        assertEquals(background, palette.background)
        assertEquals(ColorMode.GRADIENT, palette.mode)
    }
    @Test fun styleDefaultsRestoreBothPaletteAndMode() {
        val style = Styles.find("matrix")
        assertEquals(RenderPalette(style.foreground, style.background, style.colorMode), AsciiSettings(styleId = "matrix").palette())
    }
}
