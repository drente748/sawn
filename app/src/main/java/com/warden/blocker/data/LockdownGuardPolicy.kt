package com.warden.blocker.data

import com.warden.blocker.block.LockdownPolicy

/**
 * Pure decision helper for the store-level lockdown guard.
 *
 * The lockdown is absolute: while it is active, no code path may turn protection
 * off or drop the PIN that gates it. The UI already refuses those actions, but
 * the UI is not the enforcement point — the store is. Quick Settings tile,
 * the launcher activity and any future caller go through the store.
 */
object LockdownGuardPolicy {

    /** True when an off-request must be refused because a lockdown is live. */
    fun blocksDisable(lockdownUntil: Long, now: Long): Boolean =
        LockdownPolicy.isActive(lockdownUntil, now)

    /** True when PIN removal must be refused because a lockdown is live. */
    fun blocksPinRemoval(lockdownUntil: Long, now: Long): Boolean =
        LockdownPolicy.isActive(lockdownUntil, now)

    /** True when strict-mode shutdown must be refused because a lockdown is live. */
    fun blocksStrictChange(lockdownUntil: Long, now: Long): Boolean =
        LockdownPolicy.isActive(lockdownUntil, now)
}
