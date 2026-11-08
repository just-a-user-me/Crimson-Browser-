package org.mozilla.fenix.crimson

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager

/**
 * Crimson settings. They live in the same default SharedPreferences file the settings screen writes to.
 */
object CrimsonPrefs {
    const val KEY_SPLASH = "pref_crimson_splash_animation"
    const val KEY_ACCENT = "pref_crimson_accent_color"
    const val KEY_FORCE_DARK = "pref_crimson_force_dark"
    const val KEY_DEV_MODE = "pref_crimson_dev_mode"

    const val DEFAULT_ACCENT: Int = 0xFFDC143C.toInt()

    fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun splashEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SPLASH, true)

    fun accent(context: Context): Int = prefs(context).getInt(KEY_ACCENT, DEFAULT_ACCENT)

    fun forceDark(context: Context): Boolean = prefs(context).getBoolean(KEY_FORCE_DARK, false)

    fun devMode(context: Context): Boolean = prefs(context).getBoolean(KEY_DEV_MODE, false)

    /** Lighter tint of the accent, used for the start of the logo gradient. */
    fun gradientLight(accent: Int): Int = ColorUtils.blendARGB(accent, Color.WHITE, 0.28f)

    /** Deeper shade of the accent, used for the end of the logo gradient. */
    fun gradientDark(accent: Int): Int = ColorUtils.blendARGB(accent, Color.BLACK, 0.5f)
}
