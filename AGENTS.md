# AGENTS.md — سياسة المشروع

> هذا الملف يسير مع المستودع: أي وكيل يعمل على هذا المشروع يقرأه.

## هوية المشروع

- **Sawn (صَوْن)** — حجب أندرويد محلي بالكامل: لا خادم، لا حسابات، لا تتبّع.
- الشعار المعروض: `صَوْن` / `Sawn` — لا اسم آخر، ولا روابط لمستودع مصدر خارجي.
- نصوص **السياسة/الخصوصية/عن التطبيق/الوصف/الترخيص** لا تُكتب ولا تُعاد صياغتها بلا طلب صريح.

## البنية (must not break)

- `:core:domain` — كوتلن نقية **بلا أي اعتماد أندرويد**. كل قرار размеَن هنا، لأن كل قرار
  يجب أن يكون قابلاً للاختبار على JVM بلا جهاز.
- `:core:data` — Room + DataStore. مصدر الحقيقة لسلسلة الأيام هو `protected_days`.
- `:app` — Compose + الخدمات (VPN / Accessibility / DeviceAdmin / QS tile / workers).
  الوصول للحاويات عبر `LegacyContainer` (Hilt) — مسار واحد فقط.
- `versionCode` لا يتغيّر إلا بإذن صريح.

## Clean State Policy (سياسة النظافة)

بعد كل مهمة:

**أ) احذف البقايا**
- ما يُحذف: ملفات `.kt` يتيمة، resources غير مستخدمة، imports ميتة، TODO/FIXME قديمة.
- ضوابط: `git grep` قبل الحذف، ثم `./gradlew test` بعده، ثم تسجيل المحذوفات في
  `PROGRESS.md` §9.
- ما **لا** يُحذف: ملفات مشتبه في كونها مشتقّة من GPL/AGPL، `applicationId`/`namespace`
  القديمة، مفاتيح DataStore/Room القديمة، مفاتيح التوقيع، وأي ملف لا تعرف وظيفته.

**ب) نظافة المستودع**
```bash
git status --short                        # فارغ
git ls-files --others --exclude-standard  # فارغ
git ls-files --deleted                    # فارغ
```
`.gitignore` يغطي: `*.apk` `*.aab` `*.jks` `*.keystore` `/build/` `.gradle/`
`local.properties` `.idea/` `*.log`.

**ج) حدّث الوثائق** — `PROGRESS.md` (الحالة، التالي، سجل المراحل، سجل التحقق، المحذوفات)
و`CHANGES.md`.

**د) ادفع** — commit + push (بعد تأكيد المستخدم) + أبلغ بالـ hash.

**هـ) لا تبدأ المهمة التالية قبل**: شجرة نظيفة + GitHub محدّث + `PROGRESS.md` محدّث.

## أوامر التحقق

```bash
./gradlew test                       # كل وحدات الاختبار (JVM)
./gradlew :app:compileDebugKotlin    # تجميع التطبيق
./gradlew :app:lintDebug             # بوابة lint — يجب أن تمرّ بلا أخطاء
```

## ممنوع في مسار العمل هذا

- `assembleRelease` / `bundleRelease` / التوقيع / الرفع — إلا بطلب صريح.
- `connectedAndroidTest` — يحتاج جهازاً.
- `push` إلى `main` — إلا بطلب صريح.
