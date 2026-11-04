package com.asciify.studio.engine

import android.graphics.*
import com.asciify.studio.model.*
import kotlin.math.*

/** A small, shared font atlas keeps the GPU and CPU character shapes identical. */
class GlyphAtlas(val glyphs: String) {
    val cellWidth = 32
    val cellHeight = 48
    val bitmap = Bitmap.createBitmap(cellWidth * glyphs.length, cellHeight, Bitmap.Config.ARGB_8888)
    init {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = Typeface.MONOSPACE; textSize = 39f; textAlign = Paint.Align.CENTER
        }
        val canvas = Canvas(bitmap)
        val baseline = (cellHeight - paint.fontMetrics.bottom - paint.fontMetrics.top) / 2f
        glyphs.forEachIndexed { index, char ->
            canvas.drawText(char.toString(), index * cellWidth + cellWidth / 2f, baseline, paint)
        }
    }
    fun close() = bitmap.recycle()
}

class CpuAsciiRenderer(private val settings: AsciiSettings) : AutoCloseable {
    private val style = Styles.find(settings.styleId)
    private val palette = settings.palette()
    private val atlas = if (style.renderMode == RenderMode.ASCII) GlyphAtlas(style.glyphs) else null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val samplePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixelPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private var grid: Bitmap? = null
    private var cells = IntArray(0)
    private var lastInk: Int? = null
    private val map = DitherMath.map(style.ditherPattern)
    private val tones = DitherMath.tones(settings, style)
    private val inputLinear = FloatArray(256) { DitherMath.toLinear(DitherMath.tone(it / 255f, settings.contrast, settings.brightness, settings.gamma)) }
    private val outputSrgb = FloatArray(tones) { DitherMath.toSrgb(it.toFloat() / (tones - 1)) }
    private val darkInk = DitherMath.inkIsDark(palette)
    private val ink = DitherMath.linearRgb(palette.foreground)
    private val paper = DitherMath.linearRgb(palette.background)
    private val monoColors = IntArray(tones) { i ->
        val t = i.toFloat() / (tones - 1)
        Color.rgb((DitherMath.toSrgb(paper[0] + (ink[0] - paper[0]) * t) * 255).roundToInt(),
            (DitherMath.toSrgb(paper[1] + (ink[1] - paper[1]) * t) * 255).roundToInt(),
            (DitherMath.toSrgb(paper[2] + (ink[2] - paper[2]) * t) * 255).roundToInt())
    }

    private fun sample(source: Bitmap, columns: Int, rows: Int): Bitmap {
        if (grid?.width != columns || grid?.height != rows) {
            grid?.recycle()
            grid = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
            cells = IntArray(columns * rows)
        }
        val bitmap = requireNotNull(grid)
        Canvas(bitmap).drawBitmap(source, null, Rect(0, 0, columns, rows), samplePaint)
        bitmap.getPixels(cells, 0, columns, 0, 0, columns, rows)
        return bitmap
    }

    fun render(source: Bitmap, width: Int, height: Int, output: Bitmap? = null): Bitmap {
        val target = output ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(palette.background)
        if (style.renderMode == RenderMode.DITHER) {
            renderDither(source, canvas, width, height)
            return target
        }
        val atlas = requireNotNull(atlas)
        val columns = settings.columns.coerceIn(24, 240)
        val rows = AsciiMath.rows(width, height, columns)
        sample(source, columns, rows)
        val src = Rect()
        val dest = RectF()
        for (row in 0 until rows) for (column in 0 until columns) {
            val pixel = cells[row * columns + column]
            val luma = DitherMath.tone(AsciiMath.luminance(Color.red(pixel), Color.green(pixel), Color.blue(pixel)), settings.contrast, settings.brightness, settings.gamma)
            val index = AsciiMath.glyphIndex(luma, style.glyphs.length, settings.invert)
            src.set(index * atlas.cellWidth, 0, (index + 1) * atlas.cellWidth, atlas.cellHeight)
            dest.set(column * width.toFloat() / columns, row * height.toFloat() / rows,
                (column + 1) * width.toFloat() / columns, (row + 1) * height.toFloat() / rows)
            val color = when {
                palette.mode == ColorMode.ORIGINAL -> pixel
                palette.mode == ColorMode.GRADIENT -> Color.rgb(
                    (234 + (90 - 234) * luma).roundToInt(), (150 + (235 - 150) * luma).roundToInt(), (250 + (225 - 250) * luma).roundToInt())
                else -> palette.foreground
            }
            if (lastInk != color) { paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN); lastInk = color }
            canvas.drawBitmap(atlas.bitmap, src, dest, paint)
        }
        return target
    }
    private fun renderDither(source: Bitmap, canvas: Canvas, width: Int, height: Int) {
        val columns = DitherMath.columns(width, settings)
        val rows = DitherMath.rows(width, height, columns)
        val bitmap = sample(source, columns, rows)
        fun quantize(value: Float, threshold: Float) = DitherMath.quantize(value, threshold, tones, settings.ditherAmount)
        for (y in 0 until rows) for (x in 0 until columns) {
            val index = y * columns + x
            val color = cells[index]
            var r = inputLinear[Color.red(color)]; var g = inputLinear[Color.green(color)]; var b = inputLinear[Color.blue(color)]
            if (settings.invert) { r = 1f - r; g = 1f - g; b = 1f - b }
            val threshold = map.threshold(x, y)
            cells[index] = if (palette.mode == ColorMode.ORIGINAL) {
                val red = (quantize(r, threshold) * (tones - 1)).roundToInt()
                val green = (quantize(g, threshold) * (tones - 1)).roundToInt()
                val blue = (quantize(b, threshold) * (tones - 1)).roundToInt()
                if (red + green + blue == 0) palette.background
                else Color.rgb((outputSrgb[red] * 255).roundToInt(), (outputSrgb[green] * 255).roundToInt(), (outputSrgb[blue] * 255).roundToInt())
            } else {
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val value = quantize(if (darkInk) 1f - luma else luma, threshold)
                monoColors[(value * (tones - 1)).roundToInt()]
            }
        }
        bitmap.setPixels(cells, 0, columns, 0, 0, columns, rows)
        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), pixelPaint)
    }
    override fun close() { atlas?.close(); grid?.recycle(); grid = null }
}

/** An original procedural mountain scene; no downloaded content or network dependency. */
fun demoFrame(width: Int = 960, height: Int = 600): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), intArrayOf(0xFF526569.toInt(), 0xFFBAC7B4.toInt(), 0xFF142E29.toInt()), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    paint.color = 0xFFE4E6C4.toInt()
    canvas.drawCircle(width * 0.7f, height * 0.30f, height * 0.115f, paint)
    val layers = listOf(0.56f to 0xFF72877B.toInt(), 0.70f to 0xFF3B5C4E.toInt(), 0.88f to 0xFF142F28.toInt())
    layers.forEachIndexed { layer, (base, color) ->
        val path = Path().apply { moveTo(0f, height.toFloat()) }
        for (x in 0..width step 3) {
            val ratio = x.toFloat() / width
            val ridge = sin(ratio * 9f + layer * 2.1f) * 0.13f + sin(ratio * 24f + layer) * 0.035f
            path.lineTo(x.toFloat(), height * (base + ridge))
        }
        path.lineTo(width.toFloat(), height.toFloat()); path.close()
        paint.color = color; canvas.drawPath(path, paint)
    }
    // Trees create small, legible details that demonstrate character density.
    paint.color = 0xFF081C16.toInt()
    for (i in 0..40) {
        val x = i * width / 40f
        val h = height * (0.10f + abs(sin(i * 2.3f)) * 0.20f)
        val y = height * (0.91f + sin(i * 0.8f) * 0.05f)
        val path = Path().apply { moveTo(x, y - h); lineTo(x - h * 0.24f, y); lineTo(x + h * 0.24f, y); close() }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
