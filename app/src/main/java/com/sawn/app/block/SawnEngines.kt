package com.sawn.app.block

import com.sawn.core.data.SettingsStore
import com.sawn.domain.block.BlockingEngineType

/**
 * The engines that are live right now, for the callers that record a protected day.
 *
 * The answer is read from the *runtime*, not from the settings: the user may have the
 * DNS filter switched on while the tunnel is down, and a day that only ever saw a
 * switched-off engine is not a protected day.
 */
object SawnEngines {

    /**
     * The engines enforcing the protection right now.
     *
     * @param vpnRunning whether the DNS tunnel is up.
     * @param accessibilityEnabled whether the accessibility service is enabled.
     */
    suspend fun active(
        settings: SettingsStore,
        vpnRunning: Boolean,
        accessibilityEnabled: Boolean,
    ): Set<BlockingEngineType> = buildSet {
        val snapshot = settings.protectionSnapshot()
        if (snapshot.vpnEnabled && vpnRunning) add(BlockingEngineType.DNS)
        if (snapshot.browserBlockingEnabled && accessibilityEnabled) {
            add(BlockingEngineType.BROWSER)
            add(BlockingEngineType.APP)
        }
    }
}
