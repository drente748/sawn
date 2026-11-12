package com.sawn.domain.system

/**
 * When does a stopped accessibility service count as a failure?
 *
 * The naive check — "the service is not connected" — produces false alarms right after
 * the process starts: Android binds the service a moment after the process is alive, and
 * a check that lands in that window would report a dead protection that is merely
 * waking up.
 *
 * So a **young process** is never treated as a failure. The grace period is the same
 * idea as the "process-young grace" in the PokeClaw codebase: for the first seconds
 * after the process starts, the answer is "connecting", not "down".
 */
object GuardHealthPolicy {

    /**
     * How long after the process starts a missing service is tolerated.
     *
     * Thirty seconds is comfortably longer than the bind delay observed on slow devices,
     * and short enough that a real outage is still reported promptly.
     */
    const val PROCESS_GRACE_MS = 30_000L

    /** How many self-heal attempts are made before the user is asked to help. */
    const val MAX_SELF_HEAL_ATTEMPTS = 3

    /** What the watchdog knows about the accessibility service. */
    enum class AccessibilityState {
        /** The user has not granted accessibility, so nothing is expected of it. */
        DISABLED,

        /** Granted, but the process is too young to conclude the service is dead. */
        CONNECTING,

        /** Granted and connected. */
        ALIVE,

        /** Granted, the process is old enough, and the service is still not connected. */
        DOWN,
    }

    /**
     * Classifies the accessibility service.
     *
     * @param enabled whether the user granted accessibility for this app.
     * @param connected whether the service is currently connected.
     * @param processAgeMs how long the app process has been alive.
     */
    fun accessibilityState(
        enabled: Boolean,
        connected: Boolean,
        processAgeMs: Long,
    ): AccessibilityState = when {
        !enabled -> AccessibilityState.DISABLED
        connected -> AccessibilityState.ALIVE
        // Too young to be sure: the service may still be binding.
        processAgeMs < PROCESS_GRACE_MS -> AccessibilityState.CONNECTING
        else -> AccessibilityState.DOWN
    }

    /**
     * Whether to try to bring the service back.
     *
     * Bounded on purpose: an unbounded retry loop against a service the user (or the OEM)
     * has decided to stop would drain the battery without ever succeeding.
     */
    fun shouldSelfHeal(state: AccessibilityState, attempt: Int): Boolean =
        state == AccessibilityState.DOWN && attempt < MAX_SELF_HEAL_ATTEMPTS

    /**
     * Whether the watchdog should ask WorkManager to run again sooner.
     *
     * Once the attempts are spent, the watchdog stops retrying and leaves the user with
     * the notification — further retries would only be noise.
     */
    fun shouldRetry(attempt: Int): Boolean = attempt < MAX_SELF_HEAL_ATTEMPTS
}
