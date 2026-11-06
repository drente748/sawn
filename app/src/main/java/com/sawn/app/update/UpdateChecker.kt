package com.sawn.app.update

/**
 * بنية التحديث داخل التطبيق — مُعدّة لم تُفعّل بعد.
 * صيغة التوزيع الحالية = APK مباشر من الموقع، لذلك سيكون التحديث اليدوي هو المسار الأساسي في 2.0.0،
 * وبنية "التحديث من داخل التطبيق" (in-app update prompt + تنزيل + استبدال APK) تُفعّل لاحقًا.
 * لإضافة المسار فعليًا لاحقًا: أضف رابط https بنسخة المانيفيست من موقعك/GitHub Releases،
 * وادفعل [enabled] إلى true — لا تكتب أي طلب شبكة قبل ذلك.
 */

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String,
    /** تحديث إجباري (إن وُجدت إصلاحات حرجة في حماية الأطفال) — لا يُستخدم الآن. */
    val mandatory: Boolean = false,
)

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data object Disabled : UpdateStatus
}

/** عقد التحقق من وجود تحديث — بدون أي اتصال فعلي اليوم. */
interface UpdateChecker {
    suspend fun check(): UpdateStatus
}

/** الرد الافتراضي: لا شيء — لا يُظهر أي تنبيه، لا يتصل بالإنترنت. */
object DisabledUpdateChecker : UpdateChecker {
    override suspend fun check(): UpdateStatus = UpdateStatus.Disabled
}

/**
 * (للاستخدام المستقبلي) قارئ مانيفيست واحد من عنوان HTTPS عام:
 * {"versionCode": 21, "versionName": "2.1.0", "apkUrl": "https://…/Sawn-2.1.0.apk",
 *  "changelog": "…", "mandatory": false}
 * لم يُفعّل في 2.0.0: لا اتصال، لا رابط مميز، لا تنزيلات في الخلفية.
 * عند التفعيل: هنا تكون قراءة المانيفيست وتثبيت التحديث عبر ACTION_VIEW للـ APK (نفس نمط تثبيت التطبيق العادي).
 */
object RemoteManifestUpdateChecker : UpdateChecker {
    const val MANIFEST_URL_PLACEHOLDER: String = "https://example.invalid/sawn-updates/manifest.json"

    override suspend fun check(): UpdateStatus = UpdateStatus.Disabled
}
