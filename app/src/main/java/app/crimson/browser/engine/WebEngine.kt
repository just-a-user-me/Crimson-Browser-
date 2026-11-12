package app.crimson.browser.engine

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.asImageBitmap
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONTokener
import org.mozilla.geckoview.ContentBlocking
import java.io.ByteArrayInputStream

/** Chromium engine: tabs that run on the Chromium that is built into Android (System WebView). */
class WebEngine(private val browser: BrowserState) {
    private val settings get() = browser.settings

    private val desktopAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val erudaScript: String by lazy {
        try {
            browser.context.assets.open("extensions/crimson/eruda.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(url: String?, select: Boolean, transport: WebView.WebViewTransport? = null): WebTab {
        val wv = WebView(browser.context)
        val tab = WebTab(browser.nextTabId(), wv)
        tab.desktop = settings.desktopByDefault.value
        configure(wv, tab)
        browser.addTab(tab, select)
        if (transport != null) {
            transport.webView = wv
        } else if (!url.isNullOrBlank()) {
            wv.loadUrl(browser.normalize(url))
        }
        return tab
    }

    fun applySettings(tab: WebTab) {
        val s = tab.webView.settings
        s.javaScriptEnabled = settings.jsEnabled.value
        s.textZoom = (settings.webTextScale.value * 100).toInt()
        s.javaScriptCanOpenWindowsAutomatically = !settings.blockPopups.value
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, settings.forceDark.value)
        }
        WebView.setWebContentsDebuggingEnabled(settings.remoteDebugging.value)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(settings.cookieBehavior.value != ContentBlocking.CookieBehavior.ACCEPT_NONE)
        cm.setAcceptThirdPartyCookies(tab.webView, settings.cookieBehavior.value == ContentBlocking.CookieBehavior.ACCEPT_ALL)
    }

    fun toggleDesktop(tab: WebTab) {
        tab.desktop = !tab.desktop
        tab.webView.settings.userAgentString = if (tab.desktop) desktopAgent else null
        tab.webView.reload()
    }

    fun thumbnail(tab: WebTab) {
        val wv = tab.webView
        if (wv.width <= 0 || wv.height <= 0) return
        try {
            val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
            wv.draw(Canvas(bmp))
            val w = 360
            val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
            tab.thumbnail = Bitmap.createScaledBitmap(bmp, w, h, true).asImageBitmap()
        } catch (_: Exception) {
        }
    }

    fun find(tab: WebTab, text: String, forward: Boolean) {
        if (text.isBlank()) {
            tab.webView.clearMatches()
            tab.findInfo = ""
            return
        }
        tab.webView.setFindListener { active, count, _ ->
            tab.findInfo = if (count == 0) "No matches" else "${active + 1}/$count"
        }
        tab.webView.findAllAsync(text)
        if (!forward) tab.webView.findNext(false)
    }

    fun toggleDevPanel(tab: WebTab) {
        if (erudaScript.isBlank()) return
        tab.webView.evaluateJavascript(
            erudaScript + ";(function(){if(!window.eruda)return;if(window.__crimsonE){eruda.destroy();window.__crimsonE=false}else{eruda.init();window.__crimsonE=true}})()",
            null,
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(wv: WebView, tab: WebTab) {
        val s = wv.settings
        s.javaScriptEnabled = settings.jsEnabled.value
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = !settings.blockPopups.value
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = true
        s.setGeolocationEnabled(true)
        if (tab.desktop) s.userAgentString = desktopAgent
        wv.setBackgroundColor(Color.BLACK)
        applySettings(tab)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase() ?: ""
                if (scheme == "http" || scheme == "https") {
                    val host = uri.host ?: ""
                    if (host.endsWith(".onion")) {
                        browser.newTab(uri.toString(), select = true, engine = 0)
                        return true
                    }
                    if (scheme == "http" && settings.httpsOnly.value && host != "localhost" && !host.startsWith("192.168.")) {
                        view.loadUrl("https" + uri.toString().removePrefix("http"))
                        return true
                    }
                    return false
                }
                if (scheme in setOf("about", "data", "blob", "javascript", "file")) return false
                if (scheme in setOf("tel", "mailto", "sms", "geo")) {
                    browser.openExternal?.invoke(uri.toString())
                    return true
                }
                if (settings.blockAppRedirects.value) {
                    browser.toast?.invoke("Blocked a link that tried to open another app")
                    return true
                }
                return browser.openExternal?.invoke(uri.toString()) == true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                tab.url = url ?: ""
                tab.loading = true
                tab.progress = 0
                tab.secure = url?.startsWith("https") == true
                if (url != null) browser.media.clear(url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                tab.loading = false
                tab.url = url ?: tab.url
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
                if (!tab.isPrivate && tab.url.isNotBlank()) browser.stores.history.add(tab.url, tab.title)
                scanForMedia(view, tab)
                if (settings.devMode.value && erudaScript.isNotBlank()) {
                    view.evaluateJavascript(erudaScript + ";if(window.eruda&&!window.__crimsonE){eruda.init();window.__crimsonE=true}", null)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null) tab.url = url
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                val host = u.host ?: return null
                if (settings.trackingLevel.value > 0 && AdBlock.isBlocked(host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                val path = u.toString().substringBefore("?").lowercase()
                if (path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mpd")) {
                    browser.media.add(tab.url, u.toString(), if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/*", 0)
                }
                return null
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title ?: ""
                if (!tab.isPrivate && tab.url.isNotBlank()) browser.stores.history.updateTitle(tab.url, tab.title)
            }

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (settings.blockPopups.value && !isUserGesture) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                create(null, true, transport)
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                browser.closeTab(tab.id)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                val picker = browser.filePicker
                if (picker == null) {
                    filePathCallback.onReceiveValue(null)
                    return true
                }
                val mimes = params.acceptTypes.filter { it.contains("/") }.toTypedArray()
                picker(if (mimes.isEmpty()) arrayOf("*/*") else mimes, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) { uris ->
                    filePathCallback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsVideo = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (!wantsVideo && !wantsAudio) {
                    request.deny()
                    return
                }
                val what = when {
                    wantsVideo && wantsAudio -> "use your camera and microphone"
                    wantsVideo -> "use your camera"
                    else -> "use your microphone"
                }
                browser.showPrompt(
                    UiPrompt.Confirm("Permission request", "${request.origin.host} wants to $what.", "Allow", "Block") { ok ->
                        if (!ok) {
                            request.deny()
                            return@Confirm
                        }
                        val needed = mutableListOf<String>()
                        if (wantsVideo) needed.add(android.Manifest.permission.CAMERA)
                        if (wantsAudio) needed.add(android.Manifest.permission.RECORD_AUDIO)
                        val requester = browser.androidPermissionRequester
                        if (requester == null) {
                            request.deny()
                        } else {
                            requester(needed.toTypedArray()) { granted ->
                                if (granted) request.grant(request.resources) else request.deny()
                            }
                        }
                    },
                )
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                browser.showPrompt(
                    UiPrompt.Confirm("Permission request", "$origin wants to know your location.", "Allow", "Block") { ok ->
                        if (!ok) {
                            callback.invoke(origin, false, false)
                            return@Confirm
                        }
                        val requester = browser.androidPermissionRequester
                        if (requester == null) {
                            callback.invoke(origin, false, false)
                        } else {
                            requester(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)) { granted ->
                                callback.invoke(origin, granted, false)
                            }
                        }
                    },
                )
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                browser.customView = view
                browser.customViewCallback = callback
                browser.fullscreen = true
            }

            override fun onHideCustomView() {
                browser.customView = null
                browser.customViewCallback = null
                browser.fullscreen = false
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                browser.showPrompt(UiPrompt.Alert("Message", message ?: "") { result.confirm() })
                return true
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                browser.showPrompt(
                    UiPrompt.Confirm("Confirm", message ?: "", "OK", "Cancel") { ok ->
                        if (ok) result.confirm() else result.cancel()
                    },
                )
                return true
            }

            override fun onJsPrompt(
                view: WebView,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult,
            ): Boolean {
                browser.showPrompt(
                    UiPrompt.Text("Input", message ?: "", defaultValue ?: "") { text ->
                        if (text == null) result.cancel() else result.confirm(text)
                    },
                )
                return true
            }
        }

        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            browser.downloads.enqueueUrl(
                url,
                nameHint = URLUtil.guessFileName(url, contentDisposition, mimeType),
                referer = tab.url,
                mimeHint = mimeType,
                cookie = CookieManager.getInstance().getCookie(url),
            )
            browser.toast?.invoke("Downloading")
        }

        wv.setOnLongClickListener { v ->
            val result = (v as WebView).hitTestResult
            when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    browser.contextMenu = ContextInfo(result.extra, null, null, isImage = false, isVideoOrAudio = false)
                    true
                }
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    browser.contextMenu = ContextInfo(null, null, result.extra, isImage = true, isVideoOrAudio = false)
                    true
                }
                else -> false
            }
        }
    }

    private fun scanForMedia(view: WebView, tab: WebTab) {
        val js = "(function(){var u=[];document.querySelectorAll('video,audio').forEach(function(e){" +
            "if(e.currentSrc)u.push(e.currentSrc);else if(e.src)u.push(e.src);" +
            "e.querySelectorAll('source').forEach(function(s){if(s.src)u.push(s.src)})});return JSON.stringify(u)})()"
        view.evaluateJavascript(js) { raw ->
            try {
                val text = JSONTokener(raw).nextValue() as? String ?: return@evaluateJavascript
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val u = arr.getString(i)
                    if (u.isNotBlank()) browser.media.add(tab.url, u, "video/*", 0)
                }
            } catch (_: Exception) {
            }
        }
    }
}
