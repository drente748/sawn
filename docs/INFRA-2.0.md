# صَوْن (Sawn) — دليل الإنجاز: البنية المعطلة للدفع والإعلانات والتحديث

> **الحالة:** 2.0.0 جاهزة، **مجانية بالكامل** — أي تفعيل مستقبلي = قرار معلن بإصدار جديد.

## الملفات الجديدة (commit 5697ee4)

| الملف | الغرض |
|---|---|
| `app/src/main/java/com/sawn/app/ads/AdsController.kt` | عقد إعلان معطل؛ **لا شيء لأي مستخدم**. النموذج المستقبلي: إعلانات شبكات خارجية لمن **لم يدفع**. |
| `app/src/main/java/com/sawn/app/entitlement/EntitlementService.kt` | تصنيف FREE/PAID + `NoopEntitlementService` = كل الميزات **مفعّلة ومجانية**. |
| `app/src/main/java/com/sawn/app/update/UpdateChecker.kt` | عقد + `RemoteManifestUpdateChecker` معطل؛ **لا اتصال بشبكة، لا تبعية جديدة**. |
| `docs/CHANGELOG.md` | سجل 2.0.0 المفصّل (هذا الحفظ). |
| `docs/PROGRESS-2.0.md` | دليل الإنجاز العام. |
| `docs/PRIVACY.md` | سياسة الخصوصية العربية. |
| `docs/PLAY_COMPLIANCE.md` | امتثال بلاي + فصل "البنية المعطلة مسبقًا". |
| `docs/install-ar.md`, `docs/install-en.md` | دليل تثبيت APK. |

## التسلسل المنفذ

1. `entitlement/EntitlementService.kt` — FREE/PAID؛ Noop يجعل كل شيء مجاني.
2. `ads/AdsController.kt` — عقد أولي Noop؛ عُدّل لاحقًا إلى "معطل بالكامل بلا SDK".
3. `entitlement/EntitlementService.kt` — إعادة كتابة مع نص التوزيع خارج المتجر.
4. `ads/AdsController.kt` — إعادة كتابة نهائية (النص الموجود).
5. `docs/PLAY_COMPLIANCE.md` — فصل "البنية المعطلة مسبقًا".
6. `update/UpdateChecker.kt` — عقد + Noop + `RemoteManifestUpdateChecker` معطل.
7. `docs/CHANGELOG.md`, `docs/PROGRESS-2.0.md`, `docs/PRIVACY.md`, `docs/install-ar.md`, `docs/install-en.md`.
8. `app/build.gradle.kts` — versionCode 19 / versionName 2.0.0.
9. `app/src/main/java/com/sawn/app/di/AppModule.kt` — نقاط حقن + تصحيح `com.sawn.app.ads.NoopAdsController`.
10. `docs/SANDBOX-2.0.md` — **لُغيت**: حذفها عبر `git reset --hard HEAD` (آخر commit `24449ba`).

**ملاحظة:** ملف `docs/SANDBOX-2.0.md` **مُحذوف** في مستودع Git.

## حالة البناء

- **غير مؤكد**: `assembleRelease` + `lint` بعد التعديلات (انقطع البناء بالخلفية بسبب إعادة تشغيل الخادم).
- **أمر التحقق:**
  ```
  .\\gradlew.bat :app:assembleRelease lint --console=plain
  ```
- `versionCode` 19 / `versionName` 2.0.0 مُعدّ في `build.gradle.kts`.

## شروط تفعيل الإعلانات والاشتراك (PLAY_COMPLIANCE.md)

1. تحديث PRIVACY.md (المعريفات + الغرض + الاحتفاظ بالبيانات).
2. إضافة SDK مصرّح به + التصريح في Play Console + Data Safety متطابق.
3. زر "إزالة الإعلانات" واضح في الإعدادات لكل مستخدم.
4. إعلان السعر/التفعيل قبل أي ميزة مدفوعة.