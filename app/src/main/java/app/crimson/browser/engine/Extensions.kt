package app.crimson.browser.engine

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.crimson.browser.data.AppSettings
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** Videos and audio found on pages, filled in by the built-in Crimson extension. */
class MediaDetector {
    private val found = mutableStateMapOf<String, List<MediaItem>>()

    private fun key(page: String) = page.substringBefore("#").trimEnd('/')

    fun itemsFor(page: String): List<MediaItem> = found[key(page)] ?: emptyList()

    fun add(page: String, url: String, mime: String, size: Long) {
        if (page.isBlank() || url.startsWith("blob:") || url.startsWith("data:")) return
        val lower = url.substringBefore("?").lowercase()
        if (lower.endsWith(".ts") || lower.endsWith(".m4s") || lower.endsWith(".aac")) return
        if (mime.contains("mp2t")) return
        val k = key(page)
        val existing = found[k] ?: emptyList()
        if (existing.any { it.url == url }) return
        found[k] = existing + MediaItem(url, mime, size)
    }

    fun clear(page: String) {
        found.remove(key(page))
    }
}

/**
 * Installs the built-in Crimson extension (media detection, dark mode for websites, developer panel)
 * and manages the add-ons the user installs.
 */
class ExtensionManager(
    private val runtime: GeckoRuntime,
    private val settings: AppSettings,
    private val media: MediaDetector,
    private val showPrompt: (UiPrompt) -> Unit,
    private val sessionForExtension: (String?) -> GeckoSession,
) {
    val installed = mutableStateListOf<WebExtension>()
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")

    private val controller get() = runtime.webExtensionController

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            val json = message as? JSONObject ?: return null
            return when (json.optString("type")) {
                "settings" -> {
                    val reply: Any = JSONObject()
                        .put("forceDark", settings.forceDark.value)
                        .put("devMode", settings.devMode.value)
                    GeckoResult.fromValue(reply)
                }
                "media" -> {
                    media.add(
                        json.optString("page"),
                        json.optString("url"),
                        json.optString("mime"),
                        json.optLong("size"),
                    )
                    null
                }
                else -> null
            }
        }
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(
            source: WebExtension,
            createDetails: WebExtension.CreateTabDetails,
        ): GeckoResult<GeckoSession>? {
            return GeckoResult.fromValue(sessionForExtension(createDetails.url))
        }
    }

    fun start() {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                val wanted = (permissions.toList() + origins.toList()).take(12)
                val text = if (wanted.isEmpty()) "It asks for no special permissions." else
                    "It asks to: " + wanted.joinToString(", ")
                showPrompt(
                    UiPrompt.Confirm(
                        title = "Add ${extension.metaData?.name ?: "this add-on"}?",
                        message = text,
                        yes = "Add",
                        no = "Cancel",
                        onResult = { ok ->
                            result.complete(WebExtension.PermissionPromptResponse(ok, ok, ok))
                        },
                    ),
                )
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<String>,
                newOrigins: Array<String>,
                newDataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.ALLOW)

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                refresh()
            }

            override fun onUninstalled(extension: WebExtension) {
                refresh()
            }

            override fun onEnabled(extension: WebExtension) {
                refresh()
            }

            override fun onDisabled(extension: WebExtension) {
                refresh()
            }
        })

        controller.ensureBuiltIn("resource://android/assets/extensions/crimson/", "crimson@crimson.app")
            .accept(
                { ext ->
                    ext?.setMessageDelegate(messageDelegate, "crimsonNative")
                    refresh()
                },
                { err -> Log.e("Crimson", "Built-in extension failed", err) },
            )
        refresh()
    }

    fun refresh() {
        controller.list().accept(
            { list ->
                installed.clear()
                list?.filter { !it.isBuiltIn }?.forEach {
                    it.tabDelegate = tabDelegate
                    installed.add(it)
                }
            },
            { err -> Log.e("Crimson", "Listing add-ons failed", err) },
        )
    }

    fun installFromUrl(url: String) {
        if (url.isBlank()) return
        busy = true
        error = ""
        controller.install(url.trim(), WebExtensionController.INSTALLATION_METHOD_MANAGER).accept(
            {
                busy = false
                refresh()
            },
            { err ->
                busy = false
                error = err?.message ?: "Could not install the add-on"
            },
        )
    }

    fun installUblock() {
        installFromUrl("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi")
    }

    fun setEnabled(ext: WebExtension, enabled: Boolean) {
        val r = if (enabled) {
            controller.enable(ext, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(ext, WebExtensionController.EnableSource.USER)
        }
        r.accept({ refresh() }, { err -> error = err?.message ?: "Failed" })
    }

    fun uninstall(ext: WebExtension) {
        controller.uninstall(ext).accept({ refresh() }, { err -> error = err?.message ?: "Failed" })
    }
}
