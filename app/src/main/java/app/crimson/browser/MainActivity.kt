package app.crimson.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.crimson.browser.engine.BrowserState
import app.crimson.browser.ui.CrimsonApp

class MainActivity : ComponentActivity() {
    private lateinit var browser: BrowserState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browser = BrowserState(this)
        browser.newTab(intent?.data?.toString() ?: "")
        setContent { CrimsonApp(browser) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { browser.newTab(it) }
    }
}
