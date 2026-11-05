package com.sawn.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sawn.domain.block.BlockingPolicy
import com.sawn.domain.block.ScheduleEvaluator
import com.sawn.domain.block.StrictPolicy
import com.sawn.app.data.BlockType
import com.sawn.app.data.BlockedItem
import com.sawn.app.data.DisableOutcome
import com.sawn.app.data.InterceptMode
import com.sawn.app.data.Schedule
import com.sawn.app.data.toDomain
import com.sawn.domain.feature.JournalSnapshot
import com.sawn.domain.feature.ShieldPreset
import com.sawn.domain.feature.StreakPolicy
import com.sawn.app.security.AdultListFetcher
import com.sawn.domain.security.PinHasher
import com.sawn.domain.security.PinResult
import com.sawn.app.system.FocusEndWorker
import com.sawn.app.vpn.VpnController
import com.sawn.app.sawnContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class SawnViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.sawnContainer

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
        container.settings.onboardingDone,
    ) { _, _, _, _, _ -> true }
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

    // --- Accessibility (graphics) ---
    val reduceMotion: StateFlow<Boolean> =
        container.settings.reduceMotion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPornGuard(value: Boolean) = viewModelScope.launch { container.settings.setPornGuard(value) }
    fun setSafeSearchForce(value: Boolean) = viewModelScope.launch { container.settings.setSafeSearchForce(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { container.settings.setReduceMotion(value) }

    // --- القفل الزمني (Lockdown) ---
    val lockdownUntil: StateFlow<Long> =
        container.settings.lockdownUntil.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /**
     * عدّاد حي مشتق من النافذة الموثوقة (elapsedRealtime داخل الإقلاع، ووقت الجدار
     * المحمي بـ high-water بعده). لا نستخدم System.currentTimeMillis مباشرة في الواجهة
     * لأنه يسمح بتقديم الساعة لإنهاء القفل.
     */
    private val oneSecondTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    val lockdownRemaining: StateFlow<Long> = combine(
        container.settings.lockdownWindowState,
        oneSecondTicker,
    ) { window, _ -> window.remainingAt(SystemClock.elapsedRealtime(), System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lockdownActive: StateFlow<Boolean> = lockdownRemaining
        .map { it > 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True only when the policy actually enforces blocking at the current trusted time. */
    val enforcementActive: StateFlow<Boolean> = combine(
        combine(masterEnabled, alwaysOn, container.settings.focusEndsAt, schedules, oneSecondTicker) { master, on, focusAt, list, _ ->
            val calendar = Calendar.getInstance()
            BlockingPolicy.isActive(
                masterEnabled = master,
                alwaysOn = on,
                focusEndsAt = focusAt,
                now = calendar.timeInMillis,
                anyScheduleActive = ScheduleEvaluator.anyActive(list.map { it.toDomain() }, calendar),
            )
        },
        lockdownActive,
    ) { policyActive, lockdown ->
        policyActive || lockdown
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** المدة المنتظرة — لا تُكتب أي قيمة قبل موافقة Device Admin. */
    private var pendingLockdownDurationMs: Long? = null

    /**
     * المرحلة 1: احتفظ بالمدة في الذاكرة فقط. تُحسب النهاية عند [commitLockdown] من
     * الساعة الموثوقة (anti-tamper) حتى لا يقصّر زمن شاشة الموافقة القفل، وحتى لا
     * يسمح إرجاع ساعة الجهاز بقفل منتهٍ منذ لحظته الأولى.
     */
    fun stageLockdownForDuration(durationMs: Long, onStaged: () -> Unit = {}) {
        pendingLockdownDurationMs = durationMs
        onStaged()
    }

    fun hasStagedLockdown(): Boolean = pendingLockdownDurationMs != null

    /**
     * المرحلة 2 (بعد موافقة admin): لا يُكتب القفل إلا بعد تشغيل الفلتر فعلياً.
     * قفل بلا حجب حقيقي ليس قفلاً — لذلك يفشل التفعيل إن لم يعمل الـ VPN.
     */
    fun commitLockdown(onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val durationMs = pendingLockdownDurationMs
        if (durationMs == null) {
            onResult(false)
            return@launch
        }
        pendingLockdownDurationMs = null
        val app = getApplication<Application>()
        if (VpnController.consentIntent(app) != null) {
            // The UI requests consent before staging; this is a defensive backstop.
            onResult(false)
            return@launch
        }
        if (!VpnController.startAndAwait(app)) {
            onResult(false)
            return@launch
        }
        val endsAt = container.settings.trustedNow() + durationMs
        onResult(container.settings.setLockdownUntil(endsAt))
    }

    fun discardStagedLockdown() {
        pendingLockdownDurationMs = null
    }

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
    /** نبضٌ دقيقي يجعل «اليوم» يتغيّر في الواجهة عند منتصف الليل بلا إعادة فتح. */
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    /**
     * السلسلة كما يراها المستخدم: تُشتق مقابل اليوم الحالي، فاليومُ الفائت يصفّرها
     * فوراً بدل أن تبقى تعرض رقماً قديماً حتى تُفتح الواجهة من جديد.
     */
    val currentStreak: StateFlow<Int> = combine(
        container.settings.currentStreak,
        container.settings.lastActiveDay,
        minuteTicker,
    ) { raw, lastDay, _ -> StreakPolicy.visibleStreak(raw, lastDay, StreakPolicy.todayEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** السلسلة حيّة لكن اليوم لم يُسجَّل بعد — تستخدمها الرئيسية تذكيراً هادئاً. */
    val streakAtRisk: StateFlow<Boolean> = combine(
        container.settings.currentStreak,
        container.settings.lastActiveDay,
        minuteTicker,
    ) { raw, lastDay, _ -> StreakPolicy.isAtRisk(raw, lastDay, StreakPolicy.todayEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blockJournal: StateFlow<JournalSnapshot> =
        container.settings.blockJournal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalSnapshot())

    /** نمط حماية بضغطة واحدة (خفيف / متوازن / أقصى). */
    fun applyPreset(preset: ShieldPreset) = viewModelScope.launch { container.settings.applyPreset(preset) }
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
        // الجلسة تفرض الحجب، فهي يوم محمي مهما كانت حالة المفتاح الرئيسي.
        container.settings.recordActiveDay()
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

    /**
     * سجّل اليوم المحمي عند فتح التطبيق. الشرط هو الحجب الفعلي لا مفتاح «الدائم» وحده:
     * جلسة تركيز أو نافذة جدول أو قفل زمني كلها حمايةٌ تستحق أن تُحتسب في السلسلة.
     */
    fun onAppOpened() = viewModelScope.launch {
        runCatching {
            if (container.blockEngine.isBlockingActiveNow()) {
                container.settings.recordActiveDay()
            } else {
                container.settings.reconcileStreak()
            }
        }
    }

    /** يُستدعى فور نجاح تشغيل الفلتر كي لا ينتظر العدّاد إعادة فتح التطبيق. */
    fun recordProtectedDayNow() = viewModelScope.launch {
        runCatching {
            if (container.blockEngine.isBlockingActiveNow()) container.settings.recordActiveDay()
        }
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
    suspend fun setStrictMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (enabled) container.settings.enableStrictMode(container.settings.trustedNow())
            else container.settings.setStrictMode(false)
        }.getOrDefault(false)
    }

    // --- PIN ---
    /**
     * Create a PIN. When [enableStrict] is true (the user asked for strict mode before a PIN
     * existed), strict is enabled in the same coroutine so ordering cannot be lost.
     */
    suspend fun setPin(pin: String, enableStrict: Boolean = false): Boolean =
        withContext(Dispatchers.Default) {
            runCatching {
                val salt = PinHasher.newSalt()
                val hash = PinHasher.hash(pin, salt)
                container.settings.setPin(hash, salt)
                !enableStrict || container.settings.enableStrictMode(container.settings.trustedNow())
            }.getOrDefault(false)
        }

    /** False when the store refused the removal — strict mode or a time lock still needs the PIN. */
    suspend fun clearPin(): Boolean = withContext(Dispatchers.IO) {
        runCatching { container.settings.clearPin() }.getOrDefault(false)
    }

    /** Verifies [pin], enforcing a compounding lockout after repeated failures. */
    suspend fun verifyPin(pin: String): PinResult {
        val lockRemaining = container.settings.pinLockRemainingMillis()
        if (lockRemaining > 0L) {
            return PinResult.Locked(((lockRemaining + 999L) / 1000L).toInt())
        }

        val credentials = runCatching { container.settings.pinCredentials() }
            .getOrNull() ?: return PinResult.Error
        val (hash, salt) = credentials
        return try {
            val verified = withContext(Dispatchers.Default) { PinHasher.verify(pin, salt, hash) }
            if (verified) {
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
        } catch (_: Exception) {
            PinResult.Error
        }
    }
}
