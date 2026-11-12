package app.crimson.browser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StoreAddon(
    val name: String,
    val summary: String,
    val iconUrl: String,
    val xpiUrl: String,
    val users: Long,
)

/** Lists add-ons from the Firefox add-on catalog so they can be installed without leaving the app. */
class AddonStore {
    var results by mutableStateOf<List<StoreAddon>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastQuery: String? = null

    fun load(query: String = "", force: Boolean = false) {
        if (!force && lastQuery == query && results.isNotEmpty()) return
        lastQuery = query
        loading = true
        error = ""
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) { fetch(query) }
                results = list
                if (list.isEmpty()) error = "Nothing found"
            } catch (e: Exception) {
                error = "Could not load the add-on list. Check your connection."
            } finally {
                loading = false
            }
        }
    }

    private fun fetch(query: String): List<StoreAddon> {
        var url = "https://addons.mozilla.org/api/v5/addons/search/?app=android&type=extension&sort=users&page_size=30&lang=en-US"
        if (query.isNotBlank()) url += "&q=" + URLEncoder.encode(query, "UTF-8")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<StoreAddon>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val xpi = o.optJSONObject("current_version")?.optJSONObject("file")?.optString("url").orEmpty()
            if (xpi.isBlank()) continue
            out.add(
                StoreAddon(
                    name = text(o.opt("name")),
                    summary = text(o.opt("summary")),
                    iconUrl = o.optString("icon_url"),
                    xpiUrl = xpi,
                    users = o.optLong("average_daily_users"),
                ),
            )
        }
        return out
    }

    private fun text(value: Any?): String = when (value) {
        is JSONObject -> {
            val keys = value.keys()
            if (keys.hasNext()) value.optString(keys.next()) else ""
        }
        null -> ""
        else -> value.toString()
    }
}
