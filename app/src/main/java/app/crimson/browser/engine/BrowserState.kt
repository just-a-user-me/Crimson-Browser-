package app.crimson.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.crimson.browser.data.AppSettings
import app.crimson.browser.data.SearchEngines
import app.crimson.browser.data.Stores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/** One browser tab: a Gecko session plus everything the UI shows about it. */
class Tab(val id: Int, val session: GeckoSession, val isPrivate: Boolean) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var secure by mutableStateOf(true)
    var desktop by mutableStateOf(false)
    var thumbnail by mutableStateOf<ImageBitmap?>(null)
    var pendingUrl: String? = null
}

class BrowserState(
    val context: Context,
    val settings: AppSettings,
    val stores: Stores,
) {
    val runtime = GeckoEngine.runtime(context, settings)
    val downloads = Downloader(context)
    val media = MediaDetector()
    val tor = TorManager(context, settings)
    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableIntStateOf(-1)
    var prompt by mutableStateOf<UiPrompt?>(null)
    var contextMenu by mutableStateOf<ContextInfo?>(null)
    var fullscreen by mutableStateOf(false)

    var geckoView: GeckoView? = null
    var filePicker: ((Array<String>, Boolean, (List<Uri>) -> Unit) -> Unit)? = null
    var androidPermissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null
    var toast: ((String) -> Unit)? = null
    var openExternal: ((String) -> Boolean)? = null

    private var nextId = 1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }

    val extensions = ExtensionManager(runtime, settings, media, { showPrompt(it) }) { url ->
        newTab(url = null, select = true, open = false).session
    }

    fun showPrompt(p: UiPrompt) {
        prompt = p
    }

    // ---- Tabs ----

    fun newTab(url: String?, private: Boolean = false, select: Boolean = true, open: Boolean = true): Tab {
        val sessionSettings = GeckoSessionSettings.Builder().usePrivateMode(private).build()
        val session = GeckoSession(sessionSettings)
        if (settings.desktopByDefault.value) {
            session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            session.settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        }
        val tab = Tab(nextId++, session, private)
        tab.desktop = settings.desktopByDefault.value
        attach(tab)
        if (open) session.open(runtime)
        tabs.add(tab)
        if (select) currentId = tab.id
        if (open && !url.isNullOrBlank()) load(tab, url)
        return tab
    }

    fun selectTab(id: Int) {
        val previous = current
        if (previous != null && previous.id != id) captureThumbnail(previous)
        currentId = id
        val tab = current ?: return
        tab.pendingUrl?.let {
            tab.pendingUrl = null
            tab.session.loadUri(it)
        }
    }

    fun closeTab(id: Int) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs.removeAt(index)
        tab.session.close()
        if (tabs.none { !it.isPrivate } && tabs.isEmpty()) {
            newTab(null)
        } else if (currentId == id) {
            val next = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))
            if (next == null) newTab(null) else selectTab(next.id)
        }
    }

    fun closeAll(private: Boolean) {
        tabs.filter { it.isPrivate == private }.map { it.id }.forEach { closeTab(it) }
        if (tabs.isEmpty()) newTab(null)
    }

    private fun tabFor(session: GeckoSession): Tab? = tabs.firstOrNull { it.session === session }

    // ---- Loading ----

    private fun hostOf(url: String): String = try {
        Uri.parse(url).host ?: ""
    } catch (e: Exception) {
        ""
    }

    fun normalize(input: String): String {
        val t = input.trim()
        val lower = t.lowercase()
        if (t.contains("://") || lower.startsWith("about:") || lower.startsWith("data:") ||
            lower.startsWith("view-source:") || lower.startsWith("javascript:")
        ) {
            return t
        }
        val host = t.substringBefore("/").substringBefore("?")
        if (host.lowercase().endsWith(".onion") && !t.contains(" ")) return "http://$t"
        val looksLikeUrl = !t.contains(" ") && (t.contains(".") || lower.startsWith("localhost"))
        return if (looksLikeUrl) "https://$t" else searchUrl(t)
    }

    fun searchUrl(query: String): String {
        val engine = SearchEngines.all.getOrElse(settings.searchEngine.value) { SearchEngines.all[0] }
        return engine.template.replace("%s", Uri.encode(query))
    }

    private fun load(tab: Tab, input: String) {
        val url = normalize(input)
        if (hostOf(url).endsWith(".onion") && !tor.ready) {
            if (settings.torMode.value == 0) settings.torMode.set(1)
            tor.update()
            toast?.invoke("Connecting to Tor for .onion sites")
            scope.launch {
                var waited = 0
                while (!tor.ready && waited < 90000) {
                    delay(500)
                    waited += 500
                }
                tab.session.loadUri(url)
            }
            return
        }
        tab.session.loadUri(url)
    }

    fun open(input: String) {
        val tab = current ?: return
        if (input.isBlank()) return
        load(tab, input)
    }

    fun toggleDesktop(tab: Tab) {
        tab.desktop = !tab.desktop
        tab.session.settings.userAgentMode =
            if (tab.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        tab.session.settings.viewportMode =
            if (tab.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        tab.session.reload()
    }

    fun captureThumbnail(tab: Tab) {
        val view = geckoView ?: return
        if (view.session !== tab.session) return
        try {
            view.capturePixels().accept(
                { bmp ->
                    if (bmp != null && bmp.width > 0) {
                        val w = 360
                        val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                        tab.thumbnail = Bitmap.createScaledBitmap(bmp, w, h, true).asImageBitmap()
                    }
                },
                { },
            )
        } catch (_: Exception) {
        }
    }

    // ---- Settings and data ----

    fun applySettings() {
        GeckoEngine.apply(settings)
        tor.update()
    }

    fun clearData(
        cookies: Boolean,
        cache: Boolean,
        siteData: Boolean,
        history: Boolean,
        downloadList: Boolean = false,
    ) {
        GeckoEngine.clearData(cookies, cache, siteData)
        if (history) stores.history.clear()
        if (downloadList) downloads.clearList()
    }

    fun clearOnExit() {
        if (!settings.clearOnExit.value) return
        clearData(
            cookies = settings.clearCookies.value,
            cache = settings.clearCache.value,
            siteData = settings.clearSiteData.value,
            history = settings.clearHistory.value,
            downloadList = settings.clearDownloadsList.value,
        )
    }

    fun wipeEverything() {
        GeckoEngine.clearEverything()
        stores.history.clear()
        downloads.clearList()
        tabs.toList().forEach { it.session.close() }
        tabs.clear()
        newTab(null)
    }

    // ---- Saving and restoring tabs ----

    fun saveTabs() {
        val sp = context.getSharedPreferences("crimson_tabs", Context.MODE_PRIVATE)
        val normal = tabs.filter { !it.isPrivate && it.url.isNotBlank() }
        val arr = JSONArray()
        normal.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title)) }
        val selected = normal.indexOfFirst { it.id == currentId }
        sp.edit().putString("tabs", arr.toString()).putInt("selected", selected).apply()
    }

    fun restoreTabs() {
        val sp = context.getSharedPreferences("crimson_tabs", Context.MODE_PRIVATE)
        val text = sp.getString("tabs", null)
        if (!settings.clearOnExit.value && text != null) {
            try {
                val arr = JSONArray(text)
                val selected = sp.getInt("selected", 0)
                for (i in 0 until minOf(arr.length(), 12)) {
                    val o = arr.getJSONObject(i)
                    val tab = newTab(url = null, select = i == selected.coerceAtLeast(0))
                    tab.title = o.optString("title")
                    tab.url = o.optString("url")
                    if (i == selected.coerceAtLeast(0)) {
                        tab.session.loadUri(tab.url)
                    } else {
                        tab.pendingUrl = tab.url
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (tabs.isEmpty()) newTab(null)
        if (current == null) currentId = tabs.first().id
    }

    // ---- Delegates ----

    private fun attach(tab: Tab) {
        val s = tab.session

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.url = if (url == null || url == "about:blank") "" else url
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val scheme = uri.substringBefore(":", "").lowercase()
                if (scheme == "http" || scheme == "https") {
                    val blocked = settings.isJsBlockedFor(hostOf(uri))
                    if (session.settings.allowJavascript == blocked) {
                        session.settings.allowJavascript = !blocked
                    }
                    return null
                }
                val internal = setOf(
                    "about", "data", "blob", "javascript", "moz-extension", "resource", "view-source", "chrome", "file", "ws", "wss",
                )
                if (scheme in internal || scheme.isEmpty()) return null
                if (scheme in setOf("tel", "mailto", "sms", "geo")) {
                    openExternal?.invoke(uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                if (settings.blockAppRedirects.value) {
                    toast?.invoke("Blocked a link that tried to open another app")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return if (openExternal?.invoke(uri) == true) GeckoResult.fromValue(AllowOrDeny.DENY) else null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val parentPrivate = tabFor(session)?.isPrivate ?: false
                val newTab = newTab(url = null, private = parentPrivate, select = true, open = false)
                return GeckoResult.fromValue(newTab.session)
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title ?: ""
                if (!tab.isPrivate && tab.url.isNotBlank()) stores.history.updateTitle(tab.url, tab.title)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                fullscreen = fullScreen
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                val type = element.type
                val hasLink = !element.linkUri.isNullOrBlank()
                val hasSrc = !element.srcUri.isNullOrBlank()
                if (hasLink || hasSrc) {
                    contextMenu = ContextInfo(
                        linkUri = element.linkUri,
                        linkText = element.linkText,
                        srcUri = element.srcUri,
                        isImage = type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE,
                        isVideoOrAudio = type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO ||
                            type == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO,
                    )
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                downloads.enqueueResponse(response)
                toast?.invoke("Downloading")
            }

            override fun onCrash(session: GeckoSession) {
                recover(tab)
            }

            override fun onKill(session: GeckoSession) {
                recover(tab)
            }

            override fun onCloseRequest(session: GeckoSession) {
                closeTab(tab.id)
            }

            override fun onFocusRequest(session: GeckoSession) {
                selectTab(tab.id)
            }
        }

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.progress = 0
                media.clear(url)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                if (success && !tab.isPrivate && tab.url.isNotBlank()) {
                    stores.history.add(tab.url, tab.title)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                tab.secure = securityInfo.isSecure
            }
        }

        s.promptDelegate = promptHandler
        s.permissionDelegate = permissionHandler
    }

    private fun recover(tab: Tab) {
        val url = tab.url
        tab.session.close()
        tab.session.open(runtime)
        if (url.isNotBlank()) tab.session.loadUri(url)
    }

    // ---- Page prompts ----

    private val promptHandler = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            showPrompt(
                UiPrompt.Alert(prompt.title ?: "Message", prompt.message ?: "") {
                    result.complete(prompt.confirm())
                },
            )
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ButtonPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            showPrompt(
                UiPrompt.Confirm(prompt.title ?: "Confirm", prompt.message ?: "", "OK", "Cancel") { ok ->
                    val type = if (ok) {
                        GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE
                    } else {
                        GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE
                    }
                    result.complete(prompt.confirm(type))
                },
            )
            return result
        }

        override fun onTextPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.TextPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            showPrompt(
                UiPrompt.Text(prompt.title ?: "Input", prompt.message ?: "", prompt.defaultValue ?: "") { text ->
                    result.complete(if (text == null) prompt.dismiss() else prompt.confirm(text))
                },
            )
            return result
        }

        override fun onChoicePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ChoicePrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val choices = prompt.choices.filter { !it.separator && !it.disabled }
            val multiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE
            showPrompt(
                UiPrompt.Choice(
                    title = prompt.title ?: "Choose",
                    message = prompt.message ?: "",
                    labels = choices.map { it.label ?: "" },
                    ids = choices.map { it.id },
                    multiple = multiple,
                ) { picked ->
                    result.complete(
                        when {
                            picked == null || picked.isEmpty() -> prompt.dismiss()
                            multiple -> prompt.confirm(picked.toTypedArray())
                            else -> prompt.confirm(picked.first())
                        },
                    )
                },
            )
            return result
        }

        override fun onFilePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.FilePrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val picker = filePicker ?: return GeckoResult.fromValue(prompt.dismiss())
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
            val mimes = (prompt.mimeTypes ?: emptyArray()).filter { it.contains("/") }.toTypedArray()
            picker(if (mimes.isEmpty()) arrayOf("*/*") else mimes, multiple) { uris ->
                result.complete(
                    when {
                        uris.isEmpty() -> prompt.dismiss()
                        multiple -> prompt.confirm(context, uris.toTypedArray())
                        else -> prompt.confirm(context, uris.first())
                    },
                )
            }
            return result
        }

        override fun onPopupPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.PopupPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            val allow = if (settings.blockPopups.value) AllowOrDeny.DENY else AllowOrDeny.ALLOW
            return GeckoResult.fromValue(prompt.confirm(allow))
        }

        override fun onBeforeUnloadPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

        override fun onRepostConfirmPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt,
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    // ---- Permissions ----

    private fun permissionLabel(permission: Int): String = when (permission) {
        GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "know your location"
        GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "show notifications"
        GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "store data on your device"
        GeckoSession.PermissionDelegate.PERMISSION_CLIPBOARD_READ -> "read your clipboard"
        GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "use your cookies across sites"
        GeckoSession.PermissionDelegate.PERMISSION_XR -> "use virtual or augmented reality"
        GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> "access devices on your network"
        GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> "access devices on your phone"
        else -> "use a feature"
    }

    private val permissionHandler = object : GeckoSession.PermissionDelegate {
        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<String>?,
            callback: GeckoSession.PermissionDelegate.Callback,
        ) {
            val requester = androidPermissionRequester
            if (permissions == null || requester == null) {
                callback.reject()
                return
            }
            requester(permissions) { granted -> if (granted) callback.grant() else callback.reject() }
        }

        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: GeckoSession.PermissionDelegate.ContentPermission,
        ): GeckoResult<Int>? {
            val allow = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            val deny = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            when (perm.permission) {
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
                -> return GeckoResult.fromValue(allow)
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_TRACKING,
                -> return GeckoResult.fromValue(deny)
            }
            val result = GeckoResult<Int>()
            showPrompt(
                UiPrompt.Confirm(
                    title = "Permission request",
                    message = "${hostOf(perm.uri ?: "")} wants to ${permissionLabel(perm.permission)}.",
                    yes = "Allow",
                    no = "Block",
                ) { ok -> result.complete(if (ok) allow else deny) },
            )
            return result
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback,
        ) {
            val wantsVideo = !video.isNullOrEmpty()
            val wantsAudio = !audio.isNullOrEmpty()
            val what = when {
                wantsVideo && wantsAudio -> "use your camera and microphone"
                wantsVideo -> "use your camera"
                else -> "use your microphone"
            }
            showPrompt(
                UiPrompt.Confirm("Permission request", "${hostOf(uri)} wants to $what.", "Allow", "Block") { ok ->
                    if (ok) {
                        val v: GeckoSession.PermissionDelegate.MediaSource? = video?.firstOrNull()
                        val a: GeckoSession.PermissionDelegate.MediaSource? = audio?.firstOrNull()
                        callback.grant(v, a)
                    } else {
                        callback.reject()
                    }
                },
            )
        }
    }
}
