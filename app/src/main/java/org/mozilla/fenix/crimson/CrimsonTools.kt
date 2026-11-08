package org.mozilla.fenix.crimson

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.support.webextensions.BuiltInWebExtensionController
import org.json.JSONObject

/**
 * Installs the built-in "Crimson Tools" extension (dark mode for websites and in-page developer tools)
 * and pushes the settings from the app to it.
 */
object CrimsonTools : SharedPreferences.OnSharedPreferenceChangeListener {
    private const val TAG = "CrimsonTools"
    private const val EXTENSION_ID = "crimson-tools@crimson.app"
    private const val EXTENSION_URL = "resource://android/assets/extensions/crimson-tools/"
    private const val PORT_NAME = "crimsonTools"

    private val controller = BuiltInWebExtensionController(EXTENSION_ID, EXTENSION_URL, PORT_NAME)

    @Volatile
    private var activePort: Port? = null
    private var appContext: Context? = null
    private var started = false

    @Synchronized
    fun start(context: Context, runtime: WebExtensionRuntime) {
        if (started) return
        started = true
        appContext = context.applicationContext
        CrimsonPrefs.prefs(context).registerOnSharedPreferenceChangeListener(this)

        controller.registerBackgroundMessageHandler(
            object : MessageHandler {
                override fun onPortConnected(port: Port) {
                    activePort = port
                    pushSettings()
                }

                override fun onPortDisconnected(port: Port) {
                    if (activePort == port) {
                        activePort = null
                    }
                }
            },
        )

        controller.install(
            runtime,
            onSuccess = { Log.d(TAG, "Crimson Tools installed") },
            onError = { Log.e(TAG, "Crimson Tools failed to install", it) },
        )
    }

    private fun pushSettings() {
        val context = appContext ?: return
        val message = JSONObject()
            .put("forceDark", CrimsonPrefs.forceDark(context))
            .put("devMode", CrimsonPrefs.devMode(context))
        activePort?.postMessage(message)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == CrimsonPrefs.KEY_FORCE_DARK || key == CrimsonPrefs.KEY_DEV_MODE) {
            pushSettings()
        }
    }
}
