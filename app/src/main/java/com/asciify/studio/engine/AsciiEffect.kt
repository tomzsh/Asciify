@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.asciify.studio.engine

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.asciify.studio.model.*

class AsciiEffect(private val settings: AsciiSettings) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        AsciiShader(context, settings, useHdr)
}

private class AsciiShader(context: Context, private val settings: AsciiSettings, useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, 1) {
    private val style = Styles.find(settings.styleId)
    private val palette = settings.palette()
    private val program: GlProgram
    private val atlasTexture: Int
    private val columns = settings.columns.coerceIn(24, 240)

    init {
        try {
            val vertex = context.assets.open("shaders/ascii.vert").bufferedReader().use { it.readText() }
            val fragment = context.assets.open("shaders/ascii.frag").bufferedReader().use { it.readText() }
            program = GlProgram(vertex, fragment)
            program.setBufferAttribute("aPosition", floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f), 2)
            if (style.renderMode == RenderMode.DITHER) {
                val map = DitherMath.map(style.ditherPattern)
                val bitmap = Bitmap.createBitmap(map.size, map.size, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.setPixels(IntArray(map.ranks.size) { Color.rgb(map.ranks[it], 0, 0) }, 0, map.size, 0, 0, map.size, map.size)
                    atlasTexture = GlUtil.createTexture(bitmap)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTexture)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                } finally { bitmap.recycle() }
                program.setFloatUniform("uMatrixSize", map.size.toFloat())
            } else {
                val atlas = GlyphAtlas(style.glyphs)
                try { atlasTexture = GlUtil.createTexture(atlas.bitmap) } finally { atlas.close() }
                program.setFloatUniform("uMatrixSize", 1f)
            }
            program.setSamplerTexIdUniform("uAtlas", atlasTexture, 1)
            program.setFloatUniform("uGlyphCount", style.glyphs.length.toFloat())
            program.setFloatUniform("uContrast", settings.contrast)
            program.setFloatUniform("uBrightness", settings.brightness)
            program.setFloatUniform("uGamma", settings.gamma.coerceIn(0.5f, 2f))
            program.setFloatUniform("uRenderMode", if (style.renderMode == RenderMode.DITHER) 1f else 0f)
            program.setFloatUniform("uTones", DitherMath.tones(settings, style).toFloat())
            program.setFloatUniform("uDitherAmount", settings.ditherAmount.coerceIn(0f, 1f))
            program.setFloatUniform("uInkIsDark", if (DitherMath.inkIsDark(palette)) 1f else 0f)
            program.setFloatsUniform("uLinearForeground", DitherMath.linearRgb(palette.foreground))
            program.setFloatsUniform("uLinearBackground", DitherMath.linearRgb(palette.background))
            program.setFloatUniform("uInvert", if (settings.invert) 1f else 0f)
            program.setFloatUniform("uColorMode", when {
                palette.mode == ColorMode.ORIGINAL -> 1f
                palette.mode == ColorMode.GRADIENT -> 2f
                else -> 0f
            })
            fun rgb(color: Int) = floatArrayOf(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f)
            program.setFloatsUniform("uForeground", rgb(palette.foreground))
            program.setFloatsUniform("uBackground", rgb(palette.background))
        } catch (error: Exception) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val cols = if (style.renderMode == RenderMode.DITHER) DitherMath.columns(inputWidth, settings) else columns
        val rows = if (style.renderMode == RenderMode.DITHER) DitherMath.rows(inputWidth, inputHeight, cols) else AsciiMath.rows(inputWidth, inputHeight, cols)
        program.setFloatsUniform("uGrid", floatArrayOf(cols.toFloat(), rows.toFloat()))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uSource", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        try {
            program.delete()
            GlUtil.deleteTexture(atlasTexture)
        } catch (error: GlUtil.GlException) { throw VideoFrameProcessingException(error) }
        finally { super.release() }
    }
}
