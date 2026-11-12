package com.sawn.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import com.sawn.app.sawnContainer
import kotlinx.coroutines.delay

object VpnController {

    /**
     * Whether the DNS/VPN engine is part of the protection right now.
     *
     * Cached rather than read on every call: [start] is called from a BroadcastReceiver
     * and from the Quick Settings tile, and a blocking DataStore read there would stall
     * the caller. [refreshEngineEnabled] keeps it honest from a coroutine.
     *
     * The default is `true` so a protection that was on is never dropped because a read
     * failed.
     */
    @Volatile
    private var engineEnabled: Boolean = true

    /** True when the DNS filter may run. */
    fun isEngineEnabled(): Boolean = engineEnabled

    /** Reads the setting and caches it. Call from a coroutine. */
    suspend fun refreshEngineEnabled(context: Context): Boolean {
        val enabled = runCatching {
            context.sawnContainer.settings.protectionSnapshot().vpnEnabled
        }.getOrDefault(true)
        engineEnabled = enabled
        return enabled
    }

    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    /**
     * Starts the filter. A background start on Android 12+ can be refused
     * (ForegroundServiceStartNotAllowedException); fall back to a plain start, which still
     * works while the app is in the foreground, instead of reporting a false failure.
     *
     * In the light mode there is no DNS filter to run: the accessibility engines carry
     * the protection. The start is refused before anything is asked of the system, so the
     * VPN permission is never requested in that mode.
     */
    fun start(context: Context): Boolean {
        if (!engineEnabled) return false
        val intent = Intent(context, SawnVpnService::class.java)
            .setAction(SawnVpnService.ACTION_START)
        if (runCatching { context.startForegroundService(intent) }.isSuccess) return true
        return runCatching { context.startService(intent) }.isSuccess
    }

    suspend fun startAndAwait(context: Context, timeoutMs: Long = 6_000L): Boolean {
        if (!refreshEngineEnabled(context)) return false
        if (SawnVpnService.running) return true
        if (!start(context)) return false
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (SawnVpnService.running) return true
            delay(120L)
        }
        return SawnVpnService.running
    }

    fun stop(context: Context): Boolean {
        val intent = Intent(context, SawnVpnService::class.java)
        return runCatching { context.stopService(intent) }.isSuccess
    }
}
