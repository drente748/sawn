package com.sawn.domain.block

import com.sawn.domain.block.web.HostNormalizer

/**
 * The production [BlockCoordinator]. Pure Kotlin: every touch of the device world comes
 * through [BlockCoordinatorEnv], so the rules below are unit-testable without a phone.
 *
 * The engines themselves stay where they belong — the DNS filter lives in the VPN
 * service, the browser and app engines in the accessibility service. This class only
 * decides *what* should be blocked and *which* engine owns that decision.
 */
class DefaultBlockCoordinator(private val env: BlockCoordinatorEnv) : BlockCoordinator {

    override suspend fun getActiveEngines(): Set<BlockingEngineType> = env.activeEngines()

    override suspend fun getProtectionLevel(): ProtectionLevel =
        ProtectionPolicy.level(env.activeEngines(), env.isPaused())

    override suspend fun evaluateDomain(host: String?): BlockDecision {
        val normalized = HostNormalizer.normalizeHost(host)
            ?: return BlockDecision(host, blocked = false, source = null)
        if (env.isPaused()) {
            return BlockDecision(normalized, blocked = false, source = null)
        }
        val matched = firstMatch(normalized) ?: return BlockDecision(normalized, blocked = false, source = null)
        // The DNS filter owns the decision whenever it is live: it sees the lookup before
        // any address bar is drawn, so blocking twice would only produce a second,
        // confusing screen for the user.
        val source = if (BlockingEngineType.DNS in env.activeEngines()) {
            BlockSource.DNS
        } else {
            BlockSource.BROWSER
        }
        return BlockDecision(normalized, blocked = true, source = source, matchedDomain = matched)
    }

    override suspend fun reportBlock(decision: BlockDecision, source: BlockSource) {
        if (!decision.blocked) return
        env.recordBlock(decision, source)
    }

    /**
     * The blocklist entry that covers [host]. Subdomains count ("www." and "m." are
     * stripped), but a longer label does not ("notexample.com" is not "example.com").
     */
    private suspend fun firstMatch(host: String): String? =
        env.blockedDomains().firstOrNull { entry ->
            DomainMatcher.matches(host, setOf(entry))
        }
}
