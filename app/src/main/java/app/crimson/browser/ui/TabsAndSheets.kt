package app.crimson.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.crimson.browser.data.AppSettings
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.engine.ContextInfo
import app.crimson.browser.engine.MediaItem

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Crimson", text))
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun TabSwitcher(
    browser: BrowserState,
    onClose: () -> Unit,
    onPickTab: () -> Unit,
) {
    val c = LocalCrimson.current
    var privateView by remember { mutableStateOf(browser.current?.isPrivate == true) }
    val shown = browser.tabs.filter { it.isPrivate == privateView }
    val normalCount = browser.tabs.count { !it.isPrivate }
    val privateCount = browser.tabs.count { it.isPrivate }

    Box(Modifier.fillMaxSize()) {
    AmbientBackground()
    if (privateView) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, onClick = onClose)
            Spacer(Modifier.weight(1f))
            Chip("Tabs $normalCount", !privateView) { privateView = false }
            Spacer(Modifier.width(8.dp))
            Chip("Private $privateCount", privateView) { privateView = true }
            Spacer(Modifier.weight(1f))
            IconTap(Icons.Default.Delete) { browser.closeAll(privateView) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (privateView) "No private tabs" else "No tabs",
                        color = c.muted,
                        fontSize = 16.sp,
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { tab ->
                    val selected = tab.id == browser.currentId
                    val shape = RoundedCornerShape(c.r(22))
                    Column(
                        Modifier
                            .glassPanel(shape)
                            .border(if (selected) 2.dp else 0.dp, if (selected) c.accent else Color.Transparent, shape)
                            .clickable {
                                browser.selectTab(tab.id)
                                onPickTab()
                            },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (tab.title.isNotBlank()) tab.title else if (tab.url.isEmpty()) "New tab" else displayUrl(tab.url),
                                color = c.onSurface,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconTap(Icons.Default.Close) { browser.closeTab(tab.id) }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .background(c.surfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            val thumb = tab.thumbnail
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                CrimsonLogo(c.logo.copy(alpha = 0.6f), Modifier.size(56.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            PillButton(if (privateView) "New private tab" else "New tab") {
                browser.newTab(null, private = privateView)
                onPickTab()
            }
        }
    }
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    return Color(
        red = this.red * a + background.red * (1 - a),
        green = this.green * a + background.green * (1 - a),
        blue = this.blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

class MenuAction(val icon: ImageVector, val label: String, val active: Boolean = false, val onClick: () -> Unit)

@Composable
fun MenuSheet(
    browser: BrowserState,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onFind: () -> Unit,
    onQuit: () -> Unit,
) {
    val c = LocalCrimson.current
    val context = LocalContext.current
    val tab = browser.current
    val bookmarked = tab != null && browser.stores.bookmarks.contains(tab.url)

    val actions = listOf(
        MenuAction(Icons.Default.Add, "New tab") { browser.newTab(null); onDismiss() },
        MenuAction(Icons.Default.Face, "Private tab") { browser.newTab(null, private = true); onDismiss() },
        MenuAction(Icons.Default.Star, if (bookmarked) "Bookmarked" else "Bookmark", bookmarked) {
            if (tab != null && tab.url.isNotBlank()) {
                browser.stores.bookmarks.toggle(tab.url, tab.title)
            }
            onDismiss()
        },
        MenuAction(Icons.Default.Share, "Share") {
            tab?.takeIf { it.url.isNotBlank() }?.let { shareText(context, it.url) }
            onDismiss()
        },
        MenuAction(Icons.Default.Search, "Find in page") { onFind(); onDismiss() },
        MenuAction(Icons.Default.Home, "Desktop site", tab?.desktop == true) {
            tab?.let { browser.toggleDesktop(it) }
            onDismiss()
        },
        MenuAction(Icons.Default.Info, "Dark web pages", settings.forceDark.value) {
            settings.forceDark.set(!settings.forceDark.value)
        },
        MenuAction(Icons.Default.Build, "Dev panel", settings.devMode.value) {
            tab?.let { browser.toggleDevPanel(it) }
            onDismiss()
        },
        MenuAction(
            Icons.Default.Refresh,
            if (tab?.isChromium == true) "Use Gecko" else "Use Chromium",
        ) {
            tab?.let { browser.switchEngine(it) }
            onDismiss()
        },
        MenuAction(Icons.Default.Search, "Find videos") {
            tab?.let { browser.scanForVideos(it) }
            onDismiss()
        },
        MenuAction(Icons.Default.Info, "View source") {
            tab?.takeIf { it.url.isNotBlank() }?.let { browser.newTab("view-source:" + it.url, engine = 0) }
            onDismiss()
        },
        MenuAction(Icons.Default.Face, "Extensions") {
            browser.extensions.sheetOpen = true
            onDismiss()
        },
        MenuAction(Icons.Default.Star, "Bookmarks") { onNavigate(Screen.Bookmarks) },
        MenuAction(Icons.Default.Info, "History") { onNavigate(Screen.History) },
        MenuAction(Icons.Default.Add, "Downloads") { onNavigate(Screen.Downloads) },
        MenuAction(Icons.Default.Build, "Add-ons") { onNavigate(Screen.Addons) },
        MenuAction(Icons.Default.Warning, "Privacy Guard") { onNavigate(Screen.PrivacyGuard) },
        MenuAction(Icons.Default.Settings, "Settings") { onNavigate(Screen.Settings) },
        MenuAction(Icons.Default.Delete, "Clear data") { onNavigate(Screen.PrivacyAdvanced) },
        MenuAction(Icons.Default.Close, "Quit") { onQuit() },
    )

    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CrimsonLogo(c.logo, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("Crimson", color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { a -> MenuTile(a, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MenuTile(action: MenuAction, modifier: Modifier) {
    val c = LocalCrimson.current
    Column(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(c.r(18)))
            .clickable(onClick = action.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(c.r(17)))
                .then(if (action.active) Modifier.background(c.accentBrush) else Modifier.glassPanel(RoundedCornerShape(c.r(17)), 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                tint = if (action.active) c.onAccent else c.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            action.label,
            color = c.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Brush2(c: CrimsonColors): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.SolidColor(c.surfaceHigh)

@Composable
fun MediaSheet(
    browser: BrowserState,
    items: List<MediaItem>,
    pageUrl: String,
    onDismiss: () -> Unit,
) {
    val c = LocalCrimson.current
    Sheet(onDismiss) {
        Text("Download", color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Found on this page", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        items.forEach { item ->
            val kind = when {
                item.url.substringBefore("?").lowercase().endsWith(".m3u8") || item.mime.contains("mpegurl") -> "Stream (HLS)"
                item.url.substringBefore("?").lowercase().endsWith(".mpd") || item.mime.contains("dash") -> "Stream (DASH)"
                item.mime.startsWith("audio") -> "Audio"
                else -> "Video"
            }
            val dash = kind == "Stream (DASH)"
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(c.r(18)))
                    .background(c.surfaceHigh)
                    .clickable(enabled = !dash) {
                        browser.downloads.enqueueUrl(item.url, referer = pageUrl, mimeHint = item.mime)
                        browser.toast?.invoke("Download started")
                        onDismiss()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        android.net.Uri.parse(item.url).lastPathSegment ?: item.url,
                        color = c.onSurface,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        kind + if (item.size > 0) " \u00B7 " + formatBytes(item.size) else "" + if (dash) " \u00B7 not supported yet" else "",
                        color = c.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

@Composable
fun ContextSheet(browser: BrowserState, info: ContextInfo, onDismiss: () -> Unit) {
    val c = LocalCrimson.current
    val context = LocalContext.current
    val tabPrivate = browser.current?.isPrivate == true
    Sheet(onDismiss) {
        Text(
            info.linkUri ?: info.srcUri ?: "",
            color = c.muted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        info.linkUri?.takeIf { it.isNotBlank() }?.let { link ->
            ContextRow("Open in new tab") { browser.newTab(link, private = tabPrivate); onDismiss() }
            ContextRow("Open in private tab") { browser.newTab(link, private = true); onDismiss() }
            ContextRow("Copy link") { copyText(context, link); onDismiss() }
            ContextRow("Share link") { shareText(context, link); onDismiss() }
        }
        info.srcUri?.takeIf { it.isNotBlank() }?.let { src ->
            if (info.isImage || info.isVideoOrAudio) {
                ContextRow(if (info.isImage) "Download image" else "Download media") {
                    browser.downloads.enqueueUrl(src, referer = browser.current?.url)
                    browser.toast?.invoke("Download started")
                    onDismiss()
                }
                ContextRow("Copy address") { copyText(context, src); onDismiss() }
            }
        }
    }
}

@Composable
private fun ContextRow(text: String, onClick: () -> Unit) {
    val c = LocalCrimson.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(c.r(16)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(text, color = c.onSurface, fontSize = 16.sp)
    }
}
