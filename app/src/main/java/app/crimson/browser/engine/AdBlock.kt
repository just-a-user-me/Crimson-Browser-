package app.crimson.browser.engine

import android.content.Context
import kotlin.concurrent.thread

/** A list of known ad, tracker and malware domains, used by Chromium tabs. */
object AdBlock {
    private val hosts = HashSet<String>()

    @Volatile
    private var ready = false

    private val fallback = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google.com", "adnxs.com", "taboola.com",
        "outbrain.com", "criteo.com", "criteo.net", "amazon-adsystem.com", "adsrvr.org", "rubiconproject.com",
        "pubmatic.com", "openx.net", "moatads.com", "scorecardresearch.com", "hotjar.com", "quantserve.com",
        "adform.net", "smartadserver.com", "casalemedia.com", "advertising.com", "yieldmo.com", "mathtag.com",
    )

    fun init(context: Context) {
        if (ready) return
        thread(name = "adblock-load") {
            val loaded = HashSet<String>()
            loaded.addAll(fallback)
            try {
                context.assets.open("adhosts.txt").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val t = line.trim()
                        if (t.isEmpty() || t.startsWith("#")) continue
                        val parts = t.split(Regex("\\s+"))
                        val host = if (parts.size >= 2) parts[1] else parts[0]
                        if (host.contains(".") && host != "localhost" && !host.startsWith("0.")) loaded.add(host.lowercase())
                    }
                }
            } catch (_: Exception) {
            }
            synchronized(hosts) { hosts.addAll(loaded) }
            ready = true
        }
    }

    fun isBlocked(host: String): Boolean {
        if (!ready) return false
        var h = host.lowercase()
        synchronized(hosts) {
            while (true) {
                if (hosts.contains(h)) return true
                val i = h.indexOf('.')
                if (i < 0) return false
                h = h.substring(i + 1)
                if (!h.contains('.')) return false
            }
        }
    }
}
