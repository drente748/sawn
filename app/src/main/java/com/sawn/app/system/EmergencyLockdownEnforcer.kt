package com.sawn.app.system

import android.content.Context
import android.os.SystemClock
import com.sawn.app.util.CriticalApps
import com.sawn.core.data.SettingsStore
import com.sawn.domain.lockdown.EmergencyLockdown
import com.sawn.domain.lockdown.LockdownReason
import com.sawn.domain.lockdown.LockdownSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The panic lock, in one place.
 *
 * Three kinds of caller read it: the Quick Settings tile, the app's screen, and the two
 * engines that enforce it (the DNS filter and the accessibility service). The engines
 * read it from a hot path — a DNS packet, an accessibility event — so the session is
 * cached in a volatile field and kept current by [watch], while [isLockdownActive]
 * reads the store directly for the callers that can afford to wait.
 *
 * The lock cannot be cancelled: [start] and [extend] only ever add time. The only way
 * out is the timer, which is why the session is written with a *monotonic* deadline.
 *
 * The store is passed in rather than reached for, so the engines and the tests read
 * the same state through the same code.
 */
object EmergencyLockdownEnforcer {

    /**
     * The packages that stay reachable under the panic lock.
     *
     * A lock that blocked the phone, the messages, the camera or the keyboard would
     * leave the user unable to call for help — the lock protects them, it must not
     * strand them.
     */
    private val SYSTEM_ESSENTIALS = setOf(
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.cellbroadcastreceiver",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.camera2",
        "com.android.camera",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.nfc",
        "com.android.nfc.cardemulation",
        "com.google.android.marvin.talkback",
    )

    /** The cached session, for the paths that cannot suspend. */
    @Volatile private var cached: LockdownSession? = null

    /** The monotonic clock every session is written in. */
    private fun now(): Long = SystemClock.elapsedRealtime()

    /** Whether the panic lock is running right now. */
    suspend fun isLockdownActive(settings: SettingsStore): Boolean {
        val session = reanchorIfStale(settings, settings.emergencyLockdownSession.first())
        cached = session
        return EmergencyLockdown.isActive(session, now())
    }

    /** The cached answer, for the hot paths (a DNS packet, an accessibility event). */
    fun isPanicActive(): Boolean = EmergencyLockdown.isActive(cached, now())

    /** True when the package must stay reachable even under the panic lock. */
    fun isEssential(context: Context, packageName: String): Boolean =
        CriticalApps.isCritical(context, packageName) || packageName in SYSTEM_ESSENTIALS

    /** Starts a panic lock and arms the timer that ends it. */
    suspend fun start(
        settings: SettingsStore,
        context: Context,
        durationMinutes: Int,
        reason: LockdownReason,
    ): LockdownSession {
        val session = EmergencyLockdown.start(
            durationMinutes = durationMinutes,
            now = now(),
            reason = reason,
            bootCount = settings.currentBootCount(),
            // The wall deadline is the part that survives a reboot.
            wallEndsAt = settings.trustedNow() + durationMinutes * 60_000L,
        )
        settings.saveLockdownSession(session)
        cached = session
        EmergencyLockdownWorker.schedule(context, durationMinutes)
        return session
    }

    /** Adds time to a running lock. A lock can only get longer. */
    suspend fun extend(
        settings: SettingsStore,
        context: Context,
        extraMinutes: Int,
    ): LockdownSession? {
        val current = reanchorIfStale(settings, settings.emergencyLockdownSession.first())
            ?: return null
        val extended = EmergencyLockdown.extend(current, extraMinutes)
        settings.saveLockdownSession(extended)
        cached = extended
        EmergencyLockdownWorker.schedule(context, extended.durationMinutes)
        return extended
    }

    /** Ends the lock — the timer does this, never the user. */
    suspend fun clear(settings: SettingsStore) {
        settings.clearLockdownSession()
        cached = null
    }

    /** Keeps the cached session current for the hot paths. */
    fun watch(settings: SettingsStore, scope: CoroutineScope) {
        scope.launch {
            settings.emergencyLockdownSession.collectLatest { session ->
                cached = session
            }
        }
    }

    /**
     * Re-anchors a session that was carried over a reboot.
     *
     * The monotonic clock resets at every boot, so the stored deadline belongs to a
     * boot that no longer exists. The wall deadline is what survived; the session is
     * rewritten around it, keeping `endsAt - startedAt == durationMinutes * 60_000`
     * exactly, so the countdown and the enforcement still agree.
     */
    private suspend fun reanchorIfStale(
        settings: SettingsStore,
        session: LockdownSession?,
    ): LockdownSession? {
        val boot = settings.currentBootCount()
        if (session == null || session.bootCount == boot) return session
        val remaining = session.wallEndsAt - settings.trustedNow()
        if (remaining <= 0L) {
            // The lock was over while the device was off — nothing to restore.
            settings.clearLockdownSession()
            return null
        }
        val durationMs = session.durationMinutes * 60_000L
        val reanchored = session.copy(
            startedAt = now() - (durationMs - remaining),
            endsAt = now() + remaining,
            bootCount = boot,
        )
        settings.saveLockdownSession(reanchored)
        return reanchored
    }
}
