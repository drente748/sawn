package com.warden.blocker.block

/**
 * سياسة القفل الزمني — نقية بالكامل (JVM-testable): أثناء قفل نشط، لا إيقاف حماية
 * ولا رفع قفل قبل انتهاء المدة. القفل مطلق (حسم المستخدم 2026-11-01).
 */
object LockdownPolicy {

    enum class DurationUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS }

    const val MIN_MINUTES: Long = 1L
    const val MAX_MONTHS: Long = 12L

    /** القفل نشط إذا المدة مستقبلية — `until > now`. صفر يعني بلا قفل. */
    fun isActive(until: Long, now: Long): Boolean = until > now

    /** تحويل مدة يدوية إلى millis، أو null إن كانت خارج [1 دقيقة .. 12 شهراً]. */
    fun validateManual(amount: Long, unit: DurationUnit): Long? {
        if (amount < MIN_MINUTES) return null
        val millis = when (unit) {
            DurationUnit.MINUTES -> amount * 60_000L
            DurationUnit.HOURS -> amount * 3_600_000L
            DurationUnit.DAYS -> amount * 86_400_000L
            DurationUnit.WEEKS -> amount * 7 * 86_400_000L
            DurationUnit.MONTHS -> amount * 30L * 86_400_000L
        }
        // 12 شهراً = 360 يوماً بخمن الشهر الثابت.
        val max = MAX_MONTHS * 30L * 86_400_000L
        return if (millis in 60_000L..max) millis else null
    }
}
