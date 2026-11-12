package app.crimson.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** All colors used by Crimson screens. Everything is derived from the one accent color. */
class CrimsonColors(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val muted: Color,
    val outline: Color,
    val accent: Color,
    val accentLight: Color,
    val accentDark: Color,
    val isLight: Boolean,
    val corner: Float,
    val glass: Boolean,
    val logo: Color,
) {
    fun r(dp: Int): Dp = (dp * corner).dp

    val accentBrush: Brush
        get() = Brush.linearGradient(listOf(accentLight, accent, accentDark))

    val onAccent: Color
        get() = if (accent.luminance() > 0.45f) Color(0xFF0A0B10) else Color.White
}

val LocalCrimson = staticCompositionLocalOf<CrimsonColors> { error("CrimsonTheme missing") }

fun readableAccent(color: Color, light: Boolean): Color {
    val lum = color.luminance()
    return if (!light) {
        val t = ((0.4f - lum).coerceAtLeast(0f) * 0.9f).coerceAtMost(0.6f)
        lerp(color, Color.White, t)
    } else {
        val t = ((lum - 0.5f).coerceAtLeast(0f) * 0.9f).coerceAtMost(0.5f)
        lerp(color, Color.Black, t)
    }
}

fun gradientLight(accent: Color): Color = lerp(accent, Color.White, 0.28f)

fun gradientDark(accent: Color): Color = lerp(accent, Color.Black, 0.5f)

fun logoColor(argb: Int): Color {
    val c = Color(argb)
    return if (c.luminance() < 0.06f) lerp(c, Color.White, 0.35f) else c
}

fun buildColors(accentArgb: Int, mode: Int, corner: Float, glass: Boolean): CrimsonColors {
    val light = mode == 2
    val accent = readableAccent(Color(accentArgb), light)
    val tint = if (light) 0.04f else 0.05f
    return if (light) {
        CrimsonColors(
            bg = lerp(Color(0xFFF6F6F9), accent, tint),
            surface = lerp(Color(0xFFFFFFFF), accent, 0.02f),
            surfaceHigh = lerp(Color(0xFFEDEEF3), accent, 0.06f),
            onSurface = Color(0xFF15161B),
            muted = Color(0xFF6B6D7A),
            outline = Color(0x1A000000),
            accent = accent,
            accentLight = gradientLight(accent),
            accentDark = gradientDark(accent),
            isLight = true,
            corner = corner,
            glass = glass,
            logo = logoColor(accentArgb),
        )
    } else {
        val amoled = mode == 1
        CrimsonColors(
            bg = if (amoled) Color.Black else lerp(Color(0xFF0A0B10), accent, tint),
            surface = if (amoled) lerp(Color(0xFF0B0B0F), accent, 0.04f) else lerp(Color(0xFF14151C), accent, 0.06f),
            surfaceHigh = if (amoled) lerp(Color(0xFF16161C), accent, 0.06f) else lerp(Color(0xFF1D1F28), accent, 0.08f),
            onSurface = Color(0xFFECECF1),
            muted = Color(0xFF9A9CAB),
            outline = Color(0x14FFFFFF),
            accent = accent,
            accentLight = gradientLight(accent),
            accentDark = gradientDark(accent),
            isLight = false,
            corner = corner,
            glass = glass,
            logo = logoColor(accentArgb),
        )
    }
}

@Composable
fun CrimsonTheme(
    accentArgb: Int,
    mode: Int,
    corner: Float,
    uiScale: Float,
    glass: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = buildColors(accentArgb, mode, corner, glass)
    val scheme = if (colors.isLight) {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.muted,
            outline = colors.outline,
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.muted,
            outline = colors.outline,
        )
    }
    val base = LocalDensity.current
    val scaled = Density(base.density * uiScale, base.fontScale)
    CompositionLocalProvider(
        LocalCrimson provides colors,
        LocalDensity provides scaled,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
