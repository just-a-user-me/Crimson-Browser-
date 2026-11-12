package app.crimson.browser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BoolPref(private val sp: SharedPreferences, private val key: String, default: Boolean) {
    var value by mutableStateOf(sp.getBoolean(key, default))
        private set

    fun set(v: Boolean) {
        value = v
        sp.edit().putBoolean(key, v).apply()
    }
}

class IntPref(private val sp: SharedPreferences, private val key: String, default: Int) {
    var value by mutableIntStateOf(sp.getInt(key, default))
        private set

    fun set(v: Int) {
        value = v
        sp.edit().putInt(key, v).apply()
    }
}

class FloatPref(private val sp: SharedPreferences, private val key: String, default: Float) {
    var value by mutableFloatStateOf(sp.getFloat(key, default))
        private set

    fun set(v: Float) {
        value = v
        sp.edit().putFloat(key, v).apply()
    }
}

class StringPref(private val sp: SharedPreferences, private val key: String, default: String) {
    var value by mutableStateOf(sp.getString(key, default) ?: default)
        private set

    fun set(v: String) {
        value = v
        sp.edit().putString(key, v).apply()
    }
}

/** Every user-changeable setting in Crimson. Values are Compose state, so the UI updates live. */
class AppSettings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("crimson_settings", Context.MODE_PRIVATE)

    // Appearance
    val accent = IntPref(sp, "accent", DEFAULT_ACCENT)
    val themeMode = IntPref(sp, "theme_mode", 0) // 0 dark, 1 amoled, 2 light
    val uiScale = FloatPref(sp, "ui_scale", 1f)
    val cornerScale = FloatPref(sp, "corner_scale", 1f)
    val barAtTop = BoolPref(sp, "bar_top", false)
    val wallpaper = StringPref(sp, "wallpaper", "")
    val wallpaperDim = FloatPref(sp, "wallpaper_dim", 0.35f)
    val wallpaperBlur = FloatPref(sp, "wallpaper_blur", 0f)
    val splash = BoolPref(sp, "splash", true)
    val iconIndex = IntPref(sp, "icon_index", -1)
    val webTextScale = FloatPref(sp, "web_text_scale", 1f)
    val showShortcuts = BoolPref(sp, "show_shortcuts", true)
    val uiStyle = IntPref(sp, "ui_style", 1) // 0 classic, 1 liquid glass
    val defaultEngine = IntPref(sp, "default_engine", 0) // 0 Gecko, 1 Chromium (system WebView)

    // Privacy Guard
    val trackingLevel = IntPref(sp, "tracking_level", 2) // 0 off, 1 standard, 2 strict
    val blockAds = BoolPref(sp, "block_ads", true)
    val blockAnalytics = BoolPref(sp, "block_analytics", true)
    val blockSocial = BoolPref(sp, "block_social", true)
    val blockCrypto = BoolPref(sp, "block_crypto", true)
    val blockFingerprinters = BoolPref(sp, "block_fingerprinters", true)
    val blockContentTrackers = BoolPref(sp, "block_content", true)
    val safeBrowsing = BoolPref(sp, "safe_browsing", true)
    val queryStripping = BoolPref(sp, "query_stripping", true)
    val httpsOnly = BoolPref(sp, "https_only", true)
    val fingerprintProtection = BoolPref(sp, "fingerprint", true)
    val globalPrivacyControl = BoolPref(sp, "gpc", true)
    val jsEnabled = BoolPref(sp, "js_enabled", true)
    val jsBlockedSites = StringPref(sp, "js_blocked_sites", "")
    val cookieBehavior = IntPref(sp, "cookie_behavior", 5)
    val blockAppRedirects = BoolPref(sp, "block_app_redirects", true)
    val blockPopups = BoolPref(sp, "block_popups", true)

    // Clear on exit
    val clearOnExit = BoolPref(sp, "clear_on_exit", false)
    val clearCookies = BoolPref(sp, "clear_cookies", true)
    val clearCache = BoolPref(sp, "clear_cache", true)
    val clearHistory = BoolPref(sp, "clear_history", true)
    val clearSiteData = BoolPref(sp, "clear_site_data", true)
    val clearDownloadsList = BoolPref(sp, "clear_downloads_list", false)

    // Network
    val torMode = IntPref(sp, "tor_mode", 0) // 0 off, 1 only .onion, 2 all traffic
    val secureDns = BoolPref(sp, "secure_dns", false)

    // Tools
    val forceDark = BoolPref(sp, "force_dark", false)
    val devMode = BoolPref(sp, "dev_mode", false)
    val remoteDebugging = BoolPref(sp, "remote_debugging", false)
    val mediaButton = BoolPref(sp, "media_button", true)
    val mediaButtonOpacity = FloatPref(sp, "media_button_opacity", 0.85f)
    val searchEngine = IntPref(sp, "search_engine", 0)
    val desktopByDefault = BoolPref(sp, "desktop_default", false)

    fun isJsBlockedFor(host: String): Boolean {
        if (!jsEnabled.value) return true
        return jsBlockedSites.value.lineSequence().any { it.isNotBlank() && it == host }
    }

    fun blockedSites(): List<String> =
        jsBlockedSites.value.lineSequence().filter { it.isNotBlank() }.toList()

    fun setSiteJsBlocked(host: String, blocked: Boolean) {
        val sites = blockedSites().toMutableList()
        sites.remove(host)
        if (blocked) sites.add(host)
        jsBlockedSites.set(sites.joinToString("\n"))
    }

    companion object {
        /** The Crimson logo color. */
        const val DEFAULT_ACCENT: Int = 0xFFDC143C.toInt()
    }
}

class SearchEngine(val name: String, val template: String)

object SearchEngines {
    val all = listOf(
        SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        SearchEngine("Brave Search", "https://search.brave.com/search?q=%s"),
        SearchEngine("Startpage", "https://www.startpage.com/do/search?q=%s"),
        SearchEngine("Google", "https://www.google.com/search?q=%s"),
        SearchEngine("Bing", "https://www.bing.com/search?q=%s"),
        SearchEngine("Ecosia", "https://www.ecosia.org/search?q=%s"),
        SearchEngine("Qwant", "https://www.qwant.com/?q=%s"),
    )
}
