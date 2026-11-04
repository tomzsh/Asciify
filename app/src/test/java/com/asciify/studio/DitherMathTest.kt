package com.asciify.studio

import com.asciify.studio.model.*
import org.junit.Assert.*
import org.junit.Test

class DitherMathTest {
    @Test fun bayerTwoUsesTheStandardOrientation() {
        assertArrayEquals(intArrayOf(0, 2, 3, 1), DitherMath.map(DitherPattern.BAYER_2).ranks)
    }
    @Test fun everyPatternHasBalancedUniqueRanks() {
        DitherPattern.entries.forEach {
            val map = DitherMath.map(it)
            assertEquals(map.size * map.size, map.ranks.size)
            assertEquals((map.ranks.indices).toSet(), map.ranks.toSet())
            assertTrue(map.ranks.indices.all { i -> map.threshold(i % map.size, i / map.size) in 0f..1f })
        }
    }
    @Test fun bayerFourHasTheExpectedRecursiveRanks() {
        assertArrayEquals(intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5), DitherMath.map(DitherPattern.BAYER_4).ranks)
    }
    @Test fun patternCoordinatesTileAcrossPositiveAndNegativePositions() {
        val map = DitherMath.map(DitherPattern.BAYER_8)
        assertEquals(map.threshold(1, 2), map.threshold(1 + map.size * 3, 2 - map.size * 2), 0f)
    }
    @Test fun stippleIsReproducibleAndDifferentFromBayer() {
        assertArrayEquals(DitherMath.map(DitherPattern.STIPPLE).ranks, DitherMath.map(DitherPattern.STIPPLE).ranks)
        assertFalse(DitherMath.map(DitherPattern.STIPPLE).ranks.contentEquals(DitherMath.map(DitherPattern.BAYER_16).ranks))
    }
    @Test fun exactBlackAndWhiteSurviveAllThresholdsAndToneCounts() {
        for (tones in listOf(2, 4, 8)) for (threshold in listOf(0.001f, 0.25f, 0.5f, 0.999f)) {
            assertEquals(0f, DitherMath.quantize(0f, threshold, tones, 1f), 0f)
            assertEquals(1f, DitherMath.quantize(1f, threshold, tones, 1f), 0f)
        }
    }
    @Test fun halfGrayCoversExactlyHalfOfABayerTile() {
        val map = DitherMath.map(DitherPattern.BAYER_8)
        val lit = map.ranks.indices.count { DitherMath.quantize(0.5f, map.threshold(it % map.size, it / map.size), 2, 1f) == 1f }
        assertEquals(map.ranks.size / 2, lit)
    }
    @Test fun zeroAmountUsesNearestToneWithoutAnyPattern() {
        assertEquals(0f, DitherMath.quantize(0.4f, 0.01f, 2, 0f), 0f)
        assertEquals(0f, DitherMath.quantize(0.4f, 0.99f, 2, 0f), 0f)
        assertEquals(1f, DitherMath.quantize(0.6f, 0.99f, 2, 0f), 0f)
    }
    @Test fun multiToneQuantizationOnlyReturnsTheSelectedLevels() {
        for (tones in listOf(2, 4, 8)) for (i in 0..100) {
            val value = DitherMath.quantize(i / 100f, 0.4f, tones, 1f) * (tones - 1)
            assertEquals(value.toInt().toFloat(), value, 0.00001f)
        }
    }
    @Test fun linearLightRoundTripsAndPreservesMidtone() {
        assertEquals(0.214041f, DitherMath.toLinear(0.5f), 0.00001f)
        for (i in 0..100) assertEquals(i / 100f, DitherMath.toSrgb(DitherMath.toLinear(i / 100f)), 0.00001f)
    }
    @Test fun gammaIsNeutralAtOneAndCanLiftShadows() {
        assertEquals(0.25f, DitherMath.tone(0.25f, 1f, 0f, 1f), 0.00001f)
        assertEquals(0.5f, DitherMath.tone(0.25f, 1f, 0f, 2f), 0.00001f)
        assertEquals(1f, DitherMath.tone(1f, 2f, 0.4f, 1f), 0f)
    }
    @Test fun logicalPixelGridKeepsItsShapeAcrossOutputResolutions() {
        assertEquals(60, DitherMath.rows(320, 240, 80))
        assertEquals(60, DitherMath.rows(1280, 960, 80))
    }
    @Test fun detailNeverCreatesMoreCellsThanAvailableOutputPixels() {
        val settings = AsciiSettings(ditherColumns = 480)
        assertEquals(240, DitherMath.columns(240, settings))
        assertEquals(320, DitherMath.rows(240, 320, 240))
    }
}
