package app.crimson.browser.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.crimson.browser.data.AppSettings
import app.crimson.browser.data.SearchEngines
import app.crimson.browser.engine.BrowserState

@Composable
fun SettingsRoot(browser: BrowserState, settings: AppSettings, onBack: () -> Unit, onNavigate: (Screen) -> Unit) {
    val c = LocalCrimson.current
    ScreenScaffold("Settings", onBack) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CrimsonLogo(c.accent, Modifier.size(54.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Crimson", color = c.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Version 0.1", color = c.muted, fontSize = 13.sp)
            }
        }
        SettingsCard {
            SettingRow("Appearance", "Color, theme, wallpaper, icon", Icons.Default.Create, { onNavigate(Screen.Appearance) })
            SettingRow("Privacy Guard", "Trackers, HTTPS, fingerprints, cookies", Icons.Default.Lock, { onNavigate(Screen.PrivacyGuard) })
            SettingRow("Search engine", SearchEngines.all[settings.searchEngine.value].name, Icons.Default.Search, { onNavigate(Screen.Search) })
            SettingRow("Add-ons", "Extensions like uBlock Origin", Icons.Default.Build, { onNavigate(Screen.Addons) })
            SettingRow("Downloads", "Files and videos you saved", Icons.Default.Home, { onNavigate(Screen.Downloads) })
            SettingRow("Bookmarks", null, Icons.Default.Star, { onNavigate(Screen.Bookmarks) })
            SettingRow("History", null, Icons.Default.Info, { onNavigate(Screen.History) })
        }
        GapV(14)
        SettingsCard {
            SwitchRow(
                "Video download button",
                settings.mediaButton.value,
                { settings.mediaButton.set(it) },
                "Show a button when a page has a video you can save",
                Icons.Default.Settings,
            )
            if (settings.mediaButton.value) {
                SliderRow(
                    "Button opacity",
                    settings.mediaButtonOpacity.value,
                    0.3f..1f,
                    "${(settings.mediaButtonOpacity.value * 100).toInt()}%",
                ) { settings.mediaButtonOpacity.set(it) }
            }
            SwitchRow("Desktop sites by default", settings.desktopByDefault.value, { settings.desktopByDefault.set(it) })
        }
        GapV(14)
        SettingsCard {
            SettingRow("About", "Crimson on GeckoView", Icons.Default.Warning, { onNavigate(Screen.About) })
        }
        GapV(24)
    }
}

@Composable
fun AppearanceScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    val c = LocalCrimson.current
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            settings.wallpaper.set(uri.toString())
        }
    }

    ScreenScaffold("Appearance", onBack) {
        SectionLabel("Theme color")
        SettingsCard(Modifier.padding(0.dp)) {
            Column(Modifier.padding(16.dp)) {
                ColorPicker(settings.accent.value, { settings.accent.set(it) })
                GapV(12)
                PillButton("Reset to Crimson", filled = false) { settings.accent.set(AppSettings.DEFAULT_ACCENT) }
            }
        }

        SectionLabel("Theme")
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Dark", settings.themeMode.value == 0) { settings.themeMode.set(0) }
            Chip("AMOLED", settings.themeMode.value == 1) { settings.themeMode.set(1) }
            Chip("Light", settings.themeMode.value == 2) { settings.themeMode.set(2) }
        }

        SectionLabel("Size and shape")
        SettingsCard {
            SliderRow("App size", settings.uiScale.value, 0.8f..1.3f, "${(settings.uiScale.value * 100).toInt()}%") {
                settings.uiScale.set(it)
            }
            SliderRow("Roundness", settings.cornerScale.value, 0.3f..1.5f, "${(settings.cornerScale.value * 100).toInt()}%") {
                settings.cornerScale.set(it)
            }
            SliderRow("Web text size", settings.webTextScale.value, 0.7f..1.8f, "${(settings.webTextScale.value * 100).toInt()}%") {
                settings.webTextScale.set(it)
                browser.applySettings()
            }
            SwitchRow("Address bar at the top", settings.barAtTop.value, { settings.barAtTop.set(it) })
        }

        SectionLabel("Start page")
        SettingsCard {
            SettingRow(
                "Wallpaper",
                if (settings.wallpaper.value.isBlank()) "None" else "Custom image",
                Icons.Default.Create,
                { picker.launch(arrayOf("image/*")) },
            )
            if (settings.wallpaper.value.isNotBlank()) {
                SliderRow("Dim", settings.wallpaperDim.value, 0f..0.85f, "${(settings.wallpaperDim.value * 100).toInt()}%") {
                    settings.wallpaperDim.set(it)
                }
                SliderRow("Blur", settings.wallpaperBlur.value, 0f..24f, "${settings.wallpaperBlur.value.toInt()}") {
                    settings.wallpaperBlur.set(it)
                }
                SettingRow("Remove wallpaper", null, null, { settings.wallpaper.set("") })
            }
            SwitchRow("Show bookmark shortcuts", settings.showShortcuts.value, { settings.showShortcuts.set(it) })
        }

        SectionLabel("Startup")
        SettingsCard {
            SwitchRow(
                "Startup animation",
                settings.splash.value,
                { settings.splash.set(it) },
                "Play the logo animation when the app opens",
            )
        }

        SectionLabel("App icon")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text("Pick the icon shown on your home screen", color = c.muted, fontSize = 13.sp)
                GapV(12)
                val all = listOf(-1) + (0 until 16).toList()
                all.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { index ->
                            val selected = settings.iconIndex.value == index
                            val base = if (index < 0) AppSettings.DEFAULT_ACCENT else AppIcons.colors[index].toInt()
                            val shown = readableAccent(Color(base), false)
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0A0B10))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.outline, CircleShape)
                                    .clickable {
                                        settings.iconIndex.set(index)
                                        AppIcons.apply(context, index)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                CrimsonLogo(shown, Modifier.size(30.dp))
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.size(44.dp)) }
                    }
                }
                GapV(6)
                Text(
                    if (settings.iconIndex.value < 0) "Crimson (default)" else AppIcons.names[settings.iconIndex.value],
                    color = c.onSurface,
                    fontSize = 14.sp,
                )
                Text(
                    "The icon can take a few seconds to change on your launcher.",
                    color = c.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        GapV(24)
    }
}

@Composable
fun SearchScreen(settings: AppSettings, onBack: () -> Unit) {
    ScreenScaffold("Search engine", onBack) {
        SettingsCard {
            SearchEngines.all.forEachIndexed { i, engine ->
                RadioRow(engine.name, null, settings.searchEngine.value == i) { settings.searchEngine.set(i) }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = LocalCrimson.current
    ScreenScaffold("About", onBack) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CrimsonLogo(c.accent, Modifier.size(110.dp))
            GapV(14)
            Text("Crimson", color = c.onSurface, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Version 0.1", color = c.muted, fontSize = 14.sp)
            GapV(18)
            Text(
                "A private, customizable browser built on the Gecko engine.",
                color = c.muted,
                fontSize = 14.sp,
            )
        }
    }
}
