package com.warden.blocker.data

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.warden.blocker.block.StrictPolicy
import com.warden.blocker.security.PinLockoutPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "warden_settings")

/** One consistent read of every setting that decides whether (and how) protection is active. */
data class ProtectionSnapshot(
    val masterEnabled: Boolean,
    val alwaysOn: Boolean,
    val strictMode: Boolean,
    val focusEndsAt: Long,
    val focusStrict: Boolean,
    val blockDoh: Boolean,
    val blockAds: Boolean,
    val hasPin: Boolean,
    val pornGuard: Boolean,
    val safeSearchForce: Boolean,
)

/** Result of an atomic "turn protection off" request. */
data class DisableOutcome(val disabled: Boolean, val focusActive: Boolean)

/**
 * User settings & protection state. The PIN is stored as a salted hash — never plaintext.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val ALWAYS_ON = booleanPreferencesKey("always_on")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val CURRENT_STREAK = intPreferencesKey("current_streak")
        val LONGEST_STREAK = intPreferencesKey("longest_streak")
        val LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
        val ENABLED_FEATURES = stringSetPreferencesKey("enabled_features")
        val SOCIAL_GUARD = booleanPreferencesKey("social_guard")
        val ENABLED_SOCIAL_PLATFORMS = stringSetPreferencesKey("enabled_social_platforms")
        // ( social_guard / enabled_social_platforms مهجوران بعد 1.0.0 — لم يبق لهما تعريف)
        val FOCUS_ENDS_AT = longPreferencesKey("focus_ends_at")
        val FOCUS_STRICT = booleanPreferencesKey("focus_strict")
        val BLOCK_DOH = booleanPreferencesKey("block_doh")
        val BLOCK_ADS = booleanPreferencesKey("block_ads")
        val BLOCK_NOTIFICATIONS = booleanPreferencesKey("block_notifications")
        val FOCUS_SESSIONS_DONE = intPreferencesKey("focus_sessions_done")
        val PIN_FAILS = intPreferencesKey("pin_fails")
        val PIN_LOCK_UNTIL = longPreferencesKey("pin_lock_until")
        val PIN_LOCK_ELAPSED_UNTIL = longPreferencesKey("pin_lock_elapsed_until")
        val PIN_LOCK_BOOT_COUNT = intPreferencesKey("pin_lock_boot_count")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SEEN_TIME_HIGHWAT = longPreferencesKey("seen_time_highwat")
        val PAUSE_DEFEATS_DATE = longPreferencesKey("pause_defeats_date")
        val PAUSE_DEFEATS = intPreferencesKey("pause_defeats")
        // --- Porn Guard -------------------------------------------------------
        // Default ON: the app's core promise for its target users is protection from
        // adult content, so a fresh install is protected without visiting settings.
        val PORN_GUARD = booleanPreferencesKey("porn_guard")
        val SAFE_SEARCH_FORCE = booleanPreferencesKey("safe_search_force")
        val ADULT_EXTRA_DOMAINS = stringSetPreferencesKey("adult_extra_domains")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    }

    /** A PIN is only usable when both its hash and salt are present. */
    private fun pinPresent(p: Preferences): Boolean =
        p[Keys.PIN_HASH] != null && p[Keys.PIN_SALT] != null

    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: false }.distinctUntilChanged()
    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun setOnboardingDone() =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }.let { }

    val alwaysOn: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ALWAYS_ON] ?: true }.distinctUntilChanged()
    val strictMode: Flow<Boolean> = context.dataStore.data
        .map { StrictPolicy.isEffectiveStrictMode(it[Keys.STRICT_MODE] ?: false, pinPresent(it)) }
        .distinctUntilChanged()
    val blockDoh: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BLOCK_DOH] ?: false }.distinctUntilChanged()
    val blockAds: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BLOCK_ADS] ?: true }.distinctUntilChanged()
    val blockNotifications: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BLOCK_NOTIFICATIONS] ?: false }.distinctUntilChanged()

    // --- Porn Guard -----------------------------------------------------------
    // Missing key reads as ON (fresh installs are protected by default).
    val pornGuard: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PORN_GUARD] ?: true }.distinctUntilChanged()
    val safeSearchForce: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SAFE_SEARCH_FORCE] ?: true }.distinctUntilChanged()
    /** Runtime-merged extra adult domains fetched from open blocklists. */
    val adultExtraDomains: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ADULT_EXTRA_DOMAINS] ?: emptySet() }.distinctUntilChanged()

    suspend fun setPornGuard(value: Boolean) =
        context.dataStore.edit { it[Keys.PORN_GUARD] = value }.let { }

    suspend fun setSafeSearchForce(value: Boolean) =
        context.dataStore.edit { it[Keys.SAFE_SEARCH_FORCE] = value }.let { }

    suspend fun setAdultExtraDomains(domains: Set<String>) =
        context.dataStore.edit { it[Keys.ADULT_EXTRA_DOMAINS] = domains }.let { }

    // --- إمكانية الوصول الرسومي ---------------------------------------------
    /** تقليل حركة واجهة (نبض الدرع). Default false: الحركة حيّة إفتراضياً. */
    val reduceMotion: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }.distinctUntilChanged()

    suspend fun setReduceMotion(value: Boolean) =
        context.dataStore.edit { it[Keys.REDUCE_MOTION] = value }.let { }

    suspend fun setBlockDoh(value: Boolean) =
        context.dataStore.edit { it[Keys.BLOCK_DOH] = value }.let { }

    suspend fun setBlockAds(value: Boolean) =
        context.dataStore.edit { it[Keys.BLOCK_ADS] = value }.let { }

    suspend fun setBlockNotifications(value: Boolean) =
        context.dataStore.edit { it[Keys.BLOCK_NOTIFICATIONS] = value }.let { }
    val hasPin: Flow<Boolean> =
        context.dataStore.data.map { pinPresent(it) }.distinctUntilChanged()
    val currentStreak: Flow<Int> =
        context.dataStore.data.map { it[Keys.CURRENT_STREAK] ?: 0 }.distinctUntilChanged()
    val longestStreak: Flow<Int> =
        context.dataStore.data.map { it[Keys.LONGEST_STREAK] ?: 0 }.distinctUntilChanged()
    val enabledFeatureKeys: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ENABLED_FEATURES] ?: emptySet() }.distinctUntilChanged()
    // socialGuard/enabledSocialPlatforms: أُزيلت الميزة من السطح في 1.0.0؛ المفتاحان
    // يُتركان في DataStore دون هجرة (تنظيف عميق لاحقاً). الخدمة تصل صفر منطقها
    // عبر التيار أدناه.
    /** دائمة false بعد 1.0.0 — مسار منصات التواصل في الخدمة لن يعمل. */
    val socialGuard: Flow<Boolean> = flowOf(false)
    val enabledSocialPlatforms: Flow<Set<String>?> = flowOf(null)

    /** Epoch millis a focus session ends (0 = none), and whether it can be cancelled early. */
    val focusEndsAt: Flow<Long> =
        context.dataStore.data.map { it[Keys.FOCUS_ENDS_AT] ?: 0L }.distinctUntilChanged()
    val focusStrict: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.FOCUS_STRICT] ?: false }.distinctUntilChanged()

    suspend fun startFocus(endsAt: Long, strict: Boolean) =
        context.dataStore.edit { it[Keys.FOCUS_ENDS_AT] = endsAt; it[Keys.FOCUS_STRICT] = strict }.let { }

    suspend fun clearFocus() =
        context.dataStore.edit { it.remove(Keys.FOCUS_ENDS_AT); it.remove(Keys.FOCUS_STRICT) }.let { }

    val focusSessionsDone: Flow<Int> =
        context.dataStore.data.map { it[Keys.FOCUS_SESSIONS_DONE] ?: 0 }.distinctUntilChanged()

    suspend fun incrementFocusSessions() =
        context.dataStore.edit { it[Keys.FOCUS_SESSIONS_DONE] = (it[Keys.FOCUS_SESSIONS_DONE] ?: 0) + 1 }.let { }

    suspend fun setFeatureEnabled(key: String, enabled: Boolean) =
        context.dataStore.edit { p ->
            val current = (p[Keys.ENABLED_FEATURES] ?: emptySet()).toMutableSet()
            if (enabled) current.add(key) else current.remove(key)
            p[Keys.ENABLED_FEATURES] = current
        }.let { }

    /**
     * Mark today as a protected day and update the focus streak. Called once per app open
     * while blocking is enabled. A gap of more than a day resets the streak to 1.
     */
    suspend fun recordActiveDay() {
        val today = LocalDate.now().toEpochDay()
        context.dataStore.edit { p ->
            val last = p[Keys.LAST_ACTIVE_DAY] ?: -1L
            if (last == today) return@edit
            val current = p[Keys.CURRENT_STREAK] ?: 0
            val next = if (last == today - 1) current + 1 else 1
            p[Keys.CURRENT_STREAK] = next
            p[Keys.LONGEST_STREAK] = maxOf(p[Keys.LONGEST_STREAK] ?: 0, next)
            p[Keys.LAST_ACTIVE_DAY] = today
        }
    }

    suspend fun setMasterEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.MASTER_ENABLED] = value }.let { }

    suspend fun setAlwaysOn(value: Boolean) =
        context.dataStore.edit { it[Keys.ALWAYS_ON] = value }.let { }

    /** Returns true when the change was applied; enabling requires a usable PIN. */
    suspend fun setStrictMode(value: Boolean): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            if (value && !StrictPolicy.canEnableStrictMode(pinPresent(p))) return@edit
            p[Keys.STRICT_MODE] = value
            applied = true
        }
        return applied
    }

    /**
     * Atomically enable strict mode and make any live focus session strict too, so there is
     * never a moment where strict is on but the running session can still be cancelled.
     * Requires a PIN; returns false when that precondition fails.
     */
    suspend fun enableStrictMode(now: Long): Boolean {
        var applied = false
        context.dataStore.edit { p ->
            if (!StrictPolicy.canEnableStrictMode(pinPresent(p))) return@edit
            p[Keys.STRICT_MODE] = true
            val endsAt = p[Keys.FOCUS_ENDS_AT] ?: 0L
            if (endsAt > now) p[Keys.FOCUS_STRICT] = true
            applied = true
        }
        return applied
    }

    suspend fun setPin(hash: String, salt: String) = context.dataStore.edit { p ->
        // A legacy strict flag with no PIN was never effective; do not silently activate it
        // the moment a PIN is created. (The Settings flow explicitly re-enables strict after
        // PIN creation when that was the user's intent.)
        val staleStrictWithoutPin = (p[Keys.STRICT_MODE] ?: false) && !pinPresent(p)
        p[Keys.PIN_HASH] = hash
        p[Keys.PIN_SALT] = salt
        if (staleStrictWithoutPin) p[Keys.STRICT_MODE] = false
    }.let { }

    /** Returns true when the PIN was removed; strict mode keeps its credential in place. */
    suspend fun clearPin(): Boolean {
        var cleared = false
        context.dataStore.edit { p ->
            val pin = pinPresent(p)
            val effectiveStrict = StrictPolicy.isEffectiveStrictMode(
                p[Keys.STRICT_MODE] ?: false,
                pin,
            )
            if (!StrictPolicy.canClearPin(effectiveStrict, pin)) return@edit
            p.remove(Keys.PIN_HASH)
            p.remove(Keys.PIN_SALT)
            cleared = true
        }
        return cleared
    }

    /** Consistent snapshot for enforcement paths that must not mix values from different edits. */
    suspend fun protectionSnapshot(): ProtectionSnapshot {
        val p = context.dataStore.data.first()
        val pin = pinPresent(p)
        return ProtectionSnapshot(
            masterEnabled = p[Keys.MASTER_ENABLED] ?: false,
            alwaysOn = p[Keys.ALWAYS_ON] ?: true,
            strictMode = StrictPolicy.isEffectiveStrictMode(p[Keys.STRICT_MODE] ?: false, pin),
            focusEndsAt = p[Keys.FOCUS_ENDS_AT] ?: 0L,
            focusStrict = p[Keys.FOCUS_STRICT] ?: false,
            blockDoh = p[Keys.BLOCK_DOH] ?: false,
            blockAds = p[Keys.BLOCK_ADS] ?: true,
            hasPin = pin,
            pornGuard = p[Keys.PORN_GUARD] ?: true,
            safeSearchForce = p[Keys.SAFE_SEARCH_FORCE] ?: true,
        )
    }

    /**
     * Re-check strict/focus locks and turn the master switch off in the same DataStore
     * transaction, so a focus session starting concurrently cannot be undercut by a check
     * that used an older snapshot. [DisableOutcome.focusActive] tells the caller whether the
     * tunnel must stay up (a focus session is still forcing blocking on).
     */
    suspend fun disableProtection(now: Long): DisableOutcome {
        var outcome = DisableOutcome(disabled = false, focusActive = false)
        context.dataStore.edit { p ->
            val pin = pinPresent(p)
            val strict = StrictPolicy.isEffectiveStrictMode(p[Keys.STRICT_MODE] ?: false, pin)
            val master = p[Keys.MASTER_ENABLED] ?: false
            val focusStrict = p[Keys.FOCUS_STRICT] ?: false
            val focusEndsAt = p[Keys.FOCUS_ENDS_AT] ?: 0L
            val focusActive = StrictPolicy.isFocusActive(focusEndsAt, now)
            val locked = StrictPolicy.isMasterLocked(strict, master, focusStrict, focusEndsAt, now)
            if (!locked) p[Keys.MASTER_ENABLED] = false
            outcome = DisableOutcome(disabled = !locked, focusActive = focusActive)
        }
        return outcome
    }

    // --- PIN brute-force protection ---
    suspend fun pinLockRemainingMillis(): Long {
        val p = context.dataStore.data.first()
        return PinLockoutPolicy.remainingMillis(
            wallLockUntil = p[Keys.PIN_LOCK_UNTIL] ?: 0L,
            elapsedLockUntil = p[Keys.PIN_LOCK_ELAPSED_UNTIL] ?: 0L,
            lockBootCount = p[Keys.PIN_LOCK_BOOT_COUNT] ?: -1,
            currentBootCount = currentBootCount(),
            wallNow = System.currentTimeMillis(),
            elapsedNow = SystemClock.elapsedRealtime(),
        )
    }

    suspend fun recordPinSuccess() = context.dataStore.edit { p ->
        p.remove(Keys.PIN_FAILS)
        p.remove(Keys.PIN_LOCK_UNTIL)
        p.remove(Keys.PIN_LOCK_ELAPSED_UNTIL)
        p.remove(Keys.PIN_LOCK_BOOT_COUNT)
    }.let { }

    /** Returns the remaining lock in milliseconds, or zero when another attempt is allowed. */
    suspend fun recordPinFailure(): Long {
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        var lockRemaining = 0L
        context.dataStore.edit { p ->
            val fails = (p[Keys.PIN_FAILS] ?: 0) + 1
            p[Keys.PIN_FAILS] = fails
            lockRemaining = PinLockoutPolicy.lockDurationMillis(fails)
            if (lockRemaining > 0L) {
                p[Keys.PIN_LOCK_UNTIL] = wallNow + lockRemaining
                p[Keys.PIN_LOCK_ELAPSED_UNTIL] = elapsedNow + lockRemaining
                p[Keys.PIN_LOCK_BOOT_COUNT] = bootCount
            }
        }
        return lockRemaining
    }

    private fun currentBootCount(): Int = runCatching {
        android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.BOOT_COUNT,
        )
    }.getOrDefault(-1)

    /** Returns (hash, salt) or null if no PIN configured. */
    suspend fun pinCredentials(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val h = prefs[Keys.PIN_HASH]
        val s = prefs[Keys.PIN_SALT]
        return if (h != null && s != null) h to s else null
    }

    // --- Clock-tamper defense (Tempo's lastSeen high-water mark) ---
    // The only trusted "now": at least as late as any moment we've ever observed the system
    // report (and we observe it whenever anything checks time). Rolling the clock back can
    // no longer shorten schedules, focus sessions, cooldowns, or strict locks.
    suspend fun trustedNow(): Long {
        val sys = System.currentTimeMillis()
        var now = sys
        context.dataStore.edit { p ->
            val hw = p[Keys.SEEN_TIME_HIGHWAT] ?: 0L
            if (sys > hw) { p[Keys.SEEN_TIME_HIGHWAT] = sys; }
            now = maxOf(sys, hw)
        }
        return now
    }

    /**
     * Read-only clock for hot paths (the VPN's periodic refresh). Unlike [trustedNow] it
     * never writes, so it cannot re-trigger the DataStore flows that drive those refreshes.
     */
    suspend fun peekNow(): Long {
        val sys = System.currentTimeMillis()
        val hw = context.dataStore.data.first()[Keys.SEEN_TIME_HIGHWAT] ?: 0L
        return maxOf(sys, hw)
    }

    // --- Escalating pause friction (PlugBrain-style difficulty floor, inverted) ---
    // Each "Not now — take me home" exit from an active pause adds one defeat today; the
    // pause wait added to every PAUSE item's next attempt grows with it. Resets daily.
    suspend fun recordPauseDefeat(now: Long) {
        context.dataStore.edit { p ->
            val day = p[Keys.PAUSE_DEFEATS_DATE] ?: 0L
            val dayOfNow = now / 86_400_000L
            val count = if (day == dayOfNow) (p[Keys.PAUSE_DEFEATS] ?: 0) + 1 else 1
            p[Keys.PAUSE_DEFEATS_DATE] = dayOfNow
            p[Keys.PAUSE_DEFEATS] = count
        }
    }

    suspend fun pauseDefeatsToday(now: Long): Int {
        val dayOfNow = now / 86_400_000L
        val prefs = context.dataStore.data.first()
        return if ((prefs[Keys.PAUSE_DEFEATS_DATE] ?: 0L) == dayOfNow) prefs[Keys.PAUSE_DEFEATS] ?: 0 else 0
    }
}
