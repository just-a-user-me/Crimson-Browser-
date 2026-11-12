package app.crimson.browser.engine

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.mozilla.geckoview.GeckoSession

/** One browser tab. Gecko tabs and Chromium (WebView) tabs share the same state for the interface. */
abstract class Tab(val id: Int, val isPrivate: Boolean) {
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

    abstract val isChromium: Boolean

    abstract fun goBack()
    abstract fun goForward()
    abstract fun reload()
    abstract fun stop()
    abstract fun loadUrl(url: String)
    abstract fun destroy()
}

class GeckoTab(id: Int, val session: GeckoSession, isPrivate: Boolean) : Tab(id, isPrivate) {
    override val isChromium = false

    override fun goBack() {
        session.goBack()
    }

    override fun goForward() {
        session.goForward()
    }

    override fun reload() {
        session.reload()
    }

    override fun stop() {
        session.stop()
    }

    override fun loadUrl(url: String) {
        session.loadUri(url)
    }

    override fun destroy() {
        session.close()
    }
}

class WebTab(id: Int, val webView: WebView) : Tab(id, false) {
    override val isChromium = true
    var findInfo by mutableStateOf("")

    override fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    override fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    override fun reload() {
        webView.reload()
    }

    override fun stop() {
        webView.stopLoading()
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun destroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {
        }
    }
}
