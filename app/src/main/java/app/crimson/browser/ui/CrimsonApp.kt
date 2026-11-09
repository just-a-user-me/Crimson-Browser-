package app.crimson.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.engine.Tab
import org.mozilla.geckoview.GeckoView

private enum class Panel { None, Menu, Tabs }

@Composable
fun CrimsonApp(browser: BrowserState) {
    val context = LocalContext.current
    var paletteIndex by remember { mutableIntStateOf(Prefs.paletteIndex(context)) }
    var splashOn by remember { mutableStateOf(Prefs.splash(context)) }
    var showSplash by remember { mutableStateOf(Prefs.splash(context)) }
    var panel by remember { mutableStateOf(Panel.None) }
    val accent = Palette.accent(paletteIndex)

    CrimsonTheme(accent) {
        val tab = browser.current

        BackHandler(enabled = panel == Panel.None && tab?.canGoBack == true) {
            tab?.session?.goBack()
        }
        BackHandler(enabled = panel != Panel.None) { panel = Panel.None }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    if (tab != null) {
                        GeckoViewHost(tab, Modifier.fillMaxSize())
                        if (tab.url.isEmpty()) {
                            StartPage(accent, Modifier.fillMaxSize())
                        }
                    }
                }
                if (tab != null) {
                    BottomBar(
                        tab = tab,
                        tabCount = browser.tabs.size,
                        accent = accent,
                        onTabs = { panel = Panel.Tabs },
                        onMenu = { panel = Panel.Menu },
                        onSubmit = { browser.open(it) },
                    )
                }
            }

            if (panel != Panel.None) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { panel = Panel.None },
                )
                Box(Modifier.align(Alignment.BottomCenter)) {
                    when (panel) {
                        Panel.Menu -> MenuSheet(
                            paletteIndex = paletteIndex,
                            splashOn = splashOn,
                            onPick = {
                                paletteIndex = it
                                Prefs.setPaletteIndex(context, it)
                            },
                            onSplash = {
                                splashOn = it
                                Prefs.setSplash(context, it)
                            },
                            onNewTab = {
                                browser.newTab()
                                panel = Panel.None
                            },
                            onReload = {
                                browser.current?.session?.reload()
                                panel = Panel.None
                            },
                        )
                        Panel.Tabs -> TabsSheet(
                            browser = browser,
                            onClose = { panel = Panel.None },
                        )
                        Panel.None -> {}
                    }
                }
            }

            if (showSplash) {
                CrimsonSplash(accent) { showSplash = false }
            }
        }
    }
}

@Composable
private fun GeckoViewHost(tab: Tab, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> GeckoView(ctx) },
        update = { view ->
            if (view.session !== tab.session) {
                view.releaseSession()
                view.setSession(tab.session)
            }
        },
    )
}

@Composable
private fun StartPage(accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CrimsonLogo(accent, Modifier.size(120.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Crimson",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Search or type an address below",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun displayUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

@Composable
private fun BottomBar(
    tab: Tab,
    tabCount: Int,
    accent: Color,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember(tab.id) { mutableStateOf(displayUrl(tab.url)) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(tab.id, tab.url, focused) {
        if (!focused) text = displayUrl(tab.url)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (tab.loading) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accent,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton("\u2039", enabled = tab.canGoBack) { tab.session.goBack() }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty() && !focused) {
                    Text(
                        "Search or type address",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onGo = {
                        onSubmit(text)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            Spacer(Modifier.width(4.dp))
            BarButton(tabCount.toString(), enabled = true, onClick = onTabs)
            BarButton("\u22EE", enabled = true, onClick = onMenu)
        }
    }
}

@Composable
private fun BarButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 22.sp,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}

@Composable
private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

@Composable
private fun Swatch(index: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Palette.accent(index))
            .then(if (selected) Modifier.border(BorderStroke(3.dp, Color.White), CircleShape) else Modifier)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun MenuSheet(
    paletteIndex: Int,
    splashOn: Boolean,
    onPick: (Int) -> Unit,
    onSplash: (Boolean) -> Unit,
    onNewTab: () -> Unit,
    onReload: () -> Unit,
) {
    Sheet {
        Text("Crimson", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))
        Text("Theme color", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 8) {
                    val index = row * 8 + col
                    Swatch(index, index == paletteIndex, Modifier.weight(1f)) { onPick(index) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Startup animation", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = splashOn, onCheckedChange = onSplash)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("New tab", Modifier.weight(1f), onNewTab)
            ActionButton("Reload", Modifier.weight(1f), onReload)
        }
    }
}

@Composable
private fun TabsSheet(browser: BrowserState, onClose: () -> Unit) {
    Sheet {
        Text("Tabs", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        browser.tabs.forEachIndexed { index, tab ->
            val selected = index == browser.currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable {
                        browser.select(index)
                        onClose()
                    }
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(
                        if (tab.title.isNotEmpty()) tab.title else "New tab",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tab.url.isNotEmpty()) {
                        Text(
                            displayUrl(tab.url),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                BarButton("\u00D7", enabled = true) { browser.close(index) }
            }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton("New tab", Modifier.fillMaxWidth()) {
            browser.newTab()
            onClose()
        }
    }
}
