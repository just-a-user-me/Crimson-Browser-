package app.crimson.browser.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.engine.DownloadState

@Composable
fun DownloadsScreen(browser: BrowserState, onBack: () -> Unit) {
    val c = LocalCrimson.current
    val context = LocalContext.current
    val list = browser.downloads.items
    ScreenScaffold(
        "Downloads",
        onBack,
        scroll = false,
        actions = { IconTap(Icons.Default.Delete) { browser.downloads.clearList() } },
    ) {
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nothing downloaded yet", color = c.muted, fontSize = 16.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { item ->
                    val shape = RoundedCornerShape(c.r(22))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(c.surface)
                            .border(1.dp, c.outline, shape)
                            .padding(16.dp),
                    ) {
                        Text(item.name, color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val status = when (item.state) {
                            DownloadState.Running -> if (item.total > 0) "${formatBytes(item.downloaded).takeIf { item.mime != "video/mp2t" } ?: "${item.downloaded}"} / ${if (item.mime == "video/mp2t") item.total.toString() + " parts" else formatBytes(item.total)}" else formatBytes(item.downloaded)
                            DownloadState.Done -> "Saved to Downloads/Crimson"
                            DownloadState.Failed -> item.message
                            DownloadState.Cancelled -> "Cancelled"
                        }
                        Text(status, color = if (item.state == DownloadState.Failed) c.accent else c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        if (item.state == DownloadState.Running && item.total > 0) {
                            Box(
                                Modifier
                                    .padding(top = 10.dp)
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(c.surfaceHigh),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((item.downloaded.toFloat() / item.total).coerceIn(0f, 1f))
                                        .height(5.dp)
                                        .background(c.accentBrush),
                                )
                            }
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (item.state) {
                                DownloadState.Running -> Chip("Cancel", false) { browser.downloads.cancel(item) }
                                DownloadState.Done -> {
                                    Chip("Open", false) {
                                        item.savedUri?.let { uri ->
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime)
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            } catch (_: Exception) {
                                                browser.toast?.invoke("No app can open this file")
                                            }
                                        }
                                    }
                                    Chip("Share", false) {
                                        item.savedUri?.let { uri ->
                                            val send = Intent(Intent.ACTION_SEND).setType(item.mime)
                                                .putExtra(Intent.EXTRA_STREAM, uri)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                    Chip("Remove", false) { browser.downloads.remove(item) }
                                }
                                else -> Chip("Remove", false) { browser.downloads.remove(item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(browser: BrowserState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalCrimson.current
    val items = browser.stores.history.items
    ScreenScaffold(
        "History",
        onBack,
        scroll = false,
        actions = { IconTap(Icons.Default.Delete) { browser.stores.history.clear() } },
    ) {
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No history", color = c.muted, fontSize = 16.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(items.toList(), key = { it.url + it.time }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(c.r(16)))
                            .clickable { onOpen(entry.url) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(
                                if (entry.title.isNotBlank()) entry.title else displayUrl(entry.url),
                                color = c.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(displayUrl(entry.url), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconTap(Icons.Default.Close) { browser.stores.history.remove(entry) }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksScreen(browser: BrowserState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalCrimson.current
    val items = browser.stores.bookmarks.items
    ScreenScaffold("Bookmarks", onBack, scroll = false) {
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No bookmarks yet. Use the star in the menu.", color = c.muted, fontSize = 15.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(items.toList(), key = { it.url }) { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(c.r(16)))
                            .clickable { onOpen(b.url) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(
                                if (b.title.isNotBlank()) b.title else displayUrl(b.url),
                                color = c.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(displayUrl(b.url), color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconTap(Icons.Default.Delete) { browser.stores.bookmarks.remove(b.url) }
                    }
                }
            }
        }
    }
}

@Composable
fun AddonsScreen(browser: BrowserState, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val c = LocalCrimson.current
    val ext = browser.extensions
    var url by remember { mutableStateOf("") }
    ScreenScaffold("Add-ons", onBack) {
        SectionLabel("Recommended")
        SettingsCard {
            SettingRow(
                "uBlock Origin",
                "Blocks ads and trackers on every page",
                Icons.Default.Add,
                { ext.installUblock() },
            )
            SettingRow(
                "Browse add-ons",
                "Open addons.mozilla.org and tap Add to Firefox",
                Icons.Default.Build,
                { onOpenUrl("https://addons.mozilla.org/android/") },
            )
        }

        SectionLabel("Install from a link")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(c.r(16)))
                        .background(c.surfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (url.isEmpty()) Text("Link to an .xpi add-on file", color = c.muted, fontSize = 14.sp)
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                PillButton("Install") {
                    ext.installFromUrl(url)
                    url = ""
                }
            }
        }
        if (ext.busy) {
            Text("Installing...", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
        if (ext.error.isNotBlank()) {
            Text(ext.error, color = c.accent, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }

        SectionLabel("Installed")
        if (ext.installed.isEmpty()) {
            Text("No add-ons installed yet.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        } else {
            SettingsCard {
                ext.installed.forEach { e ->
                    val enabled = e.metaData?.enabled ?: true
                    SettingRow(
                        title = e.metaData?.name ?: e.id,
                        subtitle = "Version " + (e.metaData?.version ?: ""),
                        icon = null,
                        onClick = null,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { ext.setEnabled(e, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
                                )
                                IconTap(Icons.Default.Delete) { ext.uninstall(e) }
                            }
                        },
                    )
                    e.metaData?.optionsPageUrl?.takeIf { it.isNotBlank() }?.let { page ->
                        SettingRow("Open settings", null, Icons.Default.Share, { onOpenUrl(page) })
                    }
                }
            }
        }
        GapV(24)
    }
}
