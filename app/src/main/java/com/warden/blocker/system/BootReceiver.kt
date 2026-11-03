package com.warden.blocker.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.warden.blocker.vpn.VpnController
import com.warden.blocker.vpn.WardenVpnService
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-arms the DNS filter after a reboot or an app update if protection should be live. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Watchdog backstop runs after every boot so a stale "protection stopped"
                // notification from an accessibility loss during shutdown gets resolved.
                GuardHealthWorker.schedule(context)
                val settings = context.wardenContainer.settings
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    // The monotonic anchor belongs to the previous boot; rebuild it from the
                    // wall deadline protected by the high-water mark.
                    settings.reanchorLockdown()
                }
                val lockdownActive = settings.isLockdownActiveNow()
                if (lockdownActive) {
                    // A lock must never survive with a weak filter: restore the core flags
                    // atomically, then report a missing admin immediately.
                    settings.enforceLockdownCore()
                    if (!AdminManager.isActive(context)) {
                        GuardHealthNotifier.notifyAdminDown(context)
                    }
                }
                // Re-arm if blocking is active for any reason — master, schedule, focus,
                // or a live lockdown. Consent must have been granted previously.
                val active = context.wardenContainer.blockEngine.isBlockingActiveNow()
                if (active && VpnController.consentIntent(context) == null) {
                    VpnController.start(context)
                } else if (lockdownActive && !WardenVpnService.running) {
                    // The worker will surface this if the best-effort start above did not
                    // happen (consent missing) or the tunnel died immediately.
                    GuardHealthNotifier.notifyVpnDown(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
