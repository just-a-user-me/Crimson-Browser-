package app.crimson.browser.engine

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/** One browser tab: a Gecko session plus the state the UI shows. */
class Tab(val id: Int, val session: GeckoSession) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

/** Owns the Gecko runtime (one per process). */
object GeckoHolder {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
        }
}

class BrowserState(context: Context) {
    private val runtime: GeckoRuntime = GeckoHolder.get(context)
    val tabs = mutableStateListOf<Tab>()
    var currentIndex by mutableIntStateOf(0)
    private var nextId = 1

    val current: Tab? get() = tabs.getOrNull(currentIndex)

    fun newTab(url: String = ""): Tab {
        val session = GeckoSession()
        val tab = Tab(nextId++, session)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
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
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title ?: ""
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.progress = 0
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }
        }

        session.open(runtime)
        tabs.add(tab)
        currentIndex = tabs.lastIndex
        if (url.isNotEmpty()) {
            session.loadUri(normalize(url))
        }
        return tab
    }

    fun select(index: Int) {
        if (index in tabs.indices) currentIndex = index
    }

    fun close(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        tab.session.close()
        if (tabs.isEmpty()) {
            newTab()
        } else {
            if (index < currentIndex) currentIndex -= 1
            currentIndex = currentIndex.coerceIn(0, tabs.lastIndex)
        }
    }

    fun open(input: String) {
        val tab = current ?: return
        if (input.isBlank()) return
        tab.session.loadUri(normalize(input))
    }

    private fun normalize(input: String): String {
        val text = input.trim()
        return when {
            text.contains("://") -> text
            text.contains(" ") || !text.contains(".") ->
                "https://duckduckgo.com/?q=" + Uri.encode(text)
            else -> "https://$text"
        }
    }
}
