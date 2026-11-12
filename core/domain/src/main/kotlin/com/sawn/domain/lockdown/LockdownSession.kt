package com.sawn.domain.lockdown

/** Why an emergency lockdown was started. */
enum class LockdownReason {
    /** The panic button: the user decided, in the moment, to stop themselves. */
    PANIC,

    /** Started from the app's emergency lockdown screen. */
    EMERGENCY,

    /** Started from the Quick Settings tile. */
    QS_TILE,
}

/**
 * One emergency lockdown session — the panic lock.
 *
 * The clock in [startedAt] and [endsAt] is the **monotonic** one (elapsed realtime).
 * That is deliberate: the user can change the system clock, and a wall clock would
 * let them shorten the very lock they set for themselves. A monotonic clock resets at
 * every boot, so the session also carries [wallEndsAt] — the deadline in wall time,
 * which is the part that survives a reboot and is re-anchored to the monotonic clock
 * when the app comes back.
 *
 * [bootCount] says which boot the monotonic clock belongs to, so a session carried
 * over a reboot is never mistaken for a fresh one.
 */
data class LockdownSession(
    /** Identifies the session, so an extension can be traced to its start. */
    val id: String,
    /** The monotonic clock reading when the lock started. */
    val startedAt: Long,
    /** The monotonic clock reading when the lock ends. */
    val endsAt: Long,
    /** How long the lock runs, in minutes — always `endsAt - startedAt`. */
    val durationMinutes: Int,
    /** What started the lock. */
    val reason: LockdownReason,
    /** Which boot the monotonic clock belongs to. */
    val bootCount: Int = 0,
    /** The wall-clock deadline — the part that survives a reboot. */
    val wallEndsAt: Long = 0L,
)
