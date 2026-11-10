package app.crimson.browser.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Bookmark(val url: String, val title: String, val added: Long)

data class HistoryEntry(val url: String, val title: String, val time: Long)

private val io = Executors.newSingleThreadExecutor()

private fun readArray(file: File): JSONArray =
    try {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }

private fun writeAsync(file: File, array: JSONArray) {
    val text = array.toString()
    io.execute {
        try {
            file.writeText(text)
        } catch (_: Exception) {
        }
    }
}

class BookmarkStore(private val file: File) {
    val items = mutableStateListOf<Bookmark>()

    init {
        val arr = readArray(file)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items.add(Bookmark(o.getString("url"), o.optString("title"), o.optLong("added")))
        }
    }

    fun contains(url: String) = items.any { it.url == url }

    fun toggle(url: String, title: String) {
        val existing = items.indexOfFirst { it.url == url }
        if (existing >= 0) items.removeAt(existing) else items.add(0, Bookmark(url, title, System.currentTimeMillis()))
        save()
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("added", it.added)) }
        writeAsync(file, arr)
    }
}

class HistoryStore(private val file: File) {
    val items = mutableStateListOf<HistoryEntry>()

    init {
        val arr = readArray(file)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items.add(HistoryEntry(o.getString("url"), o.optString("title"), o.optLong("time")))
        }
    }

    fun add(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        if (items.firstOrNull()?.url == url) {
            if (title.isNotBlank()) items[0] = items[0].copy(title = title)
        } else {
            items.add(0, HistoryEntry(url, title, System.currentTimeMillis()))
            while (items.size > 2000) items.removeAt(items.lastIndex)
        }
        save()
    }

    fun updateTitle(url: String, title: String) {
        val i = items.indexOfFirst { it.url == url }
        if (i >= 0 && title.isNotBlank()) {
            items[i] = items[i].copy(title = title)
            save()
        }
    }

    fun remove(entry: HistoryEntry) {
        items.remove(entry)
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("time", it.time)) }
        writeAsync(file, arr)
    }
}

class Stores(context: Context) {
    val bookmarks = BookmarkStore(File(context.filesDir, "bookmarks.json"))
    val history = HistoryStore(File(context.filesDir, "history.json"))
}
