package com.sawn.domain.block

/**
 * Engines that can enforce a block.
 *
 * - [DNS] — the VPN/DNS filter. It sees every lookup the device makes, so it also covers
 *   WebViews and apps that never show an address bar. It costs battery and needs the
 *   user's VPN consent.
 * - [BROWSER] — the accessibility browser engine. It reads the address bar of known
 *   browsers and blocks what it sees. Free of extra permissions, but blind to WebViews.
 * - [APP] — the accessibility app engine. It blocks whole applications by package name.
 */
enum class BlockingEngineType {
    DNS,
    BROWSER,
    APP,
}

/** The engine that produced (or should produce) a block. */
enum class BlockSource {
    DNS,
    BROWSER,
    APP,
}

/**
 * Coarse protection status, shown as a badge so the user always knows what is actually
 * watching for them.
 *
 * - [Full] — the DNS filter is running: every lookup is checked, in every app.
 * - [BrowserOnly] — only the accessibility engines are live. Websites are still blocked
 *   in known browsers, but WebViews inside other apps are not seen.
 * - [None] — no engine is live. Nothing is being blocked.
 * - [Paused] — protection was deliberately turned off (or is waiting for its window).
 */
enum class ProtectionLevel {
    Full,
    BrowserOnly,
    None,
    Paused,
}

/** The outcome of evaluating one host against the active policy. */
data class BlockDecision(
    /** The normalized host, or the raw text when it could not be normalized. */
    val host: String?,
    /** True when the host must not be reached. */
    val blocked: Boolean,
    /** The engine that enforces (or would enforce) this decision. */
    val source: BlockSource?,
    /** The blocklist entry that matched, when there was one. */
    val matchedDomain: String? = null,
)

/**
 * The single place that answers "what is protecting me right now, and is this host
 * blocked?".
 *
 * Every caller — the VPN filter, the accessibility engines, the UI badge — goes through
 * this one contract, so the engines can never disagree about the current state.
 */
interface BlockCoordinator {
    /** The engines that are live right now. */
    suspend fun getActiveEngines(): Set<BlockingEngineType>

    /** The coarse status derived from [getActiveEngines]. */
    suspend fun getProtectionLevel(): ProtectionLevel

    /** Evaluates [host] against the active policy and the blocklist. */
    suspend fun evaluateDomain(host: String?): BlockDecision

    /** Reports that [decision] was actually enforced by [source]. */
    suspend fun reportBlock(decision: BlockDecision, source: BlockSource)
}

/**
 * Everything the coordinator needs from the outside world.
 *
 * Kept as an interface so the coordinator itself stays pure Kotlin (no Android types)
 * and can be unit-tested without a device.
 */
interface BlockCoordinatorEnv {
    /** The engines that are live right now. */
    suspend fun activeEngines(): Set<BlockingEngineType>

    /** True when the user deliberately turned protection off. */
    suspend fun isPaused(): Boolean

    /** The domains that must be blocked (blocklist + adult list, as configured). */
    suspend fun blockedDomains(): Set<String>

    /** Called when a block was enforced, so the counters can be updated. */
    suspend fun recordBlock(decision: BlockDecision, source: BlockSource)
}

/**
 * Pure mapping from engine state to the badge the user sees.
 *
 * Kept separate from [DefaultBlockCoordinator] so the rules are unit-testable on their
 * own and cannot drift from the coordinator that uses them.
 */
object ProtectionPolicy {

    /**
     * - paused wins over everything: the user asked for silence.
     * - no engine at all means nothing is being blocked.
     * - the DNS filter covers every app, so it is the only engine that earns "full".
     * - any accessibility engine without the DNS filter is "browser only" — real
     *   protection, but blind to WebViews.
     */
    fun level(engines: Set<BlockingEngineType>, paused: Boolean): ProtectionLevel = when {
        paused -> ProtectionLevel.Paused
        engines.isEmpty() -> ProtectionLevel.None
        BlockingEngineType.DNS in engines -> ProtectionLevel.Full
        BlockingEngineType.BROWSER in engines || BlockingEngineType.APP in engines ->
            ProtectionLevel.BrowserOnly
        else -> ProtectionLevel.None
    }
}
