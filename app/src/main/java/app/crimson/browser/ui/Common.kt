package app.crimson.browser.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box as BoxLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SurfaceBorder(): BorderStroke = BorderStroke(1.dp, LocalCrimson.current.outline)

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalCrimson.current
    Box(Modifier.fillMaxSize()) {
    AmbientBackground()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                color = c.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        if (scroll) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                content = content,
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                content = content,
            )
        }
    }
    }
}

@Composable
fun IconTap(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = LocalCrimson.current
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = (tint ?: c.onSurface).copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = LocalCrimson.current.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalCrimson.current
    Column(
        modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(c.r(26)))
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalCrimson.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(c.r(12)))
                    .background(if (c.glass) c.accentBrush else Brush.linearGradient(listOf(c.accent.copy(alpha = 0.14f), c.accent.copy(alpha = 0.14f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (c.glass) c.onAccent else c.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onSurface, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val c = LocalCrimson.current
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.accent,
                    checkedThumbColor = c.onAccent,
                    uncheckedTrackColor = c.surfaceHigh,
                    uncheckedThumbColor = c.muted,
                    uncheckedBorderColor = c.outline,
                ),
            )
        },
    )
}

@Composable
fun RadioRow(title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    val c = LocalCrimson.current
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) c.accent else c.muted.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.accent))
            }
        },
    )
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
) {
    val c = LocalCrimson.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = c.onSurface, fontSize = 16.sp)
            Text(label, color = c.muted, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.surfaceHigh,
            ),
        )
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val c = LocalCrimson.current
    val shape = RoundedCornerShape(c.r(18))
    Box(
        modifier
            .clip(shape)
            .then(
                if (filled) Modifier.background(c.accentBrush)
                else Modifier.background(c.surfaceHigh).border(SurfaceBorder(), shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) c.onAccent else c.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalCrimson.current
    val shape = RoundedCornerShape(c.r(14))
    Box(
        Modifier
            .glassPanel(shape, 0.7f)
            .background(if (selected) c.accent.copy(alpha = 0.28f) else Color.Transparent)
            .border(1.dp, if (selected) c.accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.accent else c.onSurface, fontSize = 14.sp)
    }
}

@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalCrimson.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onDismiss()
                },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(topStart = c.r(30), topEnd = c.r(30)), clip = false)
                .background(c.surface.copy(alpha = if (c.glass) 0.9f else 1f), RoundedCornerShape(topStart = c.r(30), topEnd = c.r(30)))
                .glassPanel(RoundedCornerShape(topStart = c.r(30), topEnd = c.r(30)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun GapV(dp: Int) {
    Spacer(Modifier.height(dp.dp))
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(LocalCrimson.current.outline),
    )
}


/** Soft colored light behind everything, used by the liquid glass style. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val c = LocalCrimson.current
    Box(modifier.fillMaxSize().background(c.bg)) {
        if (c.glass) {
            val hsv = FloatArray(3)
            AColor.colorToHSV(c.accent.toArgb(), hsv)
            hsv[0] = (hsv[0] + 70f) % 360f
            val other = Color(AColor.HSVToColor(hsv))
            Canvas(Modifier.fillMaxSize()) {
                val m = size.minDimension
                drawRect(
                    Brush.radialGradient(
                        listOf(c.accent.copy(alpha = if (c.isLight) 0.26f else 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.07f),
                        radius = m * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(c.accentDark.copy(alpha = if (c.isLight) 0.22f else 0.45f), Color.Transparent),
                        center = Offset(size.width * 0.98f, size.height * 0.55f),
                        radius = m * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(other.copy(alpha = if (c.isLight) 0.14f else 0.2f), Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 1.0f),
                        radius = m * 0.85f,
                    ),
                )
            }
        }
    }
}

/** A frosted glass panel in the liquid glass style, or a plain surface in the classic style. */
@Composable
fun Modifier.glassPanel(shape: Shape, strength: Float = 1f): Modifier {
    val c = LocalCrimson.current
    return if (c.glass) {
        val top = if (c.isLight) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.075f + 0.07f * strength)
        val bottom = if (c.isLight) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.02f + 0.03f * strength)
        val edge = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (c.isLight) 0.95f else 0.5f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = if (c.isLight) 0.6f else 0.18f),
            ),
        )
        this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(BorderStroke(1.dp, edge), shape)
    } else {
        this
            .clip(shape)
            .background(c.surface)
            .border(BorderStroke(1.dp, c.outline), shape)
    }
}

/** Glass panel that also floats above the page with a soft shadow. */
@Composable
fun Modifier.floatingGlass(shape: Shape): Modifier {
    val c = LocalCrimson.current
    val base = if (c.glass) {
        this.shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = c.accent.copy(alpha = 0.35f))
            .background(c.surface.copy(alpha = 0.82f), shape)
    } else {
        this.shadow(8.dp, shape, clip = false)
    }
    return base.glassPanel(shape)
}
