package app.crimson.browser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CrimsonColors {
    val Background = Color(0xFF0A0B10)
    val Surface = Color(0xFF14151C)
    val SurfaceHigh = Color(0xFF1D1F28)
    val OnSurface = Color(0xFFECECF1)
    val OnSurfaceMuted = Color(0xFF9A9CAB)
}

@Composable
fun CrimsonTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = CrimsonColors.Background,
        background = CrimsonColors.Background,
        onBackground = CrimsonColors.OnSurface,
        surface = CrimsonColors.Surface,
        onSurface = CrimsonColors.OnSurface,
        surfaceVariant = CrimsonColors.SurfaceHigh,
        onSurfaceVariant = CrimsonColors.OnSurfaceMuted,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
