package app.crimson.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/** The Crimson mark: an open ring (a C) with a dot inside, with a gradient made from the accent. */
@Composable
fun CrimsonLogo(accent: Color, modifier: Modifier = Modifier) {
    val light = Palette.gradientLight(accent)
    val dark = Palette.gradientDark(accent)
    Canvas(modifier = modifier) {
        val k = size.minDimension / 56f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = 22f * k
        val brush = Brush.linearGradient(
            colors = listOf(light, accent, dark),
            start = Offset(cx - radius, cy - radius),
            end = Offset(cx + radius, cy + radius),
        )
        drawArc(
            brush = brush,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 8f * k, cap = StrokeCap.Round),
        )
        drawCircle(brush = brush, radius = 5.5f * k, center = Offset(cx, cy))
    }
}
