package app.crimson.browser.ui

import android.content.Context

object Prefs {
    private const val FILE = "crimson_prefs"

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun paletteIndex(context: Context): Int = sp(context).getInt("palette", Palette.DEFAULT_INDEX)

    fun setPaletteIndex(context: Context, index: Int) {
        sp(context).edit().putInt("palette", index).apply()
    }

    fun splash(context: Context): Boolean = sp(context).getBoolean("splash", true)

    fun setSplash(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("splash", value).apply()
    }
}
