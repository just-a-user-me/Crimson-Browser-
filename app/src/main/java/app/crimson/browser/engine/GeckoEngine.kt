package app.crimson.browser.engine

import android.content.Context
import app.crimson.browser.data.AppSettings
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController

/** Creates the Gecko runtime (one per process) and applies Privacy Guard settings to it. */
object GeckoEngine {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun runtime(context: Context, settings: AppSettings): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: create(context.applicationContext, settings).also {
                runtime = it
                apply(settings)
            }
        }

    private fun antiTrackingFlags(s: AppSettings): Int {
        if (s.trackingLevel.value == 0) return ContentBlocking.AntiTracking.NONE
        var flags = 0
        if (s.blockAds.value) flags = flags or ContentBlocking.AntiTracking.AD
        if (s.blockAnalytics.value) flags = flags or ContentBlocking.AntiTracking.ANALYTIC
        if (s.blockSocial.value) flags = flags or ContentBlocking.AntiTracking.SOCIAL
        if (s.blockCrypto.value) flags = flags or ContentBlocking.AntiTracking.CRYPTOMINING
        if (s.blockFingerprinters.value) flags = flags or ContentBlocking.AntiTracking.FINGERPRINTING
        if (s.blockContentTrackers.value) flags = flags or ContentBlocking.AntiTracking.CONTENT
        if (s.trackingLevel.value == 2) flags = flags or ContentBlocking.AntiTracking.STRICT
        return flags
    }

    private fun etpLevel(s: AppSettings): Int = when (s.trackingLevel.value) {
        0 -> ContentBlocking.EtpLevel.NONE
        1 -> ContentBlocking.EtpLevel.DEFAULT
        else -> ContentBlocking.EtpLevel.STRICT
    }

    private fun safeBrowsing(s: AppSettings): Int =
        if (s.safeBrowsing.value) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE

    private fun create(context: Context, s: AppSettings): GeckoRuntime {
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(antiTrackingFlags(s))
            .enhancedTrackingProtectionLevel(etpLevel(s))
            .cookieBehavior(s.cookieBehavior.value)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .safeBrowsing(safeBrowsing(s))
            .strictSocialTrackingProtection(s.trackingLevel.value == 2)
            .cookiePurging(true)
            .queryParameterStrippingEnabled(s.queryStripping.value)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .consoleOutput(false)
            .aboutConfigEnabled(false)
            .remoteDebuggingEnabled(s.remoteDebugging.value)
            .allowInsecureConnections(
                if (s.httpsOnly.value) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL,
            )
            .globalPrivacyControlEnabled(s.globalPrivacyControl.value)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .extensionsWebAPIEnabled(true)
            .fontSizeFactor(s.webTextScale.value)
            .contentBlocking(blocking)
            .build()

        return GeckoRuntime.create(context, settings)
    }

    /** Pushes the current settings into the running engine. Safe to call at any time. */
    fun apply(s: AppSettings) {
        val rt = runtime ?: return
        val rs = rt.settings
        rs.setAllowInsecureConnections(
            if (s.httpsOnly.value) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL,
        )
        rs.setGlobalPrivacyControl(s.globalPrivacyControl.value)
        rs.setFingerprintingProtection(s.fingerprintProtection.value)
        rs.setFingerprintingProtectionPrivateBrowsing(true)
        rs.setFontSizeFactor(s.webTextScale.value)
        rs.setRemoteDebuggingEnabled(s.remoteDebugging.value)
        rs.setTrustedRecursiveResolverMode(
            if (s.secureDns.value) GeckoRuntimeSettings.TRR_MODE_FIRST else GeckoRuntimeSettings.TRR_MODE_OFF,
        )
        rs.setTrustedRecursiveResolverUri("https://mozilla.cloudflare-dns.com/dns-query")

        val cb = rs.contentBlocking
        cb.setAntiTracking(antiTrackingFlags(s))
        cb.setEnhancedTrackingProtectionLevel(etpLevel(s))
        cb.setCookieBehavior(s.cookieBehavior.value)
        cb.setSafeBrowsing(safeBrowsing(s))
        cb.setStrictSocialTrackingProtection(s.trackingLevel.value == 2)
        cb.setQueryParameterStrippingEnabled(s.queryStripping.value)
    }

    /** Deletes browsing data. Pass the kinds you want removed. */
    fun clearData(cookies: Boolean, cache: Boolean, siteData: Boolean): GeckoResult<Void>? {
        val rt = runtime ?: return null
        var flags = 0L
        if (cookies) flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.AUTH_SESSIONS
        if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
        if (siteData) flags = flags or StorageController.ClearFlags.SITE_DATA or StorageController.ClearFlags.DOM_STORAGES
        if (flags == 0L) return null
        return rt.storageController.clearData(flags)
    }

    fun clearEverything(): GeckoResult<Void>? {
        val rt = runtime ?: return null
        return rt.storageController.clearData(StorageController.ClearFlags.ALL)
    }
}
