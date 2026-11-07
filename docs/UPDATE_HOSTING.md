# UPDATE_HOSTING.md — كيف نستضيف التطبيق ونرسل التحديثات مجانًا

> **الهدف:** تمكين صَوْن من إرسال تحديثات (versions) للمستخدمين مباشرة داخل التطبيق،
> دون Google Play، وباستضافة **مجانية**.

---

## 1) كيف يعمل الأمر (نظرة عامة)

```
┌──────────────┐         ┌─────────────────────┐         ┌──────────────┐
│  تطبيق صَوْن │  (1)    │  manifest.json      │         │  GitHub      │
│  (على جهاز   │ ──────► │  (استضافته مجاني)   │         │  Releases    │
│  المستخدم)   │         │  { versionCode,     │         │  (APK)       │
│              │ ◄────── │    apkUrl, ... }    │         │              │
│              │  (2)    └─────────────────────┘         └──────┬───────┘
│              │                                                 │
│              │  (3) يعرض "تحديث متاح"                        │
│              │  (4) المستخدم يضغط "تحديث"                     │
│              │ ──────────────────────────────────────────────►│ (5) تنزيل APK
│              │ ◄──────────────────────────────────────────────│
│              │  (6) تثبيت (Android Package Installer)         │
└──────────────┘                                                 └──────────────┘
```

**الخطوات:**
1. عند فتح التطبيق (أو دورياً)، يقرأ `RemoteManifestUpdateChecker` مانيفيست JSON من عنوان HTTPS ثابت.
2. يقارن `versionCode` في المانيفيست مع `BuildConfig.VERSION_CODE` (الحالي).
3. إن كان أحدث → `UpdateStatus.Available` → UI يعرض dialog "تحديث متاح" مع changelog.
4. المستخدم يضغط "تحديث" → تنزيل APK (DownloadManager أو OkHttp) إلى `cache/updates/`.
5. بعد اكتمال التنزيل → `ACTION_VIEW` مع FileProvider URI → نظام Android يُثبّت.
6. بعد التثبيت، `MY_PACKAGE_REPLACED` broadcast → `BootReceiver` يُعيد التسليح.

**لا شيء تلقائي:** لا تثبيت صامت، لا اتصال إجباري، لا tracking. كل شيء opt-in من المستخدم.

---

## 2) خيارات الاستضافة المجانية (لـ APK + manifest)

| الخدمة | التكلفة | حدود | الأفضل لـ |
|---|---|---|---|
| **GitHub Releases** | مجاني | لا حد (للـ public repos) | **APK** (36 MB) + مانيفيست |
| **GitHub Pages** | مجاني | 1 GB | مانيفيست JSON + صفحة تحميل |
| **jsDelivr CDN** | مجاني | لا حد (لـ GitHub files) | CDN سريع للـ manifest |
| **Cloudflare R2** | مجاني (10 GB/شهر) | 10 GB storage | بديل if GitHub unavailable |
| **Firebase Hosting** | مجاني (10 GB/شهر) | 10 GB storage | بديل |
| **SourceForge** | مجاني | لا حد | بديل (أبطأ) |

**التوصية:** GitHub Releases للـ APK + GitHub Pages (أو raw.githubusercontent.com) للـ manifest.

---

## 3) إعداد GitHub (خطوة بخطوة)

### 3.1 إنشاء repository
```
# على GitHub: New Repository → "sawn-updates" (public)
# أو استخدم repository موجود لصَوْن
```

### 3.2 رفع manifest.json
أنشئ ملف `manifest.json` في repository (أو في `docs/` لموقع GitHub Pages):

```json
{
  "versionCode": 20,
  "versionName": "2.0.0",
  "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v2.0.0/Sawn-2.0.0.apk",
  "changelog": "إصلاحات وتحسينات.",
  "mandatory": false
}
```

**URL ثابت (raw):**
```
https://raw.githubusercontent.com/<owner>/<repo>/main/manifest.json
```

أو (GitHub Pages):
```
https://<owner>.github.io/<repo>/manifest.json
```

### 3.3 رفع APK (GitHub Release)
```
# على GitHub: Releases → Draft a new release
# Tag: v2.0.0
# Attach: Sawn-2.0.0.apk
# Publish
```

### 3.4 تفعيل التحقق في التطبيق
في `app/src/main/java/com/sawn/app/update/UpdateChecker.kt`:
```kotlin
const val MANIFEST_URL: String = "https://raw.githubusercontent.com/<owner>/<repo>/main/manifest.json"
```

في `app/src/main/java/com/sawn/app/di/AppModule.kt`:
```kotlin
@Provides
@Singleton
fun updateChecker(): UpdateChecker = RemoteManifestUpdateChecker()  // بدلاً من DisabledUpdateChecker
```

---

## 4) كيف يعمل التحقق (الكود)

```kotlin
// UpdateChecker.kt
class RemoteManifestUpdateChecker(private val manifestUrl: String) : UpdateChecker {
    override suspend fun check(currentVersionCode: Int): UpdateStatus {
        // 1. HTTP GET manifest (10s timeout)
        // 2. Parse JSON
        // 3. Compare versionCode
        // 4. Return Available / UpToDate / Error
    }
}
```

**في ViewModel / Activity:**
```kotlin
val status = updateChecker.check(BuildConfig.VERSION_CODE)
when (status) {
    is UpdateStatus.Available -> showUpdateDialog(status.info)
    is UpdateStatus.UpToDate -> { /* silent */ }
    is UpdateStatus.Disabled -> { /* silent */ }
    is UpdateStatus.Error -> { /* log only */ }
}
```

**تنزيل + تثبيت:**
```kotlin
// Use DownloadManager (built-in, no extra deps)
val request = DownloadManager.Request(Uri.parse(info.apkUrl))
    .setDestinationInExternalFilesDir(context, "updates", "Sawn-${info.versionName}.apk")
    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
downloadManager.enqueue(request)

// After download completes (broadcast or poll):
val apkFile = File(context.getExternalFilesDir("updates"), "Sawn-${info.versionName}.apk")
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
```

---

## 5) متطلبات AndroidManifest (أُضيفت)

```xml
<!-- permission -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- provider -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

`res/xml/file_paths.xml`:
```xml
<paths>
    <cache-path name="updates" path="updates/" />
    <external-cache-path name="external_updates" path="updates/" />
</paths>
```

---

## 6) إعدادات Play Protect (تذكير)

عند تثبيت APK من خارج المتجر، Google Play Protect يعرض تحذير:
- «قد يكون هذا التطبيق غير آمن» → **مزيد من التفاصيل** → **التثبيت على أي حال**.
- هذا طبيعي لأي تطبيق side-loaded. لتجنبه: نشر على Google Play (لاحقًا).

---

## 7) checklist للتفعيل (عند 2.1)

- [ ] إنشاء GitHub repository (public) + GitHub Pages
- [ ] رفع `manifest.json` (مع versionCode الأحدث)
- [ ] رفع APK في GitHub Release
- [ ] تعيين `MANIFEST_URL` في `UpdateChecker.kt`
- [ ] تبديل `DisabledUpdateChecker` → `RemoteManifestUpdateChecker()` في `AppModule`
- [ ] إضافة UI (dialog "تحديث متاح" + زر "تحديث")
- [ ] تنزيل APK + تثبيت عبر FileProvider
- [ ] اختبار على جهاز (لا محاكي — needs real install)
- [ ] تحديث `PRIVACY.md` (إذا أضفنا telemetry — حالياً لا)

---

## 8) ملاحظات أمان

- **لا telemetry**: التحقق من التحديث = طلب GET واحد (manifest). لا بيانات تُرسل.
- **HTTPS فقط**: `usesCleartextTraffic=false` (موجود).
- **لا تثبيت صامت**: المستخدم يجب أن يضغط "تثبيت" في dialog النظام.
- **FileProvider محدود**: فقط `cache/updates/` — لا وصول لملفات أخرى.
- **لا download خارج cache**: APKs تُحفظ في `getExternalFilesDir("updates")`، تُحذف при "مسح كل بياناتي".

---

## 9) التكلفة: $0

| المكون | الخدمة | التكلفة |
|---|---|---|
| APK hosting (36 MB) | GitHub Releases | $0 |
| manifest.json | GitHub Pages / raw | $0 |
| CDN | jsDelivr (لـ GitHub) | $0 |
| Domain | github.io subdomain | $0 |
| SSL | GitHub (auto) | $0 |

**الإجمالي: $0/شهر** — مثالي لمشروع open-source.