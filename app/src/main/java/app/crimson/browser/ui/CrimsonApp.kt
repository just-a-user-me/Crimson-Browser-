package app.crimson.browser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.engine.UiPrompt

enum class Screen {
    Browser, Tabs, Settings, Appearance, PrivacyGuard, Trackers, Https, Fingerprint, Scripts, Cookies,
    Redirects, SiteSettings, PrivacyAdvanced, Search, Downloads, History, Bookmarks, Addons, About,
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun CrimsonApp(browser: BrowserState, onQuit: () -> Unit) {
    val settings = browser.settings
    var screen by remember { mutableStateOf(Screen.Browser) }
    val backStack = remember { mutableStateListOf<Screen>() }
    var menuOpen by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(settings.splash.value) }

    fun go(to: Screen) {
        menuOpen = false
        backStack.add(screen)
        screen = to
    }

    fun back() {
        screen = if (backStack.isEmpty()) Screen.Browser else backStack.removeAt(backStack.lastIndex)
    }

    fun toBrowser() {
        backStack.clear()
        screen = Screen.Browser
    }

    fun openInBrowser(url: String) {
        toBrowser()
        browser.open(url)
    }

    CrimsonTheme(settings.accent.value, settings.themeMode.value, settings.cornerScale.value, settings.uiScale.value) {
        val c = LocalCrimson.current
        val view = LocalView.current

        LaunchedEffect(c.isLight, browser.fullscreen) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = c.isLight
            controller.isAppearanceLightNavigationBars = c.isLight
            if (browser.fullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        BackHandler(enabled = screen == Screen.Browser && browser.current?.canGoBack == true) {
            browser.current?.session?.goBack()
        }
        BackHandler(enabled = screen == Screen.Browser && findOpen) { findOpen = false }
        BackHandler(enabled = browser.fullscreen) { browser.current?.session?.exitFullScreen() }
        BackHandler(enabled = screen != Screen.Browser) { back() }
        BackHandler(enabled = mediaOpen) { mediaOpen = false }
        BackHandler(enabled = menuOpen) { menuOpen = false }
        BackHandler(enabled = browser.contextMenu != null) { browser.contextMenu = null }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            when (screen) {
                Screen.Browser -> BrowserScreen(
                    browser = browser,
                    settings = settings,
                    findOpen = findOpen,
                    onCloseFind = { findOpen = false },
                    onTabs = {
                        browser.current?.let { browser.captureThumbnail(it) }
                        go(Screen.Tabs)
                    },
                    onMenu = { menuOpen = true },
                    onShowMedia = { mediaOpen = true },
                )
                Screen.Tabs -> TabSwitcher(browser, onClose = { back() }, onPickTab = { toBrowser() })
                Screen.Settings -> SettingsRoot(browser, settings, { back() }) { go(it) }
                Screen.Appearance -> AppearanceScreen(browser, settings) { back() }
                Screen.PrivacyGuard -> PrivacyGuardScreen(browser, settings, { back() }) { go(it) }
                Screen.Trackers -> TrackersScreen(browser, settings, { back() }) { go(it) }
                Screen.Https -> HttpsScreen(browser, settings) { back() }
                Screen.Fingerprint -> FingerprintScreen(browser, settings) { back() }
                Screen.Scripts -> ScriptsScreen(browser, settings) { back() }
                Screen.Cookies -> CookiesScreen(browser, settings) { back() }
                Screen.Redirects -> RedirectsScreen(settings) { back() }
                Screen.SiteSettings -> SiteSettingsScreen(browser) { back() }
                Screen.PrivacyAdvanced -> PrivacyAdvancedScreen(browser, settings) { back() }
                Screen.Search -> SearchScreen(settings) { back() }
                Screen.Downloads -> DownloadsScreen(browser) { back() }
                Screen.History -> HistoryScreen(browser, { back() }) { openInBrowser(it) }
                Screen.Bookmarks -> BookmarksScreen(browser, { back() }) { openInBrowser(it) }
                Screen.Addons -> AddonsScreen(browser, { back() }) { openInBrowser(it) }
                Screen.About -> AboutScreen { back() }
            }

            if (menuOpen) {
                MenuSheet(
                    browser = browser,
                    settings = settings,
                    onDismiss = { menuOpen = false },
                    onNavigate = { go(it) },
                    onFind = { findOpen = true },
                    onQuit = {
                        menuOpen = false
                        onQuit()
                    },
                )
            }

            if (mediaOpen) {
                val tab = browser.current
                val items = if (tab != null) browser.media.itemsFor(tab.url) else emptyList()
                if (items.isEmpty()) {
                    mediaOpen = false
                } else {
                    MediaSheet(browser, items, tab?.url ?: "") { mediaOpen = false }
                }
            }

            browser.contextMenu?.let { info ->
                ContextSheet(browser, info) { browser.contextMenu = null }
            }

            PromptHost(browser)

            if (showSplash) {
                CrimsonSplash(
                    accent = settings.accent.value.let { readableAccent(Color(it), false) },
                    backdrop = Color(0xFF0A0B10),
                ) { showSplash = false }
            }
        }
    }
}

@Composable
private fun PromptHost(browser: BrowserState) {
    val c = LocalCrimson.current
    val prompt = browser.prompt ?: return

    when (prompt) {
        is UiPrompt.Alert -> AlertDialog(
            onDismissRequest = {
                browser.prompt = null
                prompt.onClose()
            },
            containerColor = c.surface,
            title = { Text(prompt.title, color = c.onSurface) },
            text = { if (prompt.message.isNotBlank()) Text(prompt.message, color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    browser.prompt = null
                    prompt.onClose()
                }) { Text("OK", color = c.accent) }
            },
        )

        is UiPrompt.Confirm -> AlertDialog(
            onDismissRequest = {
                browser.prompt = null
                prompt.onResult(false)
            },
            containerColor = c.surface,
            title = { Text(prompt.title, color = c.onSurface) },
            text = { if (prompt.message.isNotBlank()) Text(prompt.message, color = c.muted) },
            confirmButton = {
                TextButton(onClick = {
                    browser.prompt = null
                    prompt.onResult(true)
                }) { Text(prompt.yes, color = c.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    browser.prompt = null
                    prompt.onResult(false)
                }) { Text(prompt.no, color = c.muted) }
            },
        )

        is UiPrompt.Text -> {
            var text by remember(prompt) { mutableStateOf(prompt.initial) }
            AlertDialog(
                onDismissRequest = {
                    browser.prompt = null
                    prompt.onResult(null)
                },
                containerColor = c.surface,
                title = { Text(prompt.title, color = c.onSurface) },
                text = {
                    Column {
                        if (prompt.message.isNotBlank()) Text(prompt.message, color = c.muted)
                        Box(
                            Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(c.r(14)))
                                .background(c.surfaceHigh)
                                .padding(12.dp),
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it },
                                textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp),
                                cursorBrush = SolidColor(c.accent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        browser.prompt = null
                        prompt.onResult(text)
                    }) { Text("OK", color = c.accent) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        browser.prompt = null
                        prompt.onResult(null)
                    }) { Text("Cancel", color = c.muted) }
                },
            )
        }

        is UiPrompt.Choice -> {
            val selected = remember(prompt) { mutableStateListOf<String>() }
            AlertDialog(
                onDismissRequest = {
                    browser.prompt = null
                    prompt.onResult(null)
                },
                containerColor = c.surface,
                title = { Text(prompt.title.ifBlank { "Choose" }, color = c.onSurface) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (prompt.message.isNotBlank()) Text(prompt.message, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
                        prompt.labels.forEachIndexed { i, label ->
                            val id = prompt.ids[i]
                            val isOn = selected.contains(id)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(c.r(12)))
                                    .clickable {
                                        if (prompt.multiple) {
                                            if (isOn) selected.remove(id) else selected.add(id)
                                        } else {
                                            browser.prompt = null
                                            prompt.onResult(listOf(id))
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                            ) {
                                Text(
                                    (if (prompt.multiple) (if (isOn) "\u2611  " else "\u2610  ") else "") + label,
                                    color = if (isOn) c.accent else c.onSurface,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (prompt.multiple) {
                        TextButton(onClick = {
                            browser.prompt = null
                            prompt.onResult(selected.toList())
                        }) { Text("OK", color = c.accent) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        browser.prompt = null
                        prompt.onResult(null)
                    }) { Text("Cancel", color = c.muted) }
                },
            )
        }
    }
}
