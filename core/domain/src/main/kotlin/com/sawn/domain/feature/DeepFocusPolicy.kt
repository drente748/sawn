package com.sawn.domain.feature

/**
 * «وضع التركيز العميق» — قائمة بيضاء تُسمح فقط؛ الفك = PIN + 10 ثوانٍ.
 * الاتصال/الرسائل/الخرائط افتراضيًا في القائمة البيضاء.
 */
object DeepFocusPolicy {

    /** حزم التطبيقات المسموحة افتراضيًا (اتصال/رسائل/خرائط). */
    val DEFAULT_WHITELIST: Set<String> = setOf(
        "com.android.dialer", // الهاتف
        "com.google.android.dialer",
        "android.telecom", // Telecom (framework)
        "com.google.android.apps.maps",
        "com.waze",
        "com.android.messaging", // الرسائل
        "com.google.android.apps.messaging",
    )

    /** هل يُسمح لهذا الحزمة خلال التركيز العميق؟ */
    fun isAllowed(packageName: String, whitelist: Set<String>): Boolean =
        packageName in whitelist

    /** يُطلب PIN مع انتظار 10 ثوانٍ قبل الفك (يُمنع الاندفاع). */
    const val UNLOCK_DELAY_SECONDS = 10
}
