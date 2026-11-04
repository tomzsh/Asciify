package com.asciify.studio.model

import kotlin.math.*

data class ThresholdMap(val size: Int, val ranks: IntArray) {
    fun threshold(x: Int, y: Int): Float = (ranks[Math.floorMod(y, size) * size + Math.floorMod(x, size)] + 0.5f) / ranks.size
}

object DitherMath {
    private val maps by lazy { DitherPattern.entries.associateWith(::createMap) }
    fun map(pattern: DitherPattern): ThresholdMap = maps.getValue(pattern)
    fun quantize(value: Float, threshold: Float, tones: Int, amount: Float): Float {
        val steps = tones.coerceIn(2, 8) - 1
        return (floor(value.coerceIn(0f, 1f) * steps + 0.5f + (0.5f - threshold) * amount.coerceIn(0f, 1f)) / steps).coerceIn(0f, 1f)
    }
    fun toLinear(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }
    fun toSrgb(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }
    fun tone(value: Float, contrast: Float, brightness: Float, gamma: Float): Float =
        AsciiMath.adjust(value, contrast, brightness).pow(1f / gamma.coerceIn(0.5f, 2f))
    fun columns(width: Int, settings: AsciiSettings) = min(width, settings.ditherColumns.coerceIn(48, 480)).coerceAtLeast(1)
    fun rows(width: Int, height: Int, columns: Int) = max(1, min(height, (height.toDouble() / width * columns).roundToInt()))
    fun tones(settings: AsciiSettings, style: AsciiStyle) = if (settings.ditherTones == 0) style.toneLevels else settings.ditherTones.coerceIn(2, 8)
    fun linearRgb(color: Int) = floatArrayOf(toLinear((color shr 16 and 255) / 255f), toLinear((color shr 8 and 255) / 255f), toLinear((color and 255) / 255f))
    fun inkIsDark(palette: RenderPalette): Boolean {
        fun luminance(c: Int): Float { val v = linearRgb(c); return v[0] * 0.2126f + v[1] * 0.7152f + v[2] * 0.0722f }
        return luminance(palette.foreground) < luminance(palette.background)
    }

    private fun createMap(pattern: DitherPattern): ThresholdMap = when (pattern) {
        DitherPattern.BAYER_2 -> bayer(2)
        DitherPattern.BAYER_4 -> bayer(4)
        DitherPattern.BAYER_8 -> bayer(8)
        DitherPattern.BAYER_16 -> bayer(16)
        DitherPattern.DOTS -> ranked(8) { x, y -> (x - 3.5).pow(2) + (y - 3.5).pow(2) }
        DitherPattern.DIAGONAL -> ranked(8) { x, y -> ((x + y) % 8 * 8 + x).toDouble() }
        DitherPattern.CROSSHATCH -> ranked(8) { x, y ->
            fun distance(value: Int): Int { val v = Math.floorMod(value, 8); return min(v, 8 - v) }
            (min(distance(x - y), distance(x + y)) * 64 + x * 8 + y).toDouble()
        }
        DitherPattern.STIPPLE -> {
            val ranks = IntArray(256) { it }
            var state = 0x41534349L
            for (i in ranks.lastIndex downTo 1) {
                state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
                val j = (state % (i + 1)).toInt()
                val swap = ranks[i]; ranks[i] = ranks[j]; ranks[j] = swap
            }
            ThresholdMap(16, ranks)
        }
    }
    private fun bayer(size: Int): ThresholdMap {
        var n = 1
        var ranks = intArrayOf(0)
        val offsets = intArrayOf(0, 2, 3, 1)
        while (n < size) {
            val old = ranks; val oldSize = n; n *= 2
            ranks = IntArray(n * n) { i ->
                val x = i % n; val y = i / n
                old[y % oldSize * oldSize + x % oldSize] * 4 + offsets[y / oldSize * 2 + x / oldSize]
            }
        }
        return ThresholdMap(size, ranks)
    }
    private fun ranked(size: Int, score: (Int, Int) -> Double): ThresholdMap {
        val ordered = (0 until size * size).sortedWith(compareBy<Int> { score(it % size, it / size) }.thenBy { it })
        val ranks = IntArray(size * size)
        ordered.forEachIndexed { rank, index -> ranks[index] = rank }
        return ThresholdMap(size, ranks)
    }
}
