package org.mozilla.fenix.crimson

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference

/**
 * Settings row that opens the Crimson accent color picker.
 */
class CrimsonColorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        refreshSummary()
    }

    private fun refreshSummary() {
        val color = CrimsonPrefs.accent(context)
        summary = String.format("#%06X", 0xFFFFFF and color)
    }

    override fun onClick() {
        CrimsonColorDialog.show(context, CrimsonPrefs.accent(context)) { picked ->
            CrimsonPrefs.prefs(context).edit().putInt(CrimsonPrefs.KEY_ACCENT, picked).apply()
            refreshSummary()
        }
    }
}

private const val SURFACE = 0xFF14151C.toInt()
private const val TRACK = 0xFF2A2C36.toInt()

private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

private fun Context.dpi(value: Float): Int = dp(value).toInt()

internal object CrimsonColorDialog {
    private val SWATCHES = longArrayOf(
        0xFFDC143C, 0xFFFF6B35, 0xFFFFB400, 0xFF2ECC71,
        0xFF00C2D1, 0xFF3D7BFF, 0xFF8B5CF6, 0xFFE91E8C,
    )

    fun show(context: Context, initial: Int, onApply: (Int) -> Unit) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)

        val preview = LogoPreviewView(context)
        val sv = SaturationValueView(context)
        val hue = HueBarView(context)
        val hex = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        fun currentColor(): Int = Color.HSVToColor(hsv)

        fun refresh() {
            val color = currentColor()
            preview.accent = color
            sv.hue = hsv[0]
            sv.saturation = hsv[1]
            sv.value = hsv[2]
            hue.hue = hsv[0]
            hex.text = String.format("#%06X", 0xFFFFFF and color)
            preview.invalidate()
            sv.invalidate()
            hue.invalidate()
        }

        sv.onChange = { s, v ->
            hsv[1] = s
            hsv[2] = v
            refresh()
        }
        hue.onChange = { h ->
            hsv[0] = h
            refresh()
        }

        val swatchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (swatch in SWATCHES) {
            val color = swatch.toInt()
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                setOnClickListener {
                    Color.colorToHSV(color, hsv)
                    refresh()
                }
            }
            val size = context.dpi(30f)
            swatchRow.addView(
                dot,
                LinearLayout.LayoutParams(size, size).apply {
                    marginStart = context.dpi(5f)
                    marginEnd = context.dpi(5f)
                },
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dpi(20f), context.dpi(20f), context.dpi(20f), context.dpi(8f))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = context.dp(28f)
            }
        }
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(130f)))
        content.addView(
            sv,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(180f)).apply {
                topMargin = context.dpi(12f)
            },
        )
        content.addView(
            hue,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dpi(28f)).apply {
                topMargin = context.dpi(14f)
            },
        )
        content.addView(
            swatchRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dpi(16f)
            },
        )
        content.addView(
            hex,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dpi(12f)
            },
        )

        refresh()

        val dialog = AlertDialog.Builder(context)
            .setView(content)
            .setPositiveButton("Apply") { _, _ -> onApply(currentColor()) }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset") { _, _ -> onApply(CrimsonPrefs.DEFAULT_ACCENT) }
            .create()
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = context.dp(28f)
            },
        )
        dialog.show()
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            dialog.getButton(which)?.setTextColor(Color.WHITE)
        }
    }
}

/** Shows the logo (open ring plus dot) in the chosen color with the auto-generated gradient. */
private class LogoPreviewView(context: Context) : View(context) {
    var accent: Int = CrimsonPrefs.DEFAULT_ACCENT

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0B10.toInt() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), context.dp(20f), context.dp(20f), bgPaint)

        val k = minOf(w, h) / 108f * 0.9f
        val cx = w / 2f
        val cy = h / 2f
        val radius = 22f * k
        val light = CrimsonPrefs.gradientLight(accent)
        val dark = CrimsonPrefs.gradientDark(accent)
        val shader = LinearGradient(
            cx - radius, cy - radius, cx + radius, cy + radius,
            intArrayOf(light, accent, dark), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        ringPaint.shader = shader
        ringPaint.strokeWidth = 8f * k
        dotPaint.shader = shader

        canvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 40f, 280f, false, ringPaint)
        canvas.drawCircle(cx, cy, 5.5f * k, dotPaint)
    }
}

/** Square for picking saturation (left to right) and brightness (bottom to top). */
private class SaturationValueView(context: Context) : View(context) {
    var hue = 0f
    var saturation = 1f
    var value = 1f
    var onChange: ((Float, Float) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3f)
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rect = RectF(0f, 0f, w, h)
        val radius = context.dp(16f)

        val pure = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        paint.shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)

        val x = saturation * w
        val y = (1f - value) * h
        canvas.drawCircle(x, y, context.dp(10f), thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val s = (event.x / width).coerceIn(0f, 1f)
                val v = 1f - (event.y / height).coerceIn(0f, 1f)
                onChange?.invoke(s, v)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

/** Rainbow bar for picking the hue. */
private class HueBarView(context: Context) : View(context) {
    var hue = 0f
    var onChange: ((Float) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3f)
        color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        paint.shader = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, paint)

        val x = hue / 360f * w
        canvas.drawCircle(x.coerceIn(h / 2f, w - h / 2f), h / 2f, h / 2f - context.dp(2f), thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onChange?.invoke((event.x / width).coerceIn(0f, 1f) * 359.9f)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
