package com.sawn.domain.block

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * The time lock (القفل الزمني) must never be shortened.
 *
 * This is the one promise that cannot be broken by any code path, a stale clock or a
 * corrupted preference: while a lock is live, the only accepted write is an extension.
 */
class LockdownPolicyPropertyTest : StringSpec({

    /** A lock that has not started yet may be set to anything. */
    "an expired lock accepts any new deadline" {
        checkAll(Arb.long(0L, 10_000L), Arb.long(0L, 10_000L)) { now, newUntil ->
            LockdownPolicy.mayApplyLockdown(currentUntil = 0L, newUntil = newUntil, now = now) shouldBe true
        }
    }

    /** While the lock is live, a shorter deadline is refused. */
    "a live lock refuses to be shortened" {
        checkAll(
            Arb.long(1_000L, 1_000_000L),
            Arb.long(0L, 999L),
        ) { now, delta ->
            val currentUntil = now + 10_000L
            LockdownPolicy.mayApplyLockdown(currentUntil, currentUntil - delta, now) shouldBe false
        }
    }

    /** While the lock is live, the same deadline is refused too — it is not an extension. */
    "a live lock refuses to be kept as it is" {
        checkAll(Arb.long(1_000L, 1_000_000L)) { now ->
            val currentUntil = now + 10_000L
            LockdownPolicy.mayApplyLockdown(currentUntil, currentUntil, now) shouldBe false
        }
    }

    /** Only a strictly later deadline is accepted while the lock is live. */
    "a live lock accepts an extension" {
        checkAll(
            Arb.long(1_000L, 1_000_000L),
            Arb.long(1L, 10_000L),
        ) { now, delta ->
            val currentUntil = now + 10_000L
            LockdownPolicy.mayApplyLockdown(currentUntil, currentUntil + delta, now) shouldBe true
        }
    }

    /** The lock is active exactly while its deadline is in the future. */
    "a lock is active exactly while it is in the future" {
        checkAll(Arb.long(0L, 1_000_000L)) { until ->
            LockdownPolicy.isActive(until, now = until - 1L) shouldBe true
            LockdownPolicy.isActive(until, now = until) shouldBe false
            LockdownPolicy.isActive(until, now = until + 1L) shouldBe false
        }
    }

    /** A manual duration outside the allowed range is refused. */
    "a manual duration outside the range is refused" {
        // Below the minimum (one minute).
        checkAll(Arb.long(0L, LockdownPolicy.MIN_MINUTES - 1L)) { minutes ->
            LockdownPolicy.validateManual(minutes, LockdownPolicy.DurationUnit.MINUTES) shouldBe null
        }
        // Beyond twelve months.
        checkAll(Arb.long(maxMinutes + 1L, maxMinutes + 10_000L)) { minutes ->
            LockdownPolicy.validateManual(minutes, LockdownPolicy.DurationUnit.MINUTES) shouldBe null
        }
        // Inside the range: the duration is converted exactly.
        checkAll(Arb.long(LockdownPolicy.MIN_MINUTES, maxMinutes)) { minutes ->
            LockdownPolicy.validateManual(minutes, LockdownPolicy.DurationUnit.MINUTES) shouldBe
                (minutes * 60_000L)
        }
    }

    /**
     * INVARIANT 1 — a live lock is never shortened.
     *
     * If this broke, the user could shorten the very lock they set for themselves, and
     * every promise the time lock makes would be worthless. The property is stated over
     * *every* candidate deadline: an accepted write is always strictly later than the
     * deadline that is already running.
     */
    "a live lock is never shortened, whatever the candidate deadline" {
        checkAll(
            Arb.long(1_000L, 1_000_000L),
            Arb.long(0L, 2_000_000L),
        ) { now, candidate ->
            val currentUntil = now + 10_000L
            val accepted = LockdownPolicy.mayApplyLockdown(currentUntil, candidate, now)
            if (accepted) {
                (candidate > currentUntil) shouldBe true
            } else {
                (candidate <= currentUntil) shouldBe true
            }
        }
    }

    /**
     * INVARIANT 2 — an extension is cumulative.
     *
     * The duration is added to the time that is *left*, not to the moment the user
     * pressed the button: extending a lock by an hour must never eat into the hours
     * that were already paid for. The extension is also accepted by the store.
     */
    "an extension adds to the time that is left" {
        checkAll(
            Arb.long(1_000L, 1_000_000L),
            Arb.long(1_000L, 86_400_000L),
        ) { now, duration ->
            val currentUntil = now + 10_000L
            val extended = LockdownPolicy.extend(currentUntil, duration, now)
            extended shouldBe currentUntil + duration
            LockdownPolicy.mayApplyLockdown(currentUntil, extended, now) shouldBe true
        }
    }

    /** An expired lock has no time left, so the extension starts from now. */
    "an extension of an expired lock starts from now" {
        checkAll(
            Arb.long(0L, 10_000L),
            Arb.long(1_000L, 86_400_000L),
        ) { now, duration ->
            LockdownPolicy.extend(currentUntil = 0L, durationMs = duration, now = now) shouldBe
                (now + duration)
        }
    }
})

/** Twelve months, expressed in minutes — the longest duration the policy accepts. */
private val maxMinutes = LockdownPolicy.MAX_MONTHS * 30L * 24L * 60L
