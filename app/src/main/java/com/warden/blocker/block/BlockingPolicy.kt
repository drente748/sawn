package com.warden.blocker.block

/** Pure "is blocking enforced right now?" rule, shared by the engine and tests. */
object BlockingPolicy {
    fun isActive(
        masterEnabled: Boolean,
        alwaysOn: Boolean,
        focusEndsAt: Long,
        now: Long,
        anyScheduleActive: Boolean,
    ): Boolean = focusEndsAt > now ||
        (masterEnabled && (alwaysOn || anyScheduleActive))
}
