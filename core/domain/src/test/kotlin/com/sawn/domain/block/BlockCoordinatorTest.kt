package com.sawn.domain.block

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The coordinator is the one place that answers "what is protecting me, and is this
 * host blocked?". These tests pin the two rules the whole app depends on.
 */
class BlockCoordinatorTest : StringSpec({

    "no engine at all means nothing is protected" {
        ProtectionPolicy.level(emptySet(), paused = false) shouldBe ProtectionLevel.None
    }

    "the DNS engine alone earns the full badge" {
        ProtectionPolicy.level(setOf(BlockingEngineType.DNS), paused = false) shouldBe
            ProtectionLevel.Full
    }

    "an accessibility engine without the DNS filter is browser only" {
        ProtectionPolicy.level(setOf(BlockingEngineType.BROWSER), paused = false) shouldBe
            ProtectionLevel.BrowserOnly
        ProtectionPolicy.level(setOf(BlockingEngineType.APP), paused = false) shouldBe
            ProtectionLevel.BrowserOnly
    }

    "pausing wins over every engine" {
        ProtectionPolicy.level(setOf(BlockingEngineType.DNS), paused = true) shouldBe
            ProtectionLevel.Paused
        ProtectionPolicy.level(emptySet(), paused = true) shouldBe ProtectionLevel.Paused
    }

    "a subdomain of a blocked domain is blocked" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        coordinator.evaluateDomain("www.example.com").blocked shouldBe true
        coordinator.evaluateDomain("example.com").blocked shouldBe true
    }

    "a longer label is not a subdomain" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        coordinator.evaluateDomain("notexample.com").blocked shouldBe false
        coordinator.evaluateDomain("example.com.evil.net").blocked shouldBe false
    }

    "a host nobody blocked is left alone" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        val decision = coordinator.evaluateDomain("wikipedia.org")
        decision.blocked shouldBe false
        decision.matchedDomain shouldBe null
    }

    "text that is not a host is never a host" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        coordinator.evaluateDomain("how to block a site").blocked shouldBe false
        coordinator.evaluateDomain(null).blocked shouldBe false
    }

    "a paused coordinator blocks nothing" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
            paused = true,
        )
        val coordinator = DefaultBlockCoordinator(env)

        coordinator.evaluateDomain("example.com").blocked shouldBe false
        coordinator.getProtectionLevel() shouldBe ProtectionLevel.Paused
    }

    "with the DNS filter live the DNS engine owns the decision" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.DNS, BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        val decision = coordinator.evaluateDomain("example.com")
        decision.blocked shouldBe true
        decision.source shouldBe BlockSource.DNS
    }

    "without the DNS filter the browser engine owns the decision" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        coordinator.evaluateDomain("example.com").source shouldBe BlockSource.BROWSER
    }

    "reporting a block reaches the counter" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        val decision = coordinator.evaluateDomain("example.com")
        coordinator.reportBlock(decision, BlockSource.BROWSER)
        env.reported shouldBe 1
    }

    "reporting a non-block is not a block" {
        val env = FakeEnv(
            domains = setOf("example.com"),
            engines = setOf(BlockingEngineType.BROWSER),
        )
        val coordinator = DefaultBlockCoordinator(env)

        val decision = coordinator.evaluateDomain("wikipedia.org")
        coordinator.reportBlock(decision, BlockSource.BROWSER)
        env.reported shouldBe 0
    }
})

/** A [BlockCoordinatorEnv] with fixed answers, so the rules above can be read alone. */
private class FakeEnv(
    private val domains: Set<String>,
    private val engines: Set<BlockingEngineType>,
    private val paused: Boolean = false,
) : BlockCoordinatorEnv {
    var reported = 0
        private set

    override suspend fun activeEngines(): Set<BlockingEngineType> = engines
    override suspend fun isPaused(): Boolean = paused
    override suspend fun blockedDomains(): Set<String> = domains
    override suspend fun recordBlock(decision: BlockDecision, source: BlockSource) {
        reported++
    }
}
