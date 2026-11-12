package com.sawn.app.block

import com.sawn.core.data.BlockRepository
import com.sawn.core.data.SettingsStore
import com.sawn.domain.block.BlockCoordinator
import com.sawn.domain.block.BlockCoordinatorEnv
import com.sawn.domain.block.BlockDecision
import com.sawn.domain.block.BlockSource
import com.sawn.domain.block.BlockingEngineType
import com.sawn.domain.block.DefaultBlockCoordinator

/**
 * The Android side of the coordinator: it knows which engines are actually running and
 * which domains the blocklist holds.
 *
 * The rules themselves live in :core:domain ([DefaultBlockCoordinator]) so they can be
 * unit-tested without a phone. This class only supplies the facts.
 */
class SawnBlockCoordinatorEnv(
    private val settings: SettingsStore,
    private val repo: BlockRepository,
    /** Whether the DNS/VPN filter is up right now. */
    private val vpnRunning: () -> Boolean = { false },
    /** Whether the accessibility service is enabled in the system settings. */
    private val accessibilityEnabled: () -> Boolean = { false },
    /** Whether the app-level blocking engine is running. */
    private val appBlockingEnabled: () -> Boolean = { false },
) : BlockCoordinatorEnv {

    override suspend fun activeEngines(): Set<BlockingEngineType> = buildSet {
        val snapshot = settings.protectionSnapshot()
        if (snapshot.vpnEnabled && vpnRunning()) add(BlockingEngineType.DNS)
        if (snapshot.browserBlockingEnabled && accessibilityEnabled()) {
            add(BlockingEngineType.BROWSER)
        }
        if (snapshot.browserBlockingEnabled && appBlockingEnabled()) {
            add(BlockingEngineType.APP)
        }
    }

    override suspend fun isPaused(): Boolean = false

    override suspend fun blockedDomains(): Set<String> = repo.enabledDomains().toSet()

    override suspend fun recordBlock(decision: BlockDecision, source: BlockSource) {
        settings.recordBlockEvent()
    }

    companion object {

        /** Builds the coordinator the app uses. */
        fun create(
            settings: SettingsStore,
            repo: BlockRepository,
            accessibilityEnabled: () -> Boolean,
            appBlockingEnabled: () -> Boolean,
        ): BlockCoordinator = DefaultBlockCoordinator(
            SawnBlockCoordinatorEnv(
                settings = settings,
                repo = repo,
                accessibilityEnabled = accessibilityEnabled,
                appBlockingEnabled = appBlockingEnabled,
            ),
        )
    }
}
