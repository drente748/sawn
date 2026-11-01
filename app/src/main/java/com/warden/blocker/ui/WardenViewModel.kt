package com.warden.blocker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.warden.blocker.block.StrictPolicy
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.DisableOutcome
import com.warden.blocker.data.InterceptMode
import com.warden.blocker.data.Schedule
import com.warden.blocker.security.AdultListFetcher
import com.warden.blocker.security.PinHasher
import com.warden.blocker.security.PinResult
import com.warden.blocker.system.FocusEndWorker
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WardenViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.wardenContainer

    val items: StateFlow<List<BlockedItem>> =
        container.repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedules: StateFlow<List<Schedule>> =
        container.repository.schedules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterEnabled: StateFlow<Boolean> =
        container.settings.masterEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val onboardingDone: StateFlow<Boolean> =
        container.settings.onboardingDone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** False until DataStore has produced its first snapshot; UI treats that as locked. */
    val settingsReady: StateFlow<Boolean> = combine(
        container.settings.masterEnabled,
        container.settings.strictMode,
        container.settings.hasPin,
        container.settings.focusEndsAt,
    ) { _, _, _, _ -> true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setOnboardingDone() = viewModelScope.launch { container.settings.setOnboardingDone() }
    val strictMode: StateFlow<Boolean> =
        container.settings.strictMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /**
     * Global strict lock only. A strict focus session is time-bounded, so the Home screen
     * applies that part against its own ticking clock instead of a flow value that would go
     * stale the moment the session expires without a DataStore write.
     */
    val strictLocked: StateFlow<Boolean> = combine(
        container.settings.strictMode,
        container.settings.masterEnabled,
    ) { strict, enabled -> StrictPolicy.isMasterLocked(strict, enabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val alwaysOn: StateFlow<Boolean> =
        container.settings.alwaysOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAlwaysOn(value: Boolean) = viewModelScope.launch { container.settings.setAlwaysOn(value) }

    val blockDoh: StateFlow<Boolean> =
        container.settings.blockDoh.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBlockDoh(value: Boolean) = viewModelScope.launch { container.settings.setBlockDoh(value) }

    val blockAds: StateFlow<Boolean> =
        container.settings.blockAds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setBlockAds(value: Boolean) = viewModelScope.launch { container.settings.setBlockAds(value) }

    val blockNotifications: StateFlow<Boolean> =
        container.settings.blockNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBlockNotifications(value: Boolean) = viewModelScope.launch { container.settings.setBlockNotifications(value) }

    // --- Porn Guard ---
    val pornGuard: StateFlow<Boolean> =
        container.settings.pornGuard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val safeSearchForce: StateFlow<Boolean> =
        container.settings.safeSearchForce.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val adultExtraCount: StateFlow<Int> =
        container.settings.adultExtraDomains
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setPornGuard(value: Boolean) = viewModelScope.launch { container.settings.setPornGuard(value) }
    fun setSafeSearchForce(value: Boolean) = viewModelScope.launch { container.settings.setSafeSearchForce(value) }

    /** Pull open blocklist updates while preserving the last valid list on failure. */
    fun refreshAdultLists(onResult: (AdultListFetcher.FetchResult) -> Unit = {}) = viewModelScope.launch {
        val result = AdultListFetcher(getApplication()).refreshIfDue(force = true)
        onResult(result)
    }

    val hasPin: StateFlow<Boolean> =
        container.settings.hasPin.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** null until DataStore resolves, so PIN-gated screens can stay hidden on first frame. */
    val pinConfigured: StateFlow<Boolean?> =
        container.settings.hasPin
            .map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val currentStreak: StateFlow<Int> =
        container.settings.currentStreak.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val longestStreak: StateFlow<Int> =
        container.settings.longestStreak.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val focusSessionsDone: StateFlow<Int> =
        container.settings.focusSessionsDone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val enabledFeatureKeys: StateFlow<Set<String>> =
        container.settings.enabledFeatureKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val focusEndsAt: StateFlow<Long> =
        container.settings.focusEndsAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val focusStrict: StateFlow<Boolean> =
        container.settings.focusStrict.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Begin a focus session that forces blocking on for [minutes]. */
    fun startFocus(minutes: Int, strict: Boolean) = viewModelScope.launch {
        val globalStrict = container.settings.strictMode.first()
        // Base the end on the tamper-resistant clock, not raw wall time: a rolled-back
        // clock must not create a session that is already expired.
        val now = container.settings.trustedNow()
        val endsAt = now + minutes * 60_000L
        container.settings.startFocus(
            endsAt,
            StrictPolicy.effectiveFocusStrict(globalStrict, strict),
        )
        FocusEndWorker.schedule(getApplication(), minutes * 60_000L)
    }

    /** Cancel early; refused while a strict session is still running. */
    fun cancelFocus(onCancelled: suspend () -> Unit = {}) = viewModelScope.launch {
        val snapshot = container.settings.protectionSnapshot()
        val now = container.settings.trustedNow()
        if (!StrictPolicy.canCancelFocus(snapshot.focusStrict, snapshot.focusEndsAt, now)) {
            return@launch
        }
        container.settings.clearFocus()
        FocusEndWorker.cancel(getApplication())
        onCancelled()
    }

    /**
     * Atomically check the strict/focus locks and persist protection-off. The result says
     * whether the write happened and whether a focus session must keep the tunnel alive.
     */
    suspend fun disableProtectionNow(): DisableOutcome =
        container.settings.disableProtection(container.settings.trustedNow())

    /** Fresh read for post-cancellation decisions; never rely on the UI StateFlow. */
    suspend fun isMasterEnabledNow(): Boolean = container.settings.masterEnabled.first()

    fun setFeatureEnabled(key: String, enabled: Boolean) = viewModelScope.launch {
        container.settings.setFeatureEnabled(key, enabled)
    }

    /** Record a protected day for the focus streak, once per open while blocking is on. */
    fun onAppOpened() = viewModelScope.launch {
        if (container.settings.masterEnabled.first()) container.settings.recordActiveDay()
    }

    fun addWebsite(domain: String) {
        val clean = domain.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").substringBefore("/").ifBlank { return }
        viewModelScope.launch {
            container.repository.addItem(
                BlockedItem(type = BlockType.WEBSITE, value = clean, label = clean, createdAt = System.currentTimeMillis()),
            )
        }
    }

    /** New apps default to a mindful PAUSE (ScreenZen-style), not a hard block. */
    fun addApp(packageName: String, label: String) = viewModelScope.launch {
        if (container.repository.appItem(packageName) != null) return@launch
        container.repository.addItem(
            BlockedItem(
                type = BlockType.APP,
                value = packageName,
                label = label,
                createdAt = System.currentTimeMillis(),
                interceptMode = InterceptMode.PAUSE,
            ),
        )
    }

    fun toggleItem(item: BlockedItem) = viewModelScope.launch {
        container.repository.updateItem(item.copy(enabled = !item.enabled))
    }

    /** Persist edits from the per-item config editor. */
    fun saveItem(item: BlockedItem) = viewModelScope.launch { container.repository.updateItem(item) }

    fun removeItem(item: BlockedItem) = viewModelScope.launch { container.repository.removeItem(item) }

    fun addSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.addSchedule(schedule) }
    fun removeSchedule(schedule: Schedule) = viewModelScope.launch { container.repository.removeSchedule(schedule) }

    fun setMasterEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setMasterEnabled(enabled) }

    /** Strict mode needs a PIN; the store enforces that atomically with the write. */
    fun setStrictMode(enabled: Boolean) = viewModelScope.launch {
        if (enabled) {
            container.settings.enableStrictMode(container.settings.trustedNow())
        } else {
            container.settings.setStrictMode(false)
        }
    }

    // --- PIN ---
    /**
     * Create a PIN. When [enableStrict] is true (the user asked for strict mode before a PIN
     * existed), strict is enabled in the same coroutine so ordering cannot be lost.
     */
    fun setPin(
        pin: String,
        enableStrict: Boolean = false,
        onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        val salt = PinHasher.newSalt()
        container.settings.setPin(PinHasher.hash(pin, salt), salt)
        if (enableStrict) {
            container.settings.enableStrictMode(container.settings.trustedNow())
        }
        onDone()
    }

    /** Refused atomically by the store while strict mode is effective. */
    fun clearPin() = viewModelScope.launch { container.settings.clearPin() }

    /** Verifies [pin], enforcing a compounding lockout after repeated failures. */
    suspend fun verifyPin(pin: String): PinResult {
        val lockRemaining = container.settings.pinLockRemainingMillis()
        if (lockRemaining > 0L) {
            return PinResult.Locked(((lockRemaining + 999L) / 1000L).toInt())
        }

        val (hash, salt) = container.settings.pinCredentials() ?: return PinResult.Wrong
        return if (PinHasher.verify(pin, salt, hash)) {
            container.settings.recordPinSuccess()
            PinResult.Ok
        } else {
            val newLockRemaining = container.settings.recordPinFailure()
            if (newLockRemaining > 0L) {
                PinResult.Locked(((newLockRemaining + 999L) / 1000L).toInt())
            } else {
                PinResult.Wrong
            }
        }
    }
}
