# استضافة التحديثات المجانية لصَوْن

يجهّز المشروع مسار نشر اختياريًا باستخدام **GitHub Pages + GitHub Releases + GitHub Actions**. لا يحتاج التطبيق إلى اتصال بالخادم إلا حين يختار المستخدم «تحقّق من وجود تحديث» في الإعدادات. رابط البيان يُضمّن وقت البناء في `BuildConfig.UPDATE_MANIFEST_URL`؛ إذا تُرك فارغًا تبقى ميزة التحديث معطّلة.

## ما الذي يفعله مسار النشر

عند دفع tag مثل `v2.1.5` وتشغيل GitHub Actions:

1. يُبنى APK Release بإصدار Gradle وSDK المثبتين.
2. يُوقّع باستخدام مفتاح التوقيع نفسه الذي وقّع النسخ الموجودة.
3. يُقارَن SHA-256 لشهادة التوقيع مع `SAWN_SIGNING_CERT_SHA256`؛ يفشل النشر إذا لم تتطابق.
4. يُحسب SHA-256 للـ APK، ويُنشر APK باسم `app-release.apk` وملف `manifest.json` في GitHub Release.
5. يُنشر البيان إلى `updates/manifest.json` على فرع `gh-pages`.

عند طلب التحديث، يتحقق التطبيق من أن روابط المانيفست تستخدم HTTPS، وأن رابط APK صالح، وأن بصمة SHA-256 موجودة. بعد التنزيل يُعاد حساب البصمة محليًا؛ لا يُفتح مُثبّت Android قبل تطابقها. يتطلب التثبيت إذن Android لمصدر التثبيت وموافقة المستخدم ولا يوجد تثبيت صامت.

## ربط الاستضافة — مرة واحدة

1. ادفع الكود إلى مستودع GitHub عام (Public)، ثم فعّل **Settings → Pages → Deploy from a branch → `gh-pages` / `/ (root)**. ينشئ مسار العمل الفرع عند أول نشر من رأس الفرع الافتراضي، لذا قد يحتوي أول `gh-pages` على نسخة من ملفات المستودع؛ لا تضع أسرارًا داخل المستودع.
2. من **Settings → Secrets and variables → Actions → New repository secret** أضف:
   - `SAWN_KEYSTORE_BASE64`: محتوى ملف keystore مشفرًا بـ Base64 (`base64 -w 0 sawn-release.keystore` على Linux/macOS). احتفظ بالسر الأصلي في مكان آمن؛ فقده يمنع تحديث التطبيقات المثبتة.
   - `SAWN_STORE_PASSWORD`: كلمة مرور المخزن.
   - `SAWN_KEY_ALIAS`: اسم المفتاح.
   - `SAWN_KEY_PASSWORD`: كلمة مرور المفتاح.
   - `SAWN_SIGNING_CERT_SHA256`: بصمة شهادة SHA-256 بصيغة hex صغيرة من دون نقطتين، مطابقة لـ `apksigner verify --print-certs`. الشهادة الحالية لهذا المشروع: `3e2ffe2f03ef0c9506a863fb32b63e5e8ed2a44ed99e819c0306da8bae2d1d86`؛ إن استخدمت مفتاحًا آخر استبدلها ببصمة مفتاحك.
3. تحقق من أن شهادة keystore هي الشهادة نفسها المستخدمة لتوقيع APK الذي ثبّته المستخدمون. لا تغيّر المفتاح.
4. أعد **بناء التطبيق الأساسي** بعد إعداد المستودع. يستخدم مسار النشر العنوان `https://<owner>.github.io/<repo>/updates/manifest.json` تلقائيًا عبر متغير البناء `SAWN_UPDATE_MANIFEST_URL`. APKات قديمة بُنيت بعنوان المانيفست الفارغ لا يمكنها معرفة عنوان الاستضافة الجديد بأثر رجعي؛ يلزم نشر تحديث ربط لمستخدميها أولًا.
5. ادفع tag يطابق رقم النسخة، مثل `v2.1.5`، من المستودع. بعد نجاح Workflow وتهيئة GitHub Pages، يصبح فحص التحديث متاحًا.

أضف إعداد GitHub Pages، ثم انتظر اكتمال أول Workflow. لا تشغّل عدة workflows للنشر بالتوازي؛ ينشر كل واحد إلى ملف البيان نفسه.

## إصدار يدوي

يمكن تمرير رابط المانيفست العام وقت البناء:

```powershell
$env:SAWN_UPDATE_MANIFEST_URL = "https://<owner>.github.io/<repo>/updates/manifest.json"
./gradlew :app:assembleRelease
```

ويقبل Gradle الخاصية `-PSAWN_UPDATE_MANIFEST_URL=https://...` أيضًا. الروابط غير HTTPS مرفوضة. يبقى المانيفست فارغًا افتراضيًا، فلا يُجرى أي طلب شبكة للتحديث دون إعداد صريح وإعادة بناء.

## صيغة `manifest.json`

```json
{
  "versionCode": 25,
  "versionName": "2.1.5",
  "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v2.1.5/app-release.apk",
  "sha256": "<64 lowercase hexadecimal characters>",
  "changelog": "إصلاحات وتحسينات.",
  "mandatory": false
}
```

لا يُعدّل البيان يدويًا بعد بناء APK؛ الـ Workflow يحسب بصمة SHA-256 وينشر البيان والملف معًا. ارفع `versionCode` لكل إصدار جديد. يجب أن يكون APK Release موقّعًا بالمفتاح نفسه.

## حدود ونقاط أمان

- GitHub Pages وReleases يستضيفان ملفات عامة، وليس هناك أي سر داخل التطبيق.
- الفحص اختياري يدوي؛ لا استعلام تلقائي عند فتح التطبيق، ولا رفع بيانات استخدام.
- بصمة SHA-256 تمنع تثبيت ملف تالف أو مختلف عن الملف المعلن؛ HTTPS يحمي النقل.
- بصمة SHA-256 ليست توقيعًا مستقلًا إذا اختُرق كل من GitHub Pages وGitHub Release معًا. يظل Android يتحقق أيضًا من توقيع APK أثناء التحديث. احتفظ بمفتاح keystore خارج GitHub والكود، واستخدم أسرار Actions فقط.
- ينشر الـ Workflow APK الحالي فقط؛ إعداد الـ tag وتوقيع الإصدار يجب أن يطابقا `versionName` و`versionCode` في Gradle.
- تتطلب ميزة تحديث APK إذن «تثبيت تطبيقات غير معروفة» وموافقة المستخدم على شاشة مثبت Android.
