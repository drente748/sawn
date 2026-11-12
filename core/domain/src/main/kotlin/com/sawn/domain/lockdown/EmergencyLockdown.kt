package com.sawn.domain.lockdown

import java.util.UUID

/**
 * The panic lock: full blocking for a fixed time, with **no way out**.
 *
 * The rule is deliberately one-sided. The user presses the button in the moment they
 * cannot be trusted with their own decision, so the lock that answers that moment
 * must not be cancellable by the same person one minute later:
 *
 *  - [canCancel] is always false;
 *  - [extend] only ever adds time;
 *  - the countdown runs on a monotonic clock, so moving the system clock does not
 *    shorten it.
 *
 * The only thing that ends the lock is the timer — and the app, once the timer is
 * done, returns to whatever protection the user had before.
 */
object EmergencyLockdown {

    /** The shortest lock the app offers. */
    const val MIN_MINUTES = 15

    /** The durations the interface offers. */
    val DURATIONS_MINUTES = listOf(15, 30, 60)

    /**
     * Starts a session that ends [durationMinutes] after [now].
     *
     * @param now a monotonic clock reading — elapsed realtime, never wall time.
     */
    fun start(
        durationMinutes: Int,
        now: Long,
        reason: LockdownReason = LockdownReason.PANIC,
        id: String = UUID.randomUUID().toString(),
        bootCount: Int = 0,
        wallEndsAt: Long = 0L,
    ): LockdownSession = LockdownSession(
        id = id,
        startedAt = now,
        endsAt = now + durationMinutes * 60_000L,
        durationMinutes = durationMinutes,
        reason = reason,
        bootCount = bootCount,
        wallEndsAt = wallEndsAt,
    )

    /**
     * Whether the session may be cancelled.
     *
     * **Never.** The whole promise of the panic lock is that it cannot be shortened
     * or cleared before its timer ends; the only way out is the timer. The parameters
     * are part of the shape a future policy would need — today they change nothing.
     */
    @Suppress("UNUSED_PARAMETER")
    fun canCancel(session: LockdownSession, now: Long): Boolean = false

    /**
     * Extends the session by [extraMinutes].
     *
     * The extension is cumulative: the new deadline is the old one **plus** the extra
     * time, and the recorded duration grows with it so the two always agree. A
     * non-positive extension changes nothing.
     */
    fun extend(session: LockdownSession, extraMinutes: Int): LockdownSession {
        if (extraMinutes <= 0) return session
        return session.copy(
            endsAt = session.endsAt + extraMinutes * 60_000L,
            durationMinutes = session.durationMinutes + extraMinutes,
        )
    }

    /** Whether the session is still running at [now]. */
    fun isActive(session: LockdownSession?, now: Long): Boolean =
        session != null && session.endsAt > now

    /** The millis left at [now] — zero once the session is over. */
    fun remainingMillis(session: LockdownSession?, now: Long): Long =
        if (session == null) 0L else (session.endsAt - now).coerceAtLeast(0L)
}
