package app.crimson.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.crimson.browser.data.AppSettings
import app.crimson.browser.engine.BrowserState
import org.mozilla.geckoview.ContentBlocking

@Composable
fun PrivacyGuardScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit, onNavigate: (Screen) -> Unit) {
    val levelText = when (settings.trackingLevel.value) {
        0 -> "Off"
        1 -> "Standard"
        else -> "Strict"
    }
    ScreenScaffold("Privacy Guard", onBack) {
        SectionLabel("Privacy control")
        SettingsCard {
            SettingRow("Ads & Trackers Blocker", levelText, Icons.Default.Warning, { onNavigate(Screen.Trackers) })
            SettingRow("Always Use HTTPS", if (settings.httpsOnly.value) "On" else "Off", Icons.Default.Lock, { onNavigate(Screen.Https) })
            SettingRow("Remove Fingerprint", if (settings.fingerprintProtection.value) "On" else "Off", Icons.Default.Face, { onNavigate(Screen.Fingerprint) })
            SettingRow("Script Control", if (settings.jsEnabled.value) "Scripts allowed" else "Scripts blocked", Icons.Default.Build, { onNavigate(Screen.Scripts) })
            SettingRow("Cookie Management", cookieLabel(settings.cookieBehavior.value), Icons.Default.Person, { onNavigate(Screen.Cookies) })
            SettingRow("Stop App Redirects", if (settings.blockAppRedirects.value) "On" else "Off", Icons.Default.Close, { onNavigate(Screen.Redirects) })
            SettingRow("Site Settings", null, Icons.Default.Home, { onNavigate(Screen.SiteSettings) })
        }
        GapV(14)
        SettingsCard {
            SettingRow("Advanced", "Clear on exit, Tor, secure DNS, developer tools", Icons.Default.Settings, { onNavigate(Screen.PrivacyAdvanced) })
        }
        GapV(24)
    }
}

private fun cookieLabel(v: Int): String = when (v) {
    ContentBlocking.CookieBehavior.ACCEPT_ALL -> "Allow all"
    ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY -> "Block third-party"
    ContentBlocking.CookieBehavior.ACCEPT_NONE -> "Block all"
    ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS -> "Block trackers"
    else -> "Isolate third-party"
}

@Composable
fun TrackersScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit, onNavigate: (Screen) -> Unit) {
    val apply = { browser.applySettings() }
    ScreenScaffold("Ads & Trackers", onBack) {
        SectionLabel("Protection level")
        SettingsCard {
            RadioRow("Off", "No blocking", settings.trackingLevel.value == 0) { settings.trackingLevel.set(0); apply() }
            RadioRow("Standard", "Blocks common trackers", settings.trackingLevel.value == 1) { settings.trackingLevel.set(1); apply() }
            RadioRow("Strict", "Blocks the most, a few sites may break", settings.trackingLevel.value == 2) { settings.trackingLevel.set(2); apply() }
        }
        if (settings.trackingLevel.value > 0) {
            SectionLabel("What to block")
            SettingsCard {
                SwitchRow("Ad trackers", settings.blockAds.value, { settings.blockAds.set(it); apply() })
                SwitchRow("Analytics", settings.blockAnalytics.value, { settings.blockAnalytics.set(it); apply() })
                SwitchRow("Social trackers", settings.blockSocial.value, { settings.blockSocial.set(it); apply() })
                SwitchRow("Cryptominers", settings.blockCrypto.value, { settings.blockCrypto.set(it); apply() })
                SwitchRow("Fingerprinters", settings.blockFingerprinters.value, { settings.blockFingerprinters.set(it); apply() })
                SwitchRow("Tracking content", settings.blockContentTrackers.value, { settings.blockContentTrackers.set(it); apply() })
            }
        }
        SectionLabel("More")
        SettingsCard {
            SwitchRow("Dangerous site warnings", settings.safeBrowsing.value, { settings.safeBrowsing.set(it); apply() }, "Warn about phishing and malware")
            SwitchRow("Remove tracking from links", settings.queryStripping.value, { settings.queryStripping.set(it); apply() }, "Strips tracking parameters from addresses")
            SwitchRow("Block pop-ups", settings.blockPopups.value, { settings.blockPopups.set(it) })
        }
        SectionLabel("Stronger blocking")
        SettingsCard {
            SettingRow(
                "uBlock Origin",
                "Install the best content blocker from Add-ons",
                Icons.Default.Add,
                { onNavigate(Screen.Addons) },
            )
        }
        GapV(24)
    }
}

@Composable
fun HttpsScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    ScreenScaffold("Always Use HTTPS", onBack) {
        SettingsCard {
            SwitchRow(
                "HTTPS-only mode",
                settings.httpsOnly.value,
                { settings.httpsOnly.set(it); browser.applySettings() },
                "Upgrades every connection to HTTPS and warns before loading an insecure page",
                Icons.Default.Lock,
            )
        }
    }
}

@Composable
fun FingerprintScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    ScreenScaffold("Remove Fingerprint", onBack) {
        SettingsCard {
            SwitchRow(
                "Resist fingerprinting",
                settings.fingerprintProtection.value,
                { settings.fingerprintProtection.set(it); browser.applySettings() },
                "Makes your browser look the same as many others so sites cannot single you out",
                Icons.Default.Face,
            )
            SwitchRow(
                "Send Global Privacy Control",
                settings.globalPrivacyControl.value,
                { settings.globalPrivacyControl.set(it); browser.applySettings() },
                "Tells sites not to sell or share your data",
            )
        }
        GapV(12)
        Text(
            "Some websites may look slightly different, for example showing times in UTC.",
            color = LocalCrimson.current.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
fun ScriptsScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    val c = LocalCrimson.current
    var newSite by remember { mutableStateOf("") }
    ScreenScaffold("Script Control", onBack) {
        SettingsCard {
            SwitchRow(
                "Allow JavaScript",
                settings.jsEnabled.value,
                { settings.jsEnabled.set(it) },
                "Turn off to block scripts on every site. Pages may not work without them.",
                Icons.Default.Build,
            )
        }
        SectionLabel("Block scripts on specific sites")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(c.r(16)))
                        .background(c.surfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (newSite.isEmpty()) Text("example.com", color = c.muted, fontSize = 15.sp)
                    BasicTextField(
                        value = newSite,
                        onValueChange = { newSite = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                PillButton("Add") {
                    val host = newSite.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
                    if (host.isNotBlank()) {
                        settings.setSiteJsBlocked(host, true)
                        newSite = ""
                    }
                }
            }
            settings.blockedSites().forEach { site ->
                SettingRow(
                    site,
                    null,
                    null,
                    null,
                    trailing = {
                        IconTap(Icons.Default.Delete) { settings.setSiteJsBlocked(site, false) }
                    },
                )
            }
        }
        GapV(24)
    }
}

@Composable
fun CookiesScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    val apply = { browser.applySettings() }
    ScreenScaffold("Cookie Management", onBack) {
        SectionLabel("Cookies")
        SettingsCard {
            RadioRow("Allow all", null, settings.cookieBehavior.value == ContentBlocking.CookieBehavior.ACCEPT_ALL) {
                settings.cookieBehavior.set(ContentBlocking.CookieBehavior.ACCEPT_ALL); apply()
            }
            RadioRow("Block cross-site trackers", null, settings.cookieBehavior.value == ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS) {
                settings.cookieBehavior.set(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS); apply()
            }
            RadioRow("Isolate third-party cookies", "Recommended", settings.cookieBehavior.value == ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS) {
                settings.cookieBehavior.set(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS); apply()
            }
            RadioRow("Block all third-party cookies", null, settings.cookieBehavior.value == ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY) {
                settings.cookieBehavior.set(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY); apply()
            }
            RadioRow("Block all cookies", "Many sites will not work", settings.cookieBehavior.value == ContentBlocking.CookieBehavior.ACCEPT_NONE) {
                settings.cookieBehavior.set(ContentBlocking.CookieBehavior.ACCEPT_NONE); apply()
            }
        }
        SectionLabel("Delete")
        SettingsCard {
            SettingRow(
                "Delete all cookies now",
                "Also signs you out of websites",
                Icons.Default.Delete,
                {
                    browser.clearData(cookies = true, cache = false, siteData = true, history = false)
                    browser.toast?.invoke("Cookies deleted")
                },
            )
        }
        GapV(24)
    }
}

@Composable
fun RedirectsScreen(settings: AppSettings, onBack: () -> Unit) {
    ScreenScaffold("Stop App Redirects", onBack) {
        SettingsCard {
            SwitchRow(
                "Block app redirects",
                settings.blockAppRedirects.value,
                { settings.blockAppRedirects.set(it) },
                "Stops websites from opening other apps or the Play Store without asking",
                Icons.Default.Close,
            )
            SwitchRow(
                "Block pop-ups",
                settings.blockPopups.value,
                { settings.blockPopups.set(it) },
                "Stops pages from opening new windows by themselves",
            )
        }
    }
}

@Composable
fun SiteSettingsScreen(browser: BrowserState, onBack: () -> Unit) {
    ScreenScaffold("Site Settings", onBack) {
        SettingsCard {
            SettingRow(
                "Reset site permissions",
                "Forget every location, camera and notification choice",
                Icons.Default.Home,
                {
                    browser.runtime.storageController.clearData(org.mozilla.geckoview.StorageController.ClearFlags.PERMISSIONS)
                    browser.toast?.invoke("Site permissions reset")
                },
            )
            SettingRow(
                "Reset site settings",
                "Zoom levels and other per-site choices",
                Icons.Default.Settings,
                {
                    browser.runtime.storageController.clearData(org.mozilla.geckoview.StorageController.ClearFlags.SITE_SETTINGS)
                    browser.toast?.invoke("Site settings reset")
                },
            )
        }
    }
}

@Composable
fun PrivacyAdvancedScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    val c = LocalCrimson.current
    var confirmWipe by remember { mutableStateOf(false) }
    ScreenScaffold("Advanced", onBack) {
        SectionLabel("Delete everything")
        SettingsCard {
            SettingRow(
                if (confirmWipe) "Tap again to delete everything" else "Delete all browsing data now",
                "Cookies, history, cache, site data and open tabs",
                Icons.Default.Delete,
                {
                    if (confirmWipe) {
                        browser.wipeEverything()
                        confirmWipe = false
                        browser.toast?.invoke("Everything deleted")
                    } else {
                        confirmWipe = true
                    }
                },
            )
        }

        SectionLabel("Clear when Crimson closes")
        SettingsCard {
            SwitchRow("Clear data on exit", settings.clearOnExit.value, { settings.clearOnExit.set(it) }, "Runs when you choose Quit from the menu")
            if (settings.clearOnExit.value) {
                SwitchRow("Cookies", settings.clearCookies.value, { settings.clearCookies.set(it) })
                SwitchRow("Cache", settings.clearCache.value, { settings.clearCache.set(it) })
                SwitchRow("History", settings.clearHistory.value, { settings.clearHistory.set(it) })
                SwitchRow("Site data", settings.clearSiteData.value, { settings.clearSiteData.set(it) })
                SwitchRow("Download list", settings.clearDownloadsList.value, { settings.clearDownloadsList.set(it) })
            }
        }

        SectionLabel("Tor and .onion sites")
        SettingsCard {
            RadioRow("Off", null, settings.torMode.value == 0) { settings.torMode.set(0); browser.applySettings() }
            RadioRow(".onion sites only", "Opens onion links through Tor, everything else is direct", settings.torMode.value == 1) {
                settings.torMode.set(1); browser.applySettings()
            }
            RadioRow("All traffic", "Sends everything through Tor (slower)", settings.torMode.value == 2) {
                settings.torMode.set(2); browser.applySettings()
            }
            if (settings.torMode.value > 0) {
                SettingRow("Tor status", browser.tor.status, Icons.Default.Info)
            }
        }

        SectionLabel("Network")
        SettingsCard {
            SwitchRow(
                "Secure DNS",
                settings.secureDns.value,
                { settings.secureDns.set(it); browser.applySettings() },
                "Encrypts the lookups that find websites",
            )
        }

        SectionLabel("Web pages")
        SettingsCard {
            SwitchRow(
                "Dark mode for websites",
                settings.forceDark.value,
                { settings.forceDark.set(it) },
                "Darkens pages that do not have a dark theme",
            )
        }

        SectionLabel("Developer")
        SettingsCard {
            SwitchRow(
                "Developer panel",
                settings.devMode.value,
                { settings.devMode.set(it) },
                "Console, element inspector, network and storage inside pages",
                Icons.Default.Build,
            )
            SwitchRow(
                "Remote debugging",
                settings.remoteDebugging.value,
                { settings.remoteDebugging.set(it); browser.applySettings() },
                "Lets you inspect pages from a computer over USB",
            )
        }
        GapV(24)
    }
}
