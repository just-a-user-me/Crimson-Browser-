package app.crimson.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

data class PaletteColor(val name: String, val argb: Long)

/** The 16 Minecraft dye colors. The whole app theme is built from one of these. */
object Palette {
    val colors = listOf(
        PaletteColor("White", 0xFFF9FFFE),
        PaletteColor("Orange", 0xFFF9801D),
        PaletteColor("Magenta", 0xFFC74EBD),
        PaletteColor("Light blue", 0xFF3AB3DA),
        PaletteColor("Yellow", 0xFFFED83D),
        PaletteColor("Lime", 0xFF80C71F),
        PaletteColor("Pink", 0xFFF38BAA),
        PaletteColor("Gray", 0xFF474F52),
        PaletteColor("Light gray", 0xFF9D9D97),
        PaletteColor("Cyan", 0xFF169C9C),
        PaletteColor("Purple", 0xFF8932B8),
        PaletteColor("Blue", 0xFF3C44AA),
        PaletteColor("Brown", 0xFF835432),
        PaletteColor("Green", 0xFF5E7C16),
        PaletteColor("Red", 0xFFB02E26),
        PaletteColor("Black", 0xFF1D1D21),
    )

    const val DEFAULT_INDEX = 14

    fun base(index: Int): Color = Color(colors[index.coerceIn(0, colors.lastIndex)].argb)

    /** The dye color, lightened a little when it is too dark to read on the dark UI. */
    fun accent(index: Int): Color {
        val color = base(index)
        val t = ((0.4f - color.luminance()).coerceAtLeast(0f) * 0.9f).coerceAtMost(0.6f)
        return lerp(color, Color.White, t)
    }

    fun gradientLight(accent: Color): Color = lerp(accent, Color.White, 0.28f)

    fun gradientDark(accent: Color): Color = lerp(accent, Color.Black, 0.5f)
}
