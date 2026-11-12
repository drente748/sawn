package com.sawn.app.ui.lockdown

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sawn.app.system.EmergencyLockdownEnforcer
import com.sawn.core.data.SettingsStore
import com.sawn.domain.lockdown.EmergencyLockdown
import com.sawn.domain.lockdown.LockdownReason
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the emergency lockdown screen shows. */
data class EmergencyLockdownUiState(
    /** Whether a lock is running right now. */
    val isActive: Boolean = false,
    /** The millis left on the lock — zero when there is none. */
    val remainingMillis: Long = 0L,
    /** When the lock ends, on the wall clock (for display only). */
    val endsAt: Long = 0L,
    /** When the lock ends, on the monotonic clock — what the countdown counts down. */
    val endsAtElapsed: Long = 0L,
)

/**
 * The emergency lockdown: full blocking for a fixed time, with no way out.
 *
 * The state is the truth from the store, not a local flag — a lock started from the
 * Quick Settings tile has to show up here, and a lock started here has to be visible
 * to the engines. There is deliberately **no** cancel: the only thing the interface
 * can do is add time.
 *
 * The countdown is not ticked here: the screen refreshes it once a second, so the
 * view model never holds a timer of its own.
 */
class EmergencyLockdownViewModel(
    app: Application,
    private val settings: SettingsStore,
) : AndroidViewModel(app) {

    val uiState: StateFlow<EmergencyLockdownUiState> =
        settings.emergencyLockdownSession
            .map { session ->
                val now = SystemClock.elapsedRealtime()
                EmergencyLockdownUiState(
                    isActive = EmergencyLockdown.isActive(session, now),
                    remainingMillis = EmergencyLockdown.remainingMillis(session, now),
                    endsAt = session?.wallEndsAt ?: 0L,
                    endsAtElapsed = session?.endsAt ?: 0L,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyLockdownUiState())

    /** Starts the panic lock with the default duration. */
    fun startPanic() = start(EmergencyLockdown.MIN_MINUTES)

    /** Starts a lock of the given duration. */
    fun start(durationMinutes: Int) {
        viewModelScope.launch {
            EmergencyLockdownEnforcer.start(
                settings = settings,
                context = getApplication(),
                durationMinutes = durationMinutes,
                reason = LockdownReason.EMERGENCY,
            )
        }
    }

    /** Adds time to the running lock. It can only get longer. */
    fun extend(extraMinutes: Int) {
        viewModelScope.launch {
            EmergencyLockdownEnforcer.extend(
                settings = settings,
                context = getApplication(),
                extraMinutes = extraMinutes,
            )
        }
    }
}
