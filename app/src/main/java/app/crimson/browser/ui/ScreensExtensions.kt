package app.crimson.browser.ui

import android.graphics.BitmapFactory
import android.os.Build
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.crimson.browser.data.AppSettings
import app.crimson.browser.engine.BrowserState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoView
import java.net.URL

private val iconCache = HashMap<String, ImageBitmap>()

@Composable
fun RemoteIcon(url: String, fallback: String, modifier: Modifier = Modifier) {
    val c = LocalCrimson.current
    val bitmap by produceState<ImageBitmap?>(iconCache[url], url) {
        if (value == null && url.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                try {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } catch (e: Exception) {
                    null
                }
            }
            value?.let { iconCache[url] = it }
        }
    }
    val shape = RoundedCornerShape(c.r(14))
    Box(modifier.clip(shape).background(c.accentBrush), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().padding(4.dp).background(androidx.compose.ui.graphics.Color.Transparent))
        } else {
            Text(fallback.take(1).uppercase(), color = c.onAccent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AddonsScreen(browser: BrowserState, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val c = LocalCrimson.current
    val ext = browser.extensions
    var query by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { ext.store.load("") }

    ScreenScaffold("Add-ons", onBack) {
        SectionLabel("Installed")
        if (ext.installed.isEmpty()) {
            Text("Nothing installed yet. Pick something below.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        } else {
            SettingsCard {
                ext.installed.forEach { e ->
                    val enabled = e.metaData?.enabled ?: true
                    val name = e.metaData?.name ?: e.id
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteIcon("", name, Modifier.size(42.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = c.onSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Version " + (e.metaData?.version ?: ""), color = c.muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { ext.setEnabled(e, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ext.actions.containsKey(e.id)) Chip("Open", false) { ext.clickAction(e) }
                        e.metaData?.optionsPageUrl?.takeIf { it.isNotBlank() }?.let { page ->
                            Chip("Settings", false) { onOpenUrl(page) }
                        }
                        Chip("Remove", false) { ext.uninstall(e) }
                    }
                    Divider()
                }
            }
        }
        if (ext.error.isNotBlank()) {
            Text(ext.error, color = c.accent, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
        if (ext.busy) Text("Installing...", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))

        SectionLabel("Add-on store")
        Row(
            Modifier.fillMaxWidth().glassPanel(RoundedCornerShape(c.r(22))).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (query.isEmpty()) Text("Search add-ons", color = c.muted, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { ext.store.load(query, force = true) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PillButton("Search") { ext.store.load(query, force = true) }
        }
        GapV(10)
        if (ext.store.loading) Text("Loading...", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
        if (ext.store.error.isNotBlank() && !ext.store.loading) {
            Text(ext.store.error, color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
        }
        ext.store.results.forEach { a ->
            val installed = ext.installed.any { (it.metaData?.name ?: "") == a.name }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).glassPanel(RoundedCornerShape(c.r(22))).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteIcon(a.iconUrl, a.name, Modifier.size(46.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.summary, color = c.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(formatUsers(a.users), color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.width(10.dp))
                if (installed) {
                    Text("Added", color = c.accent, fontSize = 13.sp)
                } else {
                    PillButton("Add") { ext.installFromUrl(a.xpiUrl) }
                }
            }
        }

        SectionLabel("Install from a link")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(c.r(16))).background(c.surfaceHigh).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (link.isEmpty()) Text("Link to an .xpi file", color = c.muted, fontSize = 14.sp)
                    BasicTextField(
                        value = link,
                        onValueChange = { link = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                PillButton("Install") {
                    ext.installFromUrl(link)
                    link = ""
                }
            }
        }
        GapV(24)
    }
}

private fun formatUsers(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM users", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.0fK users", n / 1_000.0)
    else -> "$n users"
}

@Composable
fun ExtensionsSheet(browser: BrowserState, onDismiss: () -> Unit, onManage: () -> Unit) {
    val c = LocalCrimson.current
    val ext = browser.extensions
    Sheet(onDismiss) {
        Text("Extensions", color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        GapV(10)
        if (ext.installed.isEmpty()) {
            Text("No extensions yet.", color = c.muted, fontSize = 14.sp)
        }
        ext.installed.forEach { e ->
            val name = e.metaData?.name ?: e.id
            val hasAction = ext.actions.containsKey(e.id)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .glassPanel(RoundedCornerShape(c.r(18)))
                    .clickableIf(hasAction) {
                        onDismiss()
                        ext.clickAction(e)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteIcon("", name, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = c.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (hasAction) "Tap to open" else "Runs in the background", color = c.muted, fontSize = 12.sp)
                }
            }
        }
        GapV(12)
        PillButton("Get more add-ons", Modifier.fillMaxWidth()) {
            onDismiss()
            onManage()
        }
    }
}

private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier.clickable(onClick = onClick)) else this

@Composable
fun PopupHost(browser: BrowserState) {
    val c = LocalCrimson.current
    val ext = browser.extensions
    val session = ext.popupSession ?: return
    Dialog(
        onDismissRequest = { ext.closePopup() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .background(c.surface, RoundedCornerShape(c.r(26)))
                .glassPanel(RoundedCornerShape(c.r(26))),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ext.popupTitle, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconTap(Icons.Default.Close) { ext.closePopup() }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(460.dp),
                factory = { ctx -> GeckoView(ctx).also { it.setSession(session) } },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalCrimson.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
fun DiagnosticsScreen(browser: BrowserState, settings: AppSettings, onBack: () -> Unit) {
    val ext = browser.extensions
    val webView = try {
        WebView.getCurrentWebViewPackage()?.let { it.packageName + " " + it.versionName } ?: "not found"
    } catch (e: Exception) {
        "unknown"
    }
    ScreenScaffold("Diagnostics", onBack) {
        Text(
            "If something does not work, take a screenshot of this page and send it.",
            color = LocalCrimson.current.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        SettingsCard {
            InfoRow("Built-in extension", ext.builtInStatus)
            InfoRow("Settings requests from it", ext.polls.toString())
            InfoRow("Video reports from it", ext.mediaReports.toString())
            InfoRow("Last video link", ext.lastMedia.ifBlank { "none" })
            InfoRow("Add-ons installed", ext.installed.size.toString())
            InfoRow("Tor", browser.tor.status + if (browser.tor.ready) " (ready)" else "")
            InfoRow("Tabs open", browser.tabs.size.toString())
            InfoRow("Current engine", if (browser.current?.isChromium == true) "Chromium (WebView)" else "Gecko")
            InfoRow("Downloads", browser.downloads.items.size.toString())
            InfoRow("Chromium engine", webView)
            InfoRow("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            InfoRow("Dark mode / dev panel", "${settings.forceDark.value} / ${settings.devMode.value}")
        }
        GapV(14)
        SettingsCard {
            SettingRow("Retry the built-in extension", null, null, { ext.retryBuiltIn() })
            SettingRow("Test the downloader", "Saves a small test file", null, {
                browser.downloads.enqueueUrl("https://www.w3.org/WAI/WCAG21/Techniques/pdf/img/table-word.jpg", "crimson-test.jpg")
                browser.toast?.invoke("Test download started")
            })
            SettingRow("Restart Tor", "Applies the Tor mode again", null, { browser.applySettings() })
        }
        GapV(24)
    }
}
