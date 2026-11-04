package com.warden.blocker.feature

import java.time.LocalDate

/**
 * حساب سلسلة الأيام، نقيٌّ وقابل للاختبار.
 *
 * الخطأ القديم: كان الرقم المخزَّن يُعرض كما هو، فتظل السلسلة تعرض «12 يوماً»
 * حتى لو مضت أيام بلا حماية. الآن يُخزَّن الطول الخام، ويُشتق المعروض مقابل
 * تاريخ اليوم: يوماً مفقوداً يعني 0 فوراً لا بعد فتح التطبيق.
 */
object StreakPolicy {

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    /** السلسلة التي يجب أن يراها المستخدم الآن — بلا أي كتابة. */
    fun visibleStreak(raw: Int, lastActiveDay: Long, today: Long): Int = when {
        raw <= 0 -> 0
        lastActiveDay <= 0L -> 0
        // آخر يوم محمي هو أمس أو اليوم: السلسلة ما زالت حيّة حتى ينتهي اليوم.
        // lastActiveDay > today يعني أن الساعة رُجِّعت، فلا نعاقب على ذلك.
        lastActiveDay >= today - 1 -> raw
        else -> 0
    }

    /**
     * السلسلة حيّة لكن لم يُسجَّل اليوم بعد — لحظة تذكير لا إنذار. واجهة الرئيسية
     * تستخدمها لتطلب من المستخدم فتح الحماية اليوم قبل أن تنقضي السلسلة.
     */
    fun isAtRisk(raw: Int, lastActiveDay: Long, today: Long): Boolean =
        raw > 0 && lastActiveDay == today - 1

    /** (الطول الخام الجديد، آخر يوم محمي) بعد تسجيل [today] يوماً محميّاً. */
    fun afterActiveDay(raw: Int, lastActiveDay: Long, today: Long): Pair<Int, Long> = when {
        lastActiveDay == today -> raw.coerceAtLeast(1) to today
        lastActiveDay == today - 1 -> (raw + 1).coerceAtLeast(1) to today
        lastActiveDay > today -> raw.coerceAtLeast(1) to today
        else -> 1 to today
    }
}
