package com.sawn.app.update

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * بنية التحديث داخل التطبيق — مُفعلة جزئيًا (القراءة فقط).
 *
 * صيغة التوزيع الحالية = APK مباشر من الموقع، لذلك:
 *   - يقرأ [RemoteManifestUpdateChecker] مانيفيست JSON من عنوان HTTPS ثابت.
 *   - إن وُجد إصدار أحدث، يُعيد [UpdateStatus.Available] (مع رابط APK).
 *   - تثبيت التحديث (تنزيل + ACTION_VIEW) يتم من طبقة UI (Activity) — لا من هنا.
 *
 * مانيفيست نموذجي (يُستضاف على GitHub Pages / raw.githubusercontent.com):
 * {
 *   "versionCode": 21,
 *   "versionName": "2.1.0",
 *   "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v2.1.0/Sawn-2.1.0.apk",
 *   "changelog": "…",
 *   "mandatory": false
 * }
 *
 * التفعيل (للمالك):
 *   1. استضف المانيفيست على عنوان HTTPS ثابت (انظر docs/UPDATE_HOSTING.md).
 *   2. عيّن [MANIFEST_URL] إلى ذلك العنوان (أو احتفظ بالـ placeholder معطلاً).
 *   3. (اختياري) فعّل [RemoteManifestUpdateChecker] في AppModule بدلاً من DisabledUpdateChecker.
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
    data class Error(val message: String) : UpdateStatus
}

/** عقد التحقق من وجود تحديث. */
interface UpdateChecker {
    suspend fun check(currentVersionCode: Int): UpdateStatus
}

/** الرد الافتراضي: لا شيء — لا يُظهر أي تنبيه، لا يتصل بالإنترنت. */
object DisabledUpdateChecker : UpdateChecker {
    override suspend fun check(currentVersionCode: Int): UpdateStatus = UpdateStatus.Disabled
}

/**
 * قارئ مانيفيست واحد من عنوان HTTPS عام.
 * - لا تبعية شبكة جديدة (HttpURLConnection مدمج).
 * - مهلة 10 ثوانٍ، لا إعادة محاولة (التحقق (check) يُستدعى عند فتح التطبيق).
 * - عند فشل الشبكة: [UpdateStatus.Error] (لا crash، لا toast).
 */
class RemoteManifestUpdateChecker(
    private val manifestUrl: String = MANIFEST_URL,
) : UpdateChecker {

    override suspend fun check(currentVersionCode: Int): UpdateStatus {
        if (manifestUrl.contains("example.invalid") || manifestUrl.isBlank()) {
            return UpdateStatus.Disabled
        }
        return try {
            val conn = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return UpdateStatus.Error("HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val remoteCode = json.optInt("versionCode", 0)
            if (remoteCode <= currentVersionCode) {
                UpdateStatus.UpToDate
            } else {
                UpdateStatus.Available(
                    UpdateInfo(
                        versionName = json.optString("versionName", ""),
                        versionCode = remoteCode,
                        apkUrl = json.optString("apkUrl", ""),
                        changelog = json.optString("changelog", ""),
                        mandatory = json.optBoolean("mandatory", false),
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w("SawnUpdate", "manifest check failed", t)
            UpdateStatus.Error(t.message ?: "unknown")
        }
    }

    companion object {
        /**
         * عنوان المانيفيست. عيّنه إلى عنوان HTTPS ثابت (GitHub Pages / raw.githubusercontent.com)
         * لتفعيل التحقق. القيمة الافتراضية (example.invalid) = معطل.
         */
        const val MANIFEST_URL: String = "https://example.invalid/sawn-updates/manifest.json"
    }
}