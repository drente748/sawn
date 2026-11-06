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
| `docs/install-ar.md`, `docs/install-en.md` | دليل تثبيت APK (منعكس/إنجليزي). |

## ما أنجزته (التسلسل الحقيقي)

1. **`entitlement/EntitlementService.kt`** — B2B: FREE/PAID؛ Noop يجعل كل شيء مجاني.
2. **`AdsController.kt`** — العقد الأولي: Noop؛ تالٍ تم تعديله إلى "معطل بالكامل بلا SDK".
3. **`entitlement/EntitlementService.kt`** — تم إعادة كتابتها مع نص B2B الكامل.
4. **`ads/AdsController.kt`** — إعادة كتابة نهائية (النص الموجود).
5. **`docs/PLAY_COMPLIANCE.md`** — قسم البنية المعطلة (النص الحالي).
6. **`update/UpdateChecker.kt`** — عقد + Noop + RemoteManifestUpdateChecker.
7. **`docs/CHANGELOG.md`**, **`docs/PROGRESS-2.0.md`**, **`docs/PRIVACY.md`**, **`docs/install-ar.md`**, **`docs/install-en.md`**.
8. **`app/build.gradle.kts`** — 2.0.0 / 19.
9. **`app/src/main/java/com/sawn/app/di/AppModule.kt`** — نقاط حقن + تصحيح `NoopAdsController`.
10. **`docs/SANDBOX-2.0.md`** — تم إنشاؤها ثم حذفها (`git rm --cached` + `git reset --hard`).

**نقطة مهمة:** تم حذف `docs/SANDBOX-2.0.md` نهائيًا عبر `git reset --hard HEAD` (آخر commit `24449ba`).

## حالة البناء

- **غير مؤكد**: بناء `assembleRelease` بعد التعديلات (كان في الخلفية وانقطع بسبب إعادة تشغيل الخادم).
- يُرجى التحقق يدويًا: `.\\gradlew.bat :app:assembleRelease lint --console=plain`.
- `versionCode` 19 / `versionName` 2.0.0 مُعدّ في `build.gradle.kts`.

## شروط تفعيل الإعلانات والاشتراك (PLAY_COMPLIANCE.md)

1. تحديث PRIVACY.md (المعريفات + الغرض + الاحتفاظ بالبيانات).
2. إضافة SDK مصرّح به + تصريح Play Console (إعلانات/شراء) + Data Safety متطابق.
3. زر "إزالة الإعلانات" واضح في الإعدادات لكل مستخدم.
4. إعلان السعر/التفعيل قبل أي ميزة مدفوعة.