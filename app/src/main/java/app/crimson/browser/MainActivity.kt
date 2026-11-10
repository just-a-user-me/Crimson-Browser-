package app.crimson.browser

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.crimson.browser.data.AppSettings
import app.crimson.browser.data.Stores
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.ui.AppIcons
import app.crimson.browser.ui.CrimsonApp

class MainActivity : ComponentActivity() {
    private lateinit var browser: BrowserState
    private var fileCallback: ((List<Uri>) -> Unit)? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            fileCallback?.invoke(uris)
            fileCallback = null
        }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionCallback?.invoke(result.values.all { it })
            permissionCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = AppSettings(this)
        val stores = Stores(this)
        browser = BrowserState(this, settings, stores)

        browser.toast = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
        browser.openExternal = { uri ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Exception) {
                false
            }
        }
        browser.filePicker = { mimes, _, callback ->
            fileCallback = callback
            filePicker.launch(mimes)
        }
        browser.androidPermissionRequester = { permissions, callback ->
            permissionCallback = callback
            permissionRequest.launch(permissions)
        }

        browser.extensions.start()
        browser.restoreTabs()
        intent?.data?.toString()?.let { browser.newTab(it) }
        browser.tor.update()

        setContent { CrimsonApp(browser, onQuit = { quit() }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { browser.newTab(it) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        browser.runtime.orientationChanged()
    }

    override fun onStop() {
        super.onStop()
        browser.saveTabs()
    }

    private fun quit() {
        browser.saveTabs()
        browser.clearOnExit()
        finishAndRemoveTask()
        // Give the engine a moment to finish deleting data, then fully stop the app.
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 1200)
    }
}
