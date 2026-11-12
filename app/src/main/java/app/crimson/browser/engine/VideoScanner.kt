package app.crimson.browser.engine

import java.net.HttpURLConnection
import java.net.URL

/** Looks through a page's source for video links. Works even when the add-on cannot report them. */
object VideoScanner {
    private const val AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "m3u8" -> "application/vnd.apple.mpegurl"
        "mpd" -> "application/dash+xml"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }

    fun scan(pageUrl: String, cookies: String?): List<MediaItem> {
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", AGENT)
        if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
        val html = conn.inputStream.bufferedReader().use { it.readText().take(3_000_000) }
        val text = html
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")

        val found = LinkedHashMap<String, String>()
        Regex("https?://[^\"'\\s<>\\\\)]+?\\.(mp4|m3u8|webm|mpd|m4v)(\\?[^\"'\\s<>\\\\)]*)?", RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { found[it.value] = mimeFor(it.groupValues[1]) }
        Regex(
            "<meta[^>]+property=[\"']og:video(?::url|:secure_url)?[\"'][^>]+content=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE,
        ).findAll(text).forEach { found.putIfAbsent(it.groupValues[1], "video/mp4") }
        Regex("<source[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(text).forEach {
            val abs = try {
                URL(URL(pageUrl), it.groupValues[1]).toString()
            } catch (e: Exception) {
                null
            }
            if (abs != null) found.putIfAbsent(abs, "video/mp4")
        }
        Regex("<video[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(text).forEach {
            val abs = try {
                URL(URL(pageUrl), it.groupValues[1]).toString()
            } catch (e: Exception) {
                null
            }
            if (abs != null) found.putIfAbsent(abs, "video/mp4")
        }
        return found.entries.map { MediaItem(it.key, it.value, 0) }.take(20)
    }
}
