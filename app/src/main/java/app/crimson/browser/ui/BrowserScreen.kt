package app.crimson.browser.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.crimson.browser.data.AppSettings
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.engine.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoView

fun displayUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

@Composable
fun GeckoViewHost(browser: BrowserState, tab: Tab, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GeckoView(ctx).also { browser.geckoView = it }
        },
        update = { view ->
            browser.geckoView = view
            if (view.session !== tab.session) {
                view.setSession(tab.session)
            }
        },
    )
}

@Composable
fun BrowserScreen(
    browser: BrowserState,
    settings: AppSettings,
    findOpen: Boolean,
    onCloseFind: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onShowMedia: () -> Unit,
) {
    val c = LocalCrimson.current
    val tab = browser.current
    var typed by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val top = settings.barAtTop.value
    val fullscreen = browser.fullscreen

    Column(Modifier.fillMaxSize().background(c.bg)) {
        if (top && !fullscreen && tab != null) {
            AddressBar(browser, tab, settings, true, onTabs, onMenu) { text, focused ->
                typed = text
                editing = focused
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (!top && !fullscreen) Modifier.statusBarsPadding() else Modifier),
        ) {
            if (tab != null) {
                GeckoViewHost(browser, tab, Modifier.fillMaxSize())
                if (tab.url.isEmpty()) {
                    StartPage(browser, settings, Modifier.fillMaxSize())
                }
                if (tab.loading && !fullscreen) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth(tab.progress / 100f)
                            .height(3.dp)
                            .background(c.accentBrush),
                    )
                }

                val mediaItems = browser.media.itemsFor(tab.url)
                if (settings.mediaButton.value && mediaItems.isNotEmpty() && !fullscreen) {
                    MediaPill(
                        count = mediaItems.size,
                        alpha = settings.mediaButtonOpacity.value,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp),
                        onClick = onShowMedia,
                    )
                }

                if (findOpen) {
                    FindBar(
                        browser = browser,
                        tab = tab,
                        onClose = onCloseFind,
                        modifier = Modifier.align(if (top) Alignment.BottomCenter else Alignment.TopCenter),
                    )
                }
            }

            if (editing && typed.isNotBlank()) {
                Suggestions(
                    browser = browser,
                    query = typed,
                    modifier = Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter),
                )
            }
        }
        if (!top && !fullscreen && tab != null) {
            AddressBar(browser, tab, settings, false, onTabs, onMenu) { text, focused ->
                typed = text
                editing = focused
            }
        }
    }
}

@Composable
private fun MediaPill(count: Int, alpha: Float, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalCrimson.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(c.surface.copy(alpha = alpha * 0.9f))
            .border(1.dp, c.accent.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.2.dp.toPx()
            drawLine(c.accent, androidx.compose.ui.geometry.Offset(w / 2, 1f), androidx.compose.ui.geometry.Offset(w / 2, h * 0.68f), stroke)
            drawLine(c.accent, androidx.compose.ui.geometry.Offset(w * 0.26f, h * 0.46f), androidx.compose.ui.geometry.Offset(w / 2, h * 0.7f), stroke)
            drawLine(c.accent, androidx.compose.ui.geometry.Offset(w * 0.74f, h * 0.46f), androidx.compose.ui.geometry.Offset(w / 2, h * 0.7f), stroke)
            drawLine(c.accent, androidx.compose.ui.geometry.Offset(w * 0.12f, h - 1f), androidx.compose.ui.geometry.Offset(w * 0.88f, h - 1f), stroke)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (count > 1) "Download ($count)" else "Download",
            color = c.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AddressBar(
    browser: BrowserState,
    tab: Tab,
    settings: AppSettings,
    atTop: Boolean,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onEditing: (String, Boolean) -> Unit,
) {
    val c = LocalCrimson.current
    val focusManager = LocalFocusManager.current
    var field by remember(tab.id) { mutableStateOf(TextFieldValue(displayUrl(tab.url))) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(tab.id, tab.url, focused) {
        if (!focused) field = TextFieldValue(displayUrl(tab.url))
    }

    val tabCount = browser.tabs.count { it.isPrivate == tab.isPrivate }
    val barShape = RoundedCornerShape(c.r(26))

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg)
            .then(if (atTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding().imePadding())
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(c.surface)
                .border(1.dp, if (focused) c.accent.copy(alpha = 0.7f) else c.outline, barShape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(Icons.AutoMirrored.Filled.ArrowBack, enabled = tab.canGoBack) { tab.session.goBack() }
            Row(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(c.r(21)))
                    .background(c.surfaceHigh)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (tab.url.isEmpty()) Icons.Default.Search else if (tab.secure) Icons.Default.Lock else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (tab.url.isNotEmpty() && !tab.secure) Color(0xFFFFB400) else c.muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty() && !focused) {
                        Text("Search or type address", color = c.muted, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            onEditing(it.text, true)
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onGo = {
                            browser.open(field.text)
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) {
                                    field = field.copy(selection = TextRange(0, field.text.length))
                                }
                                onEditing(field.text, it.isFocused)
                            },
                    )
                }
                if (focused && field.text.isNotEmpty()) {
                    IconTapSmall(Icons.Default.Close) { field = TextFieldValue("") ; onEditing("", true) }
                } else if (tab.url.isNotEmpty()) {
                    IconTapSmall(if (tab.loading) Icons.Default.Close else Icons.Default.Refresh) {
                        if (tab.loading) tab.session.stop() else tab.session.reload()
                    }
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(c.r(11)))
                    .border(2.dp, if (tab.isPrivate) c.accent else c.onSurface.copy(alpha = 0.8f), RoundedCornerShape(c.r(11)))
                    .clickable(onClick = onTabs),
                contentAlignment = Alignment.Center,
            ) {
                Text(tabCount.toString(), color = c.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            IconTap(Icons.Default.MoreVert, onClick = onMenu)
        }
    }
}

@Composable
private fun IconTapSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalCrimson.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Suggestions(browser: BrowserState, query: String, modifier: Modifier) {
    val c = LocalCrimson.current
    val q = query.trim().lowercase()
    val matches = remember(q, browser.stores.history.items.size, browser.stores.bookmarks.items.size) {
        val fromBookmarks = browser.stores.bookmarks.items.filter {
            it.url.lowercase().contains(q) || it.title.lowercase().contains(q)
        }.map { it.url to it.title }
        val fromHistory = browser.stores.history.items.filter {
            it.url.lowercase().contains(q) || it.title.lowercase().contains(q)
        }.map { it.url to it.title }
        (fromBookmarks + fromHistory).distinctBy { it.first }.take(5)
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(c.r(22)))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(c.r(22))),
    ) {
        SuggestionRow("Search for \"$query\"", null, true) { browser.open(query) }
        matches.forEach { (url, title) ->
            SuggestionRow(if (title.isNotBlank()) title else displayUrl(url), displayUrl(url), false) { browser.open(url) }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, subtitle: String?, search: Boolean, onClick: () -> Unit) {
    val c = LocalCrimson.current
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
                focus.clearFocus()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (search) Icons.Default.Search else Icons.Default.Lock,
            contentDescription = null,
            tint = if (search) c.accent else c.muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = c.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FindBar(browser: BrowserState, tab: Tab, onClose: () -> Unit, modifier: Modifier) {
    val c = LocalCrimson.current
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    fun run(backwards: Boolean) {
        if (text.isBlank()) {
            tab.session.finder.clear()
            info = ""
            return
        }
        val flags = if (backwards) org.mozilla.geckoview.GeckoSession.FINDER_FIND_BACKWARDS else 0
        tab.session.finder.find(text, flags).accept(
            { r -> info = if (r == null || r.total == 0) "No matches" else "${r.current}/${r.total}" },
            { },
        )
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(c.r(22)))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(c.r(22)))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (text.isEmpty()) Text("Find in page", color = c.muted, fontSize = 15.sp)
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    run(false)
                },
                singleLine = true,
                textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(false) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(info, color = c.muted, fontSize = 13.sp)
        IconTap(Icons.Default.KeyboardArrowUp) { run(true) }
        IconTap(Icons.Default.KeyboardArrowDown) { run(false) }
        IconTap(Icons.Default.Close) {
            tab.session.finder.clear()
            onClose()
        }
    }
}

@Composable
fun StartPage(browser: BrowserState, settings: AppSettings, modifier: Modifier = Modifier) {
    val c = LocalCrimson.current
    val context = LocalContext.current
    val wallpaperUri = settings.wallpaper.value
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, wallpaperUri) {
        value = if (wallpaperUri.isBlank()) null else withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                context.contentResolver.openInputStream(Uri.parse(wallpaperUri))?.use {
                    BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(modifier.background(c.bg)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(settings.wallpaperBlur.value.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.wallpaperDim.value)))
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(c.accent.copy(alpha = 0.18f), Color.Transparent),
                        ),
                    ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CrimsonLogo(c.accent, Modifier.size(104.dp))
            Spacer(Modifier.height(14.dp))
            Text("Crimson", color = if (bitmap != null) Color.White else c.onSurface, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))

            val shape = RoundedCornerShape(c.r(26))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.surface.copy(alpha = if (bitmap != null) 0.78f else 1f))
                    .border(1.dp, c.outline, shape)
                    .clickable { /* the address bar handles typing */ }
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Search or type an address below", color = c.muted, fontSize = 15.sp)
            }

            if (settings.showShortcuts.value && browser.stores.bookmarks.items.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                val items = browser.stores.bookmarks.items.take(8)
                items.chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowItems.forEach { b ->
                            Column(
                                Modifier
                                    .width(76.dp)
                                    .clip(RoundedCornerShape(c.r(16)))
                                    .clickable { browser.open(b.url) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(c.r(18)))
                                        .background(c.accentBrush),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (displayUrl(b.url).firstOrNull() ?: 'C').uppercase(),
                                        color = c.onAccent,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (b.title.isNotBlank()) b.title else displayUrl(b.url),
                                    color = if (bitmap != null) Color.White else c.onSurface,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
