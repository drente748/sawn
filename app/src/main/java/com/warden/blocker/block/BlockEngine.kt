package com.warden.blocker.block

import com.warden.blocker.data.BlockRepository
import com.warden.blocker.data.ProtectionSnapshot
import com.warden.blocker.data.SettingsStore
import java.util.Calendar
import com.warden.blocker.block.AdultHosts

/**
 * Answers "is blocking enforced right now?" from the master switch + schedules. The VPN
 * asks it for [blockedDomains]; per-app decisions go through [AccessController].
 *
 * Time is read through [SettingsStore.trustedNow] — a monotonic high-water mark — so
 * rolling the device clock back cannot hide active schedules or extend focus sessions.
 */
class BlockEngine(
    private val repo: BlockRepository,
    private val settings: SettingsStore,
) {
    /**
     * True when blocking is enforced right now. A focus session forces it on for its
     * duration; otherwise it's the master switch AND (always-on OR an active schedule).
     *
     * [peekOnly] uses the read-only clock so hot callers (the VPN refresher) cannot write
     * DataStore and re-trigger the flows watching it.
     */
    suspend fun isBlockingActiveNow(nowCal: Calendar? = null, peekOnly: Boolean = false): Boolean {
        val snapshot = settings.protectionSnapshot()
        val now = nowCal?.timeInMillis ?: if (peekOnly) settings.peekNow() else settings.trustedNow()
        return isActive(snapshot, now, nowCal)
    }

    private suspend fun isActive(
        snapshot: ProtectionSnapshot,
        now: Long,
        nowCal: Calendar?,
    ): Boolean {
        val cal = nowCal ?: Calendar.getInstance().apply { timeInMillis = now }
        return BlockingPolicy.isActive(
            masterEnabled = snapshot.masterEnabled,
            alwaysOn = snapshot.alwaysOn,
            focusEndsAt = snapshot.focusEndsAt,
            now = now,
            anyScheduleActive = ScheduleEvaluator.anyActive(repo.enabledSchedules(), cal),
        )
    }

    suspend fun blockedDomains(readOnly: Boolean = false): List<String> {
        val snapshot = settings.protectionSnapshot()
        val now = if (readOnly) settings.peekNow() else settings.trustedNow()
        if (!isActive(snapshot, now, null)) return emptyList()
        val domains = repo.enabledDomains().toMutableList()
        if (snapshot.pornGuard) {
            domains += AdultHosts.ALL
            domains += AdultHosts.REDDIT_MEDIA
        }
        if (snapshot.blockDoh) domains += DohHosts.ALL
        if (snapshot.blockAds) domains += AdHosts.ALL
        return domains.distinct()
    }
}
