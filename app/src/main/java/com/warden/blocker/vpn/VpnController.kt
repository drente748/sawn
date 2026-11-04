package com.warden.blocker.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import kotlinx.coroutines.delay

object VpnController {

    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    /**
     * Starts the filter. A background start on Android 12+ can be refused
     * (ForegroundServiceStartNotAllowedException); fall back to a plain start, which still
     * works while the app is in the foreground, instead of reporting a false failure.
     */
    fun start(context: Context): Boolean {
        val intent = Intent(context, WardenVpnService::class.java)
            .setAction(WardenVpnService.ACTION_START)
        if (runCatching { context.startForegroundService(intent) }.isSuccess) return true
        return runCatching { context.startService(intent) }.isSuccess
    }

    suspend fun startAndAwait(context: Context, timeoutMs: Long = 6_000L): Boolean {
        if (WardenVpnService.running) return true
        if (!start(context)) return false
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (WardenVpnService.running) return true
            delay(120L)
        }
        return WardenVpnService.running
    }

    fun stop(context: Context): Boolean {
        val intent = Intent(context, WardenVpnService::class.java)
        return runCatching { context.stopService(intent) }.isSuccess
    }
}
