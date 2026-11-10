package app.crimson.browser.engine

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.crimson.browser.data.AppSettings
import org.mozilla.geckoview.GeckoPreferenceController
import org.torproject.jni.TorService

/**
 * Runs the Tor client inside the app and points Gecko at it.
 * Mode 1 sends only .onion addresses through Tor, mode 2 sends all traffic.
 */
class TorManager(private val context: Context, private val settings: AppSettings) {
    var status by mutableStateOf("Off")
        private set
    var ready by mutableStateOf(false)
        private set

    private var service: TorService? = null
    private var running = false
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.getStringExtra(TorService.EXTRA_STATUS)) {
                TorService.STATUS_STARTING -> status = "Starting"
                TorService.STATUS_ON -> {
                    status = "Connected"
                    ready = true
                    applyProxy()
                }
                TorService.STATUS_STOPPING -> {
                    status = "Stopping"
                    ready = false
                }
                TorService.STATUS_OFF -> {
                    status = "Off"
                    ready = false
                }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as TorService.LocalBinder).service
            if (ready) applyProxy()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    /** Call whenever the Tor mode setting changes (and once at startup). */
    fun update() {
        if (settings.torMode.value > 0) {
            if (!running) start() else if (ready) applyProxy()
        } else {
            stop()
        }
    }

    private fun start() {
        running = true
        status = "Starting"
        if (!receiverRegistered) {
            context.registerReceiver(
                receiver,
                IntentFilter(TorService.ACTION_STATUS),
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        try {
            context.bindService(Intent(context, TorService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            status = "Error"
            Log.e("Crimson", "Tor failed to start", e)
        }
    }

    private fun stop() {
        if (!running) {
            clearProxy()
            return
        }
        running = false
        ready = false
        status = "Off"
        clearProxy()
        try {
            context.unbindService(connection)
        } catch (_: Exception) {
        }
        context.stopService(Intent(context, TorService::class.java))
    }

    private fun setPref(name: String, value: Boolean) {
        GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER)
    }

    private fun setPref(name: String, value: Int) {
        GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER)
    }

    private fun setPref(name: String, value: String) {
        GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER)
    }

    private fun applyProxy() {
        val port = service?.socksPort ?: -1
        if (port <= 0) return
        setPref("network.dns.blockDotOnion", false)
        if (settings.torMode.value == 2) {
            setPref("network.proxy.type", 1)
            setPref("network.proxy.socks", "127.0.0.1")
            setPref("network.proxy.socks_port", port)
            setPref("network.proxy.socks_version", 5)
            setPref("network.proxy.socks_remote_dns", true)
        } else {
            val pac = "function FindProxyForURL(url, host) { if (shExpMatch(host, \"*.onion\")) " +
                "{ return \"SOCKS5 127.0.0.1:$port\"; } return \"DIRECT\"; }"
            setPref("network.proxy.autoconfig_url", "data:text/javascript," + Uri.encode(pac))
            setPref("network.proxy.type", 2)
        }
    }

    private fun clearProxy() {
        setPref("network.proxy.type", 0)
    }
}
