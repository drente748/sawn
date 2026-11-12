package com.sawn.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sawn.app.R
import com.sawn.app.sawnContainer
import com.sawn.app.system.EmergencyLockdownEnforcer
import com.sawn.core.data.SettingsStore
import com.sawn.domain.lockdown.LockdownReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The panic tile: one tap in the shade starts a full lockdown.
 *
 * A tap starts the shortest lock the app offers (15 minutes). There is no way to turn
 * it off from here: the tile can only *start* a lock, and the running lock is shown as
 * active. The duration picker lives in the app, where the lock can also be extended —
 * never cancelled.
 */
class SawnLockdownTileService : TileService() {

    /**
     * The store the tile acts on.
     *
     * Production resolves it from the container; the tests build their own, so the tile
     * can be driven without the whole dependency graph.
     */
    internal var settingsOverride: SettingsStore? = null

    private val settings: SettingsStore
        get() = settingsOverride ?: sawnContainer.settings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            EmergencyLockdownEnforcer.start(
                settings = settings,
                context = this@SawnLockdownTileService,
                durationMinutes = PANIC_MINUTES,
                reason = LockdownReason.QS_TILE,
            )
            refreshTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        // The system offers the tile as soon as the service is declared; nothing has to
        // be stored for it to work. The state is refreshed so a lock that is already
        // running shows as active right away.
        scope.launch { refreshTile() }
    }

    private suspend fun refreshTile() {
        val active = EmergencyLockdownEnforcer.isLockdownActive(settings)
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_lockdown_label)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (active) R.string.tile_lockdown_sub_active else R.string.tile_lockdown_sub_idle,
                )
            }
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** The lock a single tap starts. */
        const val PANIC_MINUTES = 15
    }
}
