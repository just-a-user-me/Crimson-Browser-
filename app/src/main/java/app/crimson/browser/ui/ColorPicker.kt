package app.crimson.browser.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A full color picker: saturation/brightness square, hue bar, live gradient preview and hex code. */
@Composable
fun ColorPicker(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalCrimson.current
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var lastEmitted by remember { mutableIntStateOf(0) }

    LaunchedEffect(color) {
        if (color != lastEmitted) {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(color, hsv)
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
            lastEmitted = color
        }
    }

    fun emit() {
        val out = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        lastEmitted = out
        onChange(out)
    }

    val current = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
    val pure = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(c.r(20)))
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        sat = (o.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val center = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 13.dp.toPx(), center = center, style = Stroke(5.dp.toPx()))
            drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        hue = (o.x / size.width).coerceIn(0f, 1f) * 359.9f
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 359.9f
                        emit()
                    }
                },
        ) {
            val stops = List(7) { Color(AndroidColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }
            drawRect(Brush.horizontalGradient(stops))
            val x = (hue / 360f) * size.width
            drawCircle(
                Color.White,
                radius = size.height / 2f - 2.dp.toPx(),
                center = Offset(x.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f),
                style = Stroke(3.dp.toPx()),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(c.r(14)))
                    .border(1.dp, c.outline, RoundedCornerShape(c.r(14))),
            ) {
                drawRect(
                    Brush.linearGradient(
                        listOf(gradientLight(current), current, gradientDark(current)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
            Text(
                String.format("#%06X", 0xFFFFFF and AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))),
                color = c.onSurface,
                fontSize = 16.sp,
            )
        }
    }
}
