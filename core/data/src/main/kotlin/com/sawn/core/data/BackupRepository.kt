package com.sawn.core.data

import androidx.room.withTransaction
import com.sawn.domain.feature.BackupDocument
import com.sawn.domain.feature.BackupItem
import com.sawn.domain.feature.BackupProfile
import com.sawn.domain.feature.BackupSchedule
import com.sawn.domain.feature.BackupSettings
import com.sawn.domain.feature.DeepFocusPolicy
import com.sawn.domain.model.BlockType
import com.sawn.domain.model.InterceptMode
import kotlinx.coroutines.flow.first

/**
 * بناء واستعادة النسخة الاحتياطية.
 *
 * الترميز نفسه في [com.sawn.domain.feature.BackupCodec] (نواة خالصة بلا أندرويد)،
 * وهنا المهمة الوحيدة: جمع الحالة من DataStore وRoom، ثم تطبيقها بعد فكّ الترميز.
 *
 * ما لا يُنسخ عمداً: الرقم السري (تجزئة + ملح)، رقم الوالدين، وعدّاد الإقلاع —
 *_device-scoped_ وقيم مضادة للعبث لا معنى لها على جهاز آخر.
 */
class BackupRepository(
    private val settings: SettingsStore,
    private val database: SawnDatabase,
) {

    /** يجمع الحالة الكاملة الحالية في وثيقة قابلة للترميز. */
    suspend fun buildDocument(): BackupDocument {
        val items = database.blockDao().allItems()
        val schedules = database.blockDao().allSchedules()
        val days = database.protectedDaysDao().all()

        return BackupDocument(
            profile = BackupProfile(
                name = settings.profileName.first(),
                age = settings.profileAge.first(),
            ),
            settings = BackupSettings(
                masterEnabled = settings.masterEnabled.first(),
                alwaysOn = settings.alwaysOn.first(),
                blockDoh = settings.blockDoh.first(),
                blockAds = settings.blockAds.first(),
                blockNotifications = settings.blockNotifications.first(),
                pornGuard = settings.pornGuard.first(),
                safeSearchForce = settings.safeSearchForce.first(),
                reduceMotion = settings.reduceMotion.first(),
                locale = settings.locale.first(),
                deepFocusEnabled = settings.deepFocusEnabled.first(),
                deepFocusWhitelist = settings.deepFocusWhitelist.first().toList(),
                nightGuardEnabled = settings.nightGuardEnabled.first(),
                nightGuardStartMin = settings.nightGuardStartMin.first(),
                nightGuardEndMin = settings.nightGuardEndMin.first(),
                accountabilityName = settings.accountabilityName.first(),
                accountabilityContact = settings.accountabilityContact.first(),
                nudgesEnabled = settings.nudgesEnabled.first(),
            ),
            blockedItems = items.map { item ->
                BackupItem(
                    type = item.type.name,
                    value = item.value,
                    label = item.label,
                    enabled = item.enabled,
                    interceptMode = item.interceptMode.name,
                    pauseSeconds = item.pauseSeconds,
                    customPrompt = item.customPrompt,
                    openLimitPerDay = item.openLimitPerDay,
                    dailyLimitMinutes = item.dailyLimitMinutes,
                    cooldownMinutes = item.cooldownMinutes,
                )
            },
            schedules = schedules.map { schedule ->
                BackupSchedule(
                    name = schedule.name,
                    daysMask = schedule.daysMask,
                    startMinute = schedule.startMinute,
                    endMinute = schedule.endMinute,
                    enabled = schedule.enabled,
                )
            },
            protectedDays = days,
            enabledFeatures = settings.enabledFeatureKeys.first().toList(),
            onboardingAnswers = settings.onboardingAnswers.first(),
        )
    }

    /**
     * يطبّق وثيقة مستعادة. القوائم والجداول تُستبدل بالكامل (نسخة احتياطية تعني
     * «حالة الجهاز حينها» لا «إضافة إليها»)، أما الأيام المحمية فتُدمج حتى لا تُفقد
     * أيامٌ سُجّلت بعد تاريخ النسخة.
     *
     * كل الكتابة داخل معاملة واحدة: لو فشل أي صف، تبقى القاعدة كما كانت — لا قائمة
     * محذوفة بلا بديل.
     */
    suspend fun applyDocument(doc: BackupDocument) {
        settings.setProfile(doc.profile.name, doc.profile.age)
        doc.onboardingAnswers?.let { settings.setOnboardingAnswers(it) }

        with(doc.settings) {
            settings.restoreMasterEnabled(masterEnabled)
            settings.setAlwaysOn(alwaysOn)
            settings.setBlockDoh(blockDoh)
            settings.setBlockAds(blockAds)
            settings.setBlockNotifications(blockNotifications)
            settings.setPornGuard(pornGuard)
            settings.setSafeSearchForce(safeSearchForce)
            settings.setReduceMotion(reduceMotion)
            settings.setLocale(locale)
            settings.setDeepFocus(deepFocusEnabled)
            // قائمة بيضاء فارغة مع تفعيل التركيز = حجب كل شيء بما فيه الهاتف؛
            // نستخدم الافتراضية بدل ذلك.
            settings.setDeepFocusWhitelist(
                if (deepFocusWhitelist.isEmpty()) DeepFocusPolicy.DEFAULT_WHITELIST
                else deepFocusWhitelist.toSet(),
            )
            settings.setNightGuard(nightGuardEnabled, nightGuardStartMin, nightGuardEndMin)
            settings.setAccountability(accountabilityName, accountabilityContact)
            settings.setNudgesEnabled(nudgesEnabled)
        }

        // نافذتا الفكّ لا تُستعادان: نافذة خروج قديمة يجب ألا تبقى مفتوحة بعد الاستيراد.
        settings.clearDeepFocusUnlock()
        settings.setNightGuardUnlock(0L)

        database.withTransaction {
            database.blockDao().deleteAllItems()
            doc.blockedItems.forEach { item ->
                val type = runCatching { BlockType.valueOf(item.type) }.getOrNull() ?: return@forEach
                val mode = runCatching { InterceptMode.valueOf(item.interceptMode) }
                    .getOrDefault(InterceptMode.BLOCK)
                database.blockDao().upsertItem(
                    BlockedItem(
                        type = type,
                        value = item.value,
                        label = item.label,
                        enabled = item.enabled,
                        createdAt = 0L,
                        interceptMode = mode,
                        pauseSeconds = item.pauseSeconds,
                        customPrompt = item.customPrompt,
                        openLimitPerDay = item.openLimitPerDay,
                        dailyLimitMinutes = item.dailyLimitMinutes,
                        cooldownMinutes = item.cooldownMinutes,
                    ),
                )
            }

            database.blockDao().deleteAllSchedules()
            doc.schedules.forEach { schedule ->
                database.blockDao().upsertSchedule(
                    Schedule(
                        name = schedule.name,
                        enabled = schedule.enabled,
                        daysMask = schedule.daysMask,
                        startMinute = schedule.startMinute,
                        endMinute = schedule.endMinute,
                    ),
                )
            }

            doc.protectedDays.forEach { date ->
                database.protectedDaysDao().upsert(
                    ProtectedDayEntity(date = date, source = "backup", recordedAt = System.currentTimeMillis()),
                )
            }
        }

        // الميزات: تُستبدل (المفعّلة في النسخة تُفعَّل، وما ليس فيها يُطفأ).
        val current = settings.enabledFeatureKeys.first()
        current.forEach { key -> if (key !in doc.enabledFeatures) settings.setFeatureEnabled(key, false) }
        doc.enabledFeatures.forEach { key -> settings.setFeatureEnabled(key, true) }
    }
}
