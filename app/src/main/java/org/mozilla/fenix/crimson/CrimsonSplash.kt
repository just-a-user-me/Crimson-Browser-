package org.mozilla.fenix.crimson

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Startup animation (about 1.2 seconds):
 * 1. The C and the dot are both on screen, with the dot inside the C.
 * 2. The dot falls onto the lower part of the C.
 * 3. The C vanishes smoothly from the top down.
 * 4. When the bottom of the C is gone, the dot falls away and the app is revealed.
 * Tap to skip.
 */
object CrimsonSplash {
    private var shownThisProcess = false

    fun showIfEnabled(activity: ComponentActivity) {
        if (shownThisProcess) return
        shownThisProcess = true
        if (!CrimsonPrefs.splashEnabled(activity)) return

        val accentArgb = CrimsonPrefs.accent(activity)
        val composeView = ComposeView(activity)
        composeView.setContent {
            CrimsonSplashOverlay(accentArgb = accentArgb) {
                composeView.post { (composeView.parent as? ViewGroup)?.removeView(composeView) }
            }
        }
        activity.addContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}

private val FallEasing = Easing { fraction -> fraction * fraction }
private val BounceUpEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

@Composable
private fun CrimsonSplashOverlay(accentArgb: Int, onFinished: () -> Unit) {
    val accent = Color(accentArgb)
    val light = Color(CrimsonPrefs.gradientLight(accentArgb))
    val dark = Color(CrimsonPrefs.gradientDark(accentArgb))
    val backdrop = Color(0xFF0A0B10)

    // 0 = dot floating inside the C, 1 = dot resting on the bottom of the C.
    val dotRest = remember { Animatable(0f) }
    // 0 = full C, 1 = C completely gone (vanishes from the top down).
    val vanish = remember { Animatable(0f) }
    // 0 = dot resting, 1 = dot fallen off the bottom of the screen.
    val drop = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        dotRest.animateTo(1f, tween(260, easing = FallEasing))
        dotRest.animateTo(0.94f, tween(70, easing = BounceUpEasing))
        dotRest.animateTo(1f, tween(70, easing = FallEasing))
        launch { vanish.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
        delay(300)
        launch {
            delay(60)
            backdropAlpha.animateTo(0f, tween(260))
        }
        drop.animateTo(1f, tween(340, easing = FallEasing))
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop.copy(alpha = backdropAlpha.value))
            .pointerInput(Unit) {
                detectTapGestures {
                    scope.launch {
                        backdropAlpha.animateTo(0f, tween(120))
                        finish()
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val k = 200.dp.toPx() / 108f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = 22f * k
            val strokeWidth = 8f * k
            val dotRadius = 5.5f * k

            val brush = Brush.linearGradient(
                colors = listOf(light, accent, dark),
                start = Offset(cx - radius, cy - radius),
                end = Offset(cx + radius, cy + radius),
            )

            // The C: the visible part shrinks from the top end, moving down along the left side
            // toward the bottom end, which stays put until the very last moment.
            val gone = vanish.value
            val remaining = -280f * (1f - gone)
            if (remaining < -0.5f) {
                drawArc(
                    brush = brush,
                    startAngle = -40f - 280f * gone,
                    sweepAngle = remaining,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            val floatY = cy - radius * 0.35f
            val restY = cy + radius - strokeWidth / 2f - dotRadius
            val baseY = floatY + (restY - floatY) * dotRest.value
            val dotY = baseY + (size.height - restY + dotRadius * 2f) * drop.value
            drawCircle(
                brush = brush,
                radius = dotRadius,
                center = Offset(cx, dotY),
            )
        }
    }
}
