package com.warden.blocker.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import kotlinx.coroutines.delay

/** Thin facade the UI uses to start/stop the filtering tunnel. */
object VpnController {

    /**
     * Returns an intent to show the system VPN-consent dialog, or null if already granted.
     * The caller must launch it and call [start] once the result is OK.
     */
    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, WardenVpnService::class.java)
            .setAction(WardenVpnService.ACTION_START)
        context.startForegroundService(intent)
    }

    /**
     * Start the tunnel and wait briefly for `establish()` to succeed. Focus sessions are
     * persisted only after this returns true, so denying consent (or a failed start) cannot
     * leave a strict session with no filter behind it.
     */
    suspend fun startAndAwait(context: Context, timeoutMs: Long = 5_000L): Boolean {
        if (WardenVpnService.running) return true
        start(context)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (WardenVpnService.running) return true
            delay(100L)
        }
        return WardenVpnService.running
    }

    fun stop(context: Context) {
        val intent = Intent(context, WardenVpnService::class.java)
            .setAction(WardenVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
