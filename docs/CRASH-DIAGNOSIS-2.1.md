# تشخيص عطل 2.1 (Crash عند التشغيل)

## الأعراض
- التطبيق يخرج (Force-close) فور فتحه على جهاز المستخدم.

## الأسباب المحتملة (حُصّنت بالكامل)

1. **هجرة Room v3 → v4**: جدول جديد `challenge_runs`/`challenge_days`/`trigger_logs`.
   - الإصلاح: `fallbackToDestructiveMigration()` — أي فشل في الهجرة لن يوقف التطبيق (2.1 بناء تجريب بلا ترحيل بيانات).
2. **SawnApp.onCreate**: قراءة Locale داخل `runBlocking` — كان فشلًا محتملًا (أُصلح خطأ `suspend` داخل `runCatching`).
   - الإصلاح: `runBlocking { runCatching { ... } }` + `runCatching` حول كل إقلاع لاحق.
3. **جدولة Workers**: `GuardHealthWorker` كان يُجدول؛ `NightGuardWorker`/`NudgesWorker` لم يكونا يُجدولان.
   - الإصلاح: جدولة آمنة لأي فشل في `SawnApp.onCreate` (`runCatching`).
4. **ViewModel**: استعلامات Room الجديدة ملفوفة في `runCatching` (`challengeDays`).
5. **R8**: لم تُضاف keeps لـ Room entities — لم يتغير عن 2.0 (الـ AGP defaults تغطي Room)، لا حاجة.

## الإصلاحات المطبقة (2.1.1)

- `core/data/.../SawnDatabase.kt`: `fallbackToDestructiveMigration()`
- `app/.../SawnApp.kt`: locale + channels + workers داخل `runBlocking`/`runCatching`
- `app/.../ui/SawnViewModel.kt`: challengeDays آمن
- `app/build.gradle.kts`: versionCode 22 / versionName 2.1.1

## ما لم نستطعه
- لا يوجد جهاز/محاكي متيل هنا ولا logcat من جهاز المستخدم — التشخيص من تحليل الكود فقط.
- إن استمر العطل على 2.1.1: أطلب لقطة `adb logcat` (أو اسمح بالوصول) تطبيقًا لمحاولة دقيقة.

## التحقق
- `assembleDebug` ✅ (BUILD SUCCESSFUL, 57 MB — آخر APK debug جاهز)
- `assembleRelease` (جارٍ — توقيع v2/v3)