package com.sawn.domain.system

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * When does a stopped accessibility service count as a failure?
 *
 * The rule matters in both directions: a service that is merely waking up must not
 * raise an alarm, and a service that is really dead must be reported — otherwise the
 * watchdog is decoration.
 */
class GuardHealthPolicyTest : StringSpec({

    /** A service the user never enabled is not the watchdog's business. */
    "a service the user never enabled is never down" {
        checkAll(Arb.boolean(), Arb.boolean(), Arb.long(0L, 600_000L)) { enabled, connected, age ->
            if (!enabled) {
                GuardHealthPolicy.accessibilityState(enabled, connected, age) shouldBe
                    GuardHealthPolicy.AccessibilityState.DISABLED
            }
        }
    }

    /** A bound service is alive, however young the process is. */
    "a connected service is alive" {
        checkAll(Arb.long(0L, 600_000L)) { age ->
            GuardHealthPolicy.accessibilityState(
                enabled = true,
                connected = true,
                processAgeMs = age,
            ) shouldBe GuardHealthPolicy.AccessibilityState.ALIVE
        }
    }

    /**
     * The grace period: for the first seconds of the process's life a missing service
     * is "connecting", not "down".
     *
     * Without it every app launch would raise a false alarm — the system binds the
     * service a moment after the process is alive, and a check that lands in that
     * window would report a protection that is merely waking up.
     */
    "a young process is still connecting, never down" {
        checkAll(Arb.long(0L, GuardHealthPolicy.PROCESS_GRACE_MS - 1L)) { age ->
            GuardHealthPolicy.accessibilityState(
                enabled = true,
                connected = false,
                processAgeMs = age,
            ) shouldBe GuardHealthPolicy.AccessibilityState.CONNECTING
        }
    }

    /** Past the grace period, a missing service is really down. */
    "an old process without the service is down" {
        checkAll(Arb.long(GuardHealthPolicy.PROCESS_GRACE_MS, 600_000L)) { age ->
            GuardHealthPolicy.accessibilityState(
                enabled = true,
                connected = false,
                processAgeMs = age,
            ) shouldBe GuardHealthPolicy.AccessibilityState.DOWN
        }
    }

    /**
     * The self-heal is bounded: a service the user or the OEM insists on stopping must
     * not be asked to come back for ever.
     */
    "the self-heal is bounded" {
        checkAll(Arb.long(0L, 100L)) { attempt ->
            val count = attempt.toInt()
            GuardHealthPolicy.shouldSelfHeal(GuardHealthPolicy.AccessibilityState.DOWN, count) shouldBe
                (count < GuardHealthPolicy.MAX_SELF_HEAL_ATTEMPTS)
            GuardHealthPolicy.shouldRetry(count) shouldBe
                (count < GuardHealthPolicy.MAX_SELF_HEAL_ATTEMPTS)
        }
    }

    /** Only a down service is worth healing. */
    "nothing but a down service is healed" {
        checkAll(Arb.long(0L, 100L)) { attempt ->
            val count = attempt.toInt()
            listOf(
                GuardHealthPolicy.AccessibilityState.DISABLED,
                GuardHealthPolicy.AccessibilityState.CONNECTING,
                GuardHealthPolicy.AccessibilityState.ALIVE,
            ).forEach { state ->
                GuardHealthPolicy.shouldSelfHeal(state, count) shouldBe false
            }
        }
    }
})
