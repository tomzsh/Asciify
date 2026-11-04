package com.asciify.studio.model

data class AsciiStyle(
    val id: String, val name: String, val category: String, val glyphs: String,
    val foreground: Int, val background: Int, val description: String,
    val colorMode: ColorMode = ColorMode.MONO,
    val renderMode: RenderMode = RenderMode.ASCII,
    val ditherPattern: DitherPattern = DitherPattern.BAYER_4,
    val toneLevels: Int = 2,
)

object Styles {
    private fun color(hex: Long) = (hex or 0xFF000000).toInt()
    val ascii = listOf(
        AsciiStyle("classic", "Classic", "Essential", " .,:;i1tfLCG08@", color(0xDAE5D7), color(0x0B100D), "The original. Light, texture, and a little nostalgia."),
        AsciiStyle("matrix", "Matrix", "Terminal", " .:01|IYXMW#@", color(0x81F886), color(0x020A05), "A green-screen world, one character at a time."),
        AsciiStyle("color", "True color", "Essential", " .:-=+*#%@", color(0xFFFFFF), color(0x07090B), "Keep the colors. Change everything else.", ColorMode.ORIGINAL),
        AsciiStyle("paper", "Newsprint", "Essential", " .,:;ox%#@", color(0x252720), color(0xE8E6D8), "Black ink on warm paper. Made for portraits."),
        AsciiStyle("amber", "Amber CRT", "Terminal", " .,:;+=xX$#@", color(0xFFBE68), color(0x100B04), "That warm, late-night terminal glow."),
        AsciiStyle("blueprint", "Blueprint", "Creative", " .:+oxO#@", color(0xA4D9FF), color(0x0B213B), "Turn motion into a technical daydream."),
        AsciiStyle("binary", "Binary", "Terminal", " 01", color(0xB9F577), color(0x080E08), "Every frame, reduced to zeroes and ones."),
        AsciiStyle("neon", "Neon wave", "Creative", " .:;=+*#%@", color(0xEA96FA), color(0x100B19), "Violet shadows and electric cyan highlights.", ColorMode.GRADIENT),
        AsciiStyle("blocks", "Block party", "Abstract", " ░▒▓█", color(0xEEB899), color(0x160E0B), "Chunky shapes. Beautifully low resolution."),
        AsciiStyle("dots", "Dot matrix", "Abstract", " .:oO@", color(0xE3F5C3), color(0x10150D), "A soft grid of dots with a print-like finish."),
        AsciiStyle("rose", "Rose quartz", "Creative", " .,:;irsXA#@", color(0xF6BCCB), color(0x1A0D14), "Soft pink characters. Quietly expressive."),
        AsciiStyle("ice", "Arctic", "Creative", " .:-=+*#%@", color(0xAFEDEB), color(0x071819), "Cool, clear, and cinematic."),
        AsciiStyle("type", "Typewriter", "Essential", " .,'-:;!ilI?TMW@", color(0xD8D3C7), color(0x161614), "An analog feel, typed frame by frame."),
        AsciiStyle("code", "Source code", "Terminal", " .;:{}[]()<>#", color(0xB1B6FF), color(0x0E0D1B), "Brackets, braces, and beautiful chaos."),
        AsciiStyle("stars", "Stardust", "Abstract", " .+*xX#", color(0xF2E7AF), color(0x13120D), "A constellation hiding in every frame."),
        AsciiStyle("noir", "Noir", "Essential", " .:-=+*#%@", color(0xF5F5F5), color(0x050505), "Stripped back to light and shadow."),
        AsciiStyle("solar", "Solar flare", "Creative", " .:;+*xX#@", color(0xFFB465), color(0x1E0B12), "Burnt orange with a magenta undertone.", ColorMode.GRADIENT),
        AsciiStyle("minimal", "Minimal", "Abstract", " .-+#", color(0xC3CDBB), color(0x10130F), "Five characters. Infinite possibilities."),
    )
    val dithered = listOf(
        AsciiStyle("dither_bayer2", "Bayer 2×2", "Dithered", " .#", color(0xF0F2ED), color(0x080B0C),
            "Bold checker patterns with a classic one-bit feel.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.BAYER_2),
        AsciiStyle("dither_bayer4", "Bayer 4×4", "Dithered", " .#", color(0xF0F2ED), color(0x080B0C),
            "The timeless ordered-dither look. Crisp and balanced.", renderMode = RenderMode.DITHER),
        AsciiStyle("dither_bayer8", "Bayer 8×8", "Dithered", " .#", color(0xF0F2ED), color(0x080B0C),
            "More shades hidden inside a finely woven pixel pattern.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.BAYER_8),
        AsciiStyle("dither_bayer16", "Bayer 16×16", "Dithered", " .#", color(0xF0F2ED), color(0x080B0C),
            "An intricate screen with smoother transitions through light.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.BAYER_16),
        AsciiStyle("dither_dots", "Halftone Dots", "Dithered", " .#", color(0x24221D), color(0xEEE7D7),
            "Clusters of ink on warm paper, like a tiny printed photograph.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.DOTS),
        AsciiStyle("dither_diagonal", "Diagonal Ink", "Dithered", " .#", color(0xC4E0EE), color(0x102233),
            "Slanted lines turn the shadows into an engraved texture.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.DIAGONAL),
        AsciiStyle("dither_cross", "Crosshatch", "Dithered", " .#", color(0x342D29), color(0xE9DFCF),
            "Intersecting strokes with the feel of a pen-and-ink sketch.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.CROSSHATCH),
        AsciiStyle("dither_stipple", "Stipple", "Dithered", " .#", color(0xDED9C4), color(0x141510),
            "A fixed field of scattered dots. Texture without animated grain.", renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.STIPPLE),
        AsciiStyle("dither_retro", "Retro 4-tone", "Dithered", " .#", color(0xCADC9B), color(0x142C1D),
            "Four green tones, inspired by pocket-sized pixel worlds.", renderMode = RenderMode.DITHER, toneLevels = 4),
        AsciiStyle("dither_color", "Color Dither", "Dithered", " .#", color(0xFFFFFF), color(0x05070A),
            "A reduced RGB palette with a colorful ordered pixel texture.", colorMode = ColorMode.ORIGINAL,
            renderMode = RenderMode.DITHER, ditherPattern = DitherPattern.BAYER_8, toneLevels = 4),
    )
    val all = ascii + dithered
    val categories = listOf("All", "Dithered", "Essential", "Terminal", "Creative", "Abstract", "Favorites")
    fun find(id: String): AsciiStyle = all.firstOrNull { it.id == id } ?: all.first()
}
