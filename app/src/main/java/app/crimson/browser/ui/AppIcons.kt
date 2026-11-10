package app.crimson.browser.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The 16 pre-made app icons, one for each Minecraft dye color, plus the default Crimson icon. */
object AppIcons {
    val names = listOf(
        "White", "Orange", "Magenta", "Light blue", "Yellow", "Lime", "Pink", "Gray",
        "Light gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black",
    )

    val colors = longArrayOf(
        0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA, 0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
        0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA, 0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21,
    )

    private fun aliasName(index: Int): String =
        if (index < 0) "app.crimson.browser.IconDefault" else "app.crimson.browser.Icon" + String.format("%02d", index)

    /** Switches the launcher icon. Pass -1 for the default Crimson icon. */
    fun apply(context: Context, index: Int) {
        val pm = context.packageManager
        for (i in -1 until names.size) {
            val state = if (i == index) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, aliasName(i)),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (_: Exception) {
            }
        }
    }
}
