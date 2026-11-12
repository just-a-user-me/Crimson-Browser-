package app.crimson.browser.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebResponse
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class DownloadState { Running, Done, Failed, Cancelled }

class DownloadItem(val id: Int, val name: String, val url: String) {
    var state by mutableStateOf(DownloadState.Running)
    var downloaded by mutableLongStateOf(0L)
    var total by mutableLongStateOf(-1L)
    var message by mutableStateOf("")
    var savedUri by mutableStateOf<Uri?>(null)
    var mime by mutableStateOf("application/octet-stream")
    var job: Job? = null
}

/** Saves files into the public Downloads/Crimson folder. Supports direct files and HLS (.m3u8) streams. */
class Downloader(private val context: Context) {
    val items = mutableStateListOf<DownloadItem>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nextId = 1
    private val userAgent =
        "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"

    fun enqueueUrl(url: String, nameHint: String? = null, referer: String? = null, mimeHint: String? = null, cookie: String? = null) {
        val cookies = cookie ?: try { android.webkit.CookieManager.getInstance().getCookie(url) } catch (e: Exception) { null }
        val name = sanitize(nameHint ?: guessName(url, mimeHint))
        val item = DownloadItem(nextId++, name, url)
        mimeHint?.let { item.mime = it }
        items.add(0, item)
        item.job = scope.launch {
            try {
                if (isHls(url, mimeHint)) {
                    downloadHls(item, referer, cookies)
                } else {
                    downloadDirect(item, referer, cookies)
                }
            } catch (e: Exception) {
                if (item.state != DownloadState.Cancelled) {
                    item.state = DownloadState.Failed
                    item.message = e.message ?: "Download failed"
                }
            }
        }
    }

    fun enqueueResponse(response: WebResponse) {
        val headers = response.headers
        val disposition = headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value
        val type = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.substringBefore(";")
        val length = headers.entries.firstOrNull { it.key.equals("content-length", true) }?.value?.toLongOrNull() ?: -1L
        val fromDisposition = disposition?.let {
            Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
        }
        val name = sanitize(fromDisposition ?: guessName(response.uri, type))
        val item = DownloadItem(nextId++, name, response.uri)
        type?.let { item.mime = it }
        item.total = length
        items.add(0, item)
        val body = response.body
        item.job = scope.launch {
            try {
                if (body != null) {
                    saveStream(item, body)
                } else {
                    downloadDirect(item, null)
                }
            } catch (e: Exception) {
                if (item.state != DownloadState.Cancelled) {
                    item.state = DownloadState.Failed
                    item.message = e.message ?: "Download failed"
                }
            }
        }
    }

    fun cancel(item: DownloadItem) {
        item.state = DownloadState.Cancelled
        item.job?.cancel()
    }

    fun remove(item: DownloadItem) {
        cancel(item)
        items.remove(item)
    }

    fun clearList() {
        items.filter { it.state == DownloadState.Running }.forEach { cancel(it) }
        items.clear()
    }

    private fun isHls(url: String, mime: String?): Boolean =
        url.substringBefore("?").lowercase().endsWith(".m3u8") ||
            (mime?.contains("mpegurl", ignoreCase = true) == true)

    private fun open(url: String, referer: String?, cookies: String? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", userAgent)
        if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
        if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
        conn.connect()
        if (conn.responseCode >= 400) throw IllegalStateException("Server returned ${conn.responseCode}")
        return conn
    }

    private fun downloadDirect(item: DownloadItem, referer: String?, cookies: String? = null) {
        val conn = open(item.url, referer, cookies)
        item.total = conn.contentLengthLong
        conn.contentType?.substringBefore(";")?.let { if (it.isNotBlank()) item.mime = it }
        conn.inputStream.use { saveStream(item, it) }
    }

    private fun createOutput(item: DownloadItem): Pair<Uri, OutputStream> {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, item.name)
            put(MediaStore.Downloads.MIME_TYPE, item.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Crimson")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Cannot create file")
        val out = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Cannot write file")
        return uri to out
    }

    private fun finish(item: DownloadItem, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
        item.savedUri = uri
        item.state = DownloadState.Done
    }

    private fun saveStream(item: DownloadItem, input: InputStream) {
        val (uri, out) = createOutput(item)
        try {
            out.use { o ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (item.state == DownloadState.Cancelled) throw InterruptedException("Cancelled")
                    val n = input.read(buffer)
                    if (n < 0) break
                    o.write(buffer, 0, n)
                    item.downloaded += n
                }
            }
            finish(item, uri)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun downloadHls(item: DownloadItem, referer: String?, cookies: String? = null) {
        var playlistUrl = item.url
        var text = open(playlistUrl, referer, cookies).inputStream.bufferedReader().use { it.readText() }

        if (text.contains("#EXT-X-STREAM-INF")) {
            // Master playlist: pick the highest-bandwidth variant.
            val lines = text.lines()
            var bestBandwidth = -1L
            var bestUrl: String? = null
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (next != null && bw > bestBandwidth) {
                        bestBandwidth = bw
                        bestUrl = next.trim()
                    }
                }
            }
            val chosen = bestUrl ?: throw IllegalStateException("No stream found in playlist")
            playlistUrl = resolve(playlistUrl, chosen)
            text = open(playlistUrl, referer, cookies).inputStream.bufferedReader().use { it.readText() }
        }

        if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) {
            throw IllegalStateException("This stream is encrypted and cannot be downloaded")
        }

        val segments = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (segments.isEmpty()) throw IllegalStateException("The playlist has no segments")

        item.mime = "video/mp2t"
        item.total = segments.size.toLong()
        val renamed = if (item.name.lowercase().endsWith(".ts")) item.name else item.name.substringBeforeLast(".") + ".ts"
        val target = DownloadItem(item.id, renamed, item.url)
        target.mime = item.mime

        val (uri, out) = createOutput(target)
        try {
            out.use { o ->
                val buffer = ByteArray(64 * 1024)
                for (segment in segments) {
                    if (item.state == DownloadState.Cancelled) throw InterruptedException("Cancelled")
                    val conn = open(resolve(playlistUrl, segment), referer, cookies)
                    conn.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            o.write(buffer, 0, n)
                        }
                    }
                    item.downloaded += 1
                }
            }
            finish(item, uri)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun resolve(base: String, ref: String): String = URL(URL(base), ref).toString()

    private fun guessName(url: String, mime: String?): String {
        val path = Uri.parse(url).lastPathSegment ?: "download"
        if (path.contains(".")) return path
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext != null) "$path.$ext" else path
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }.take(120)
}
