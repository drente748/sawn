package com.sawn.domain.lockdown

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * The panic lock, as properties.
 *
 * These are the promises the feature makes to a user in a bad moment: the lock runs
 * for exactly the time they were told, it cannot be cancelled, and it cannot be
 * shortened — not by the interface, and not by moving the system clock.
 */
class EmergencyLockdownTest : StringSpec({

    /**
     * INVARIANT 1 — the session ends exactly when it says it ends.
     *
     * The deadline and the recorded duration are the same fact written twice; if they
     * drift apart, the countdown and the enforcement disagree about when the lock is
     * over.
     */
    "a session ends exactly durationMinutes after it started" {
        checkAll(Arb.int(1, 24 * 60), Arb.long(0L, 1_000_000_000L)) { minutes, now ->
            val session = EmergencyLockdown.start(minutes, now)
            session.endsAt - session.startedAt shouldBe minutes * 60_000L
            session.durationMinutes shouldBe minutes
        }
    }

    /**
     * INVARIANT 2 — the lock can never be cancelled.
     *
     * If this broke, the panic button would be a suggestion: the person who pressed
     * it would be the one person able to undo it.
     */
    "the lock can never be cancelled before it ends" {
        checkAll(Arb.int(1, 24 * 60), Arb.long(0L, 1_000_000_000L)) { minutes, now ->
            val session = EmergencyLockdown.start(minutes, now)
            EmergencyLockdown.canCancel(session, now) shouldBe false
            EmergencyLockdown.canCancel(session, session.endsAt - 1L) shouldBe false
            EmergencyLockdown.canCancel(session, session.endsAt) shouldBe false
        }
    }

    /**
     * INVARIANT 3 — extending never shortens the lock.
     *
     * The extension is cumulative: the deadline moves *later* and the recorded
     * duration grows with it, so the two still agree.
     */
    "extending never shortens the deadline" {
        checkAll(
            Arb.int(1, 24 * 60),
            Arb.int(0, 120),
            Arb.long(0L, 1_000_000_000L),
        ) { minutes, extra, now ->
            val session = EmergencyLockdown.start(minutes, now)
            val extended = EmergencyLockdown.extend(session, extra)
            (extended.endsAt >= session.endsAt) shouldBe true
            extended.endsAt - extended.startedAt shouldBe extended.durationMinutes * 60_000L
        }
    }

    /** The lock is active right up to its deadline, and not a moment longer. */
    "the lock is active until its deadline and not a moment longer" {
        checkAll(Arb.int(1, 24 * 60), Arb.long(0L, 1_000_000_000L)) { minutes, now ->
            val session = EmergencyLockdown.start(minutes, now)
            EmergencyLockdown.isActive(session, session.endsAt - 1L) shouldBe true
            EmergencyLockdown.isActive(session, session.endsAt) shouldBe false
            EmergencyLockdown.isActive(session, session.endsAt + 1L) shouldBe false
            EmergencyLockdown.isActive(null, now) shouldBe false
        }
    }
})
