# PROGRESS.md — الحالة التقنية

> آخر تحديث: 2026-11-12 — تطهير الهوية + سياسة النظافة (فرع `feat/identity-purge`)

## 1. الحالة الحالية

| | |
|---|---|
| الفرع | `feat/identity-purge` (من `feat/optional-vpn`) — على GitHub |
| آخر commit | `bbe1d89` — تطهير الهوية (هذا الملف نفسه في نفس الـ commit) |
| الإصدار | 2.2.0 / `versionCode 31` (لم يتغيّر) |
| المرحلة | ما بعد «الأمان والموثوقية» — لا تنظيف سلوكي |
| البناء | `./gradlew test :app:compileDebugKotlin :app:lintDebug --offline` |
| الاختبارات | 320 (`:core:domain`) + 34 (`:core:data`) + 92 (`:app`) = 446، صفر فشل |

## 2. الميزات العاملة

- [x] فلترة DNS محلية (VpnService) + حجب التطبيقات (AccessibilityService) — طبقتان مستقلتان
- [x] وضعا الحماية: **كامل** (DNS + وصول) و**خفيف** (وصول فقط، بلا إذن VPN)
- [x] حجب المتصفحات عبر قراءة شريط العنوان (لا WebView، بحدّ 80 عقدة)
- [x] القفل الزمني: تمديد فقط، لا تقصير ولا مسح
- [x] وضع الانضباط + PIN + DeviceAdmin + الجداول + جلسات التركيز
- [x] سلسلة الأيام كمصدر حقيقة (Room) + ترحيل 4→5
- [x] تدخّل تلقائي للتحديثات + **التحقق من توقيع APK قبل فتح المثبّت**
- [x] مراقب الحماية: فحص صارم كل 3 دقائق، فترة سماح 30 ثانية، self-heal محدود، إشعار دائم
- [x] اختبارات الثوابت كـ properties (Kotest): القفل، الجداول، حدود النطاقات
- [x] **القفل الفوري (Panic)**: زر في الرئيسية + بلاطة إعدادات سريعة، حجب كامل 15/30/60
      دقيقة، لا يُلغى ولا يُقصَّر، يعود بعد إعادة التشغيل

## 3. قيد العمل / التالي

- [x] **تطهير الهوية** منجزة (`bbe1d89`) — ما عدا البنود التي تحتاج قراراً بشرياً في §6
- [x] **إغلاق الديون المعلّقة الخمسة** منجزة — انظر §9
- [ ] Decision مطلوب: هل تُحذف روابط `github.io` (قناة التحديث) من التوثيق؟ (§6)
- [ ] Decision مطلوب: هل تُحذف عبارات «مجاني» عن المنتج من `privacy.md` / `play-compliance.md`؟ (§6)
- [ ] Decision مطلوب: تغيير اسم مفتاح التوقيع (`local.properties`) — يحتاج إعادة توليد الـ keystore

## 4. سجل المراحل

| # | المرحلة | الفرع | الحالة |
|---|---|---|---|
| 1 | المرشّحات الأساسية (`:core:domain` نقية، Kotest) | `sawn-rebuild` | مكتملة |
| 2 | VPN اختياري — وضع «كامل/خفيف» | `feat/optional-vpn` (`93b1973`) | مكتملة |
| 3 | توثيق التحقق | `feat/optional-vpn` (`14f2cd8`) | مكتملة |
| 4 | الأمان والموثوقية: توقيع APK + مراقب الوصول + ثوابت Properties | `feat/optional-vpn` (`27a787a`) | مكتملة |
| 5 | مراجعة: قارئ شهادات واحد + `verifyApkSignature` لا يرمي | `feat/optional-vpn` (`c2d16d0`) | مكتملة |
| 6 | **تطهير الهوية + سياسة النظافة** | `feat/identity-purge` (`bbe1d89`) | **مكتملة** |
| 7 | **إغلاق الديون المعلّقة** (حارس API، ثوابت المولّد، LICENSE، AGENTS.md، الوسم) | `feat/identity-purge` | **مكتملة** |
| 8 | **القفل الفوري (Panic Button) + Emergency Lockdown** | `feat/optional-vpn` | **قيد التحقق** |

## 5. الديون التقنية

| # | الدين | الأثر | ملاحظة |
|---|---|---|---|
| 1 | ~~خطأ lint: `ImageScreeningOrchestrator.kt` (`capture()` تتطلب API 30)~~ | ~~منخفض~~ | **مغلق**: الحارس `SDK_INT < R` نُقل إلى `maybeScreenRetry()` نفسها — الحماية قريبة من الاستدعاء، وبوابة lint تمرّ بلا أخطاء |
| 2 | مسار توقيع API 28+ غير مُختبَر على جهاز | متوسط | صورة Robolectric المتاحة بلا اتصال هي API 26 فقط؛ القاعدة مغطّاة في `:core:domain` |
| 3 | `GuardHealthWorker.doWork()` غير مُختبَر end-to-end | منخفض | `androidx.work:work-testing` غير متاح بلا اتصال؛ القرار مُغطّى بـ`GuardHealthPolicy` |
| 4 | `release/Sawn-2.2.0.apk` (artifact مُتجاهَل) يحوي الاسم القديم داخل الـ dex | منخفض | يُعاد بناؤه عند الإصدار؛ ليس ملفاً متتبَّعاً |
| 5 | `EmergencyLockdownWorker.doWork()` غير مُختبَر end-to-end | منخفض | `work-testing` غير متاح بلا اتصال؛ أثر العامل (`endLockdown`) مُختبَر مباشرة |
| 6 | `qsTile.state` غير قابل للاختبار تحت Robolectric | منخفض | لا يوجد ربط بنظام الإعدادات السريعة في Robolectric، فاختبار البلاطة يتحقق من *الجلسة* التي تُشغّل الحالة |
| 7 | ضغط طويل على بلاطة القفل | منخفض | أندرويد لا يسلّم الضغط الطويل لخدمة البلاطة كحدث مستقل (لا `onLongClick`) — التفصيل في `CHANGES.md` §9.4 |

## 6. المهام المعلّقة (تحتاج قراراً بشرياً)

| # | البند | لماذا لم يُنفَّذ |
|---|---|---|
| 1 | `local.properties`: `SAWN_KEY_ALIAS` و`SAWN_STORE_PASSWORD` يحويان الاسم القديم | مفاتيح توقيع — لا تُمس. تغيير الاسم يعني إعادة توليد الـ keystore وكل التوقيعات |
| 2 | `app/licenses/Cairo-OFL.txt` | رخصة خط من طرف ثالث (OFL) — الإسناد واجب، ليست من نصوص هذا المشروع |
| 3 | روابط `github.io` (عنوان بيان التحديث) في `README`/`docs` | رابط تشغيلي مُخبوز في البناء، لا رابط مستودع مصدر |
| 4 | عبارات «مجاني» عن المنتج في `docs/privacy.md` و`docs/play-compliance.md` | تصف السعر لا المصادر المفتوحة — وحذفها يعني تعديل نصوص سياسة/خصوصية |
| 5 | `docs/BlockingFeatureEvaluation.md` | ورقة عمل غير متتبَّعة — أُضيفت إلى `.gitignore` وبقيت على القرص |
| 6 | `release/Sawn-2.2.0.apk` | مُتجاهَل في git؛ يُعاد بناؤه عند الإصدار |

## 7. سجل التحقق

| التاريخ | الأمر | النتيجة |
|---|---|---|
| 2026-11-12 | `./gradlew test` | BUILD SUCCESSFUL — 325 (`:core:domain`) + 34 (`:core:data`) + 53×2 (`:app`)، صفر فشل |
| 2026-11-12 | `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL |
| 2026-11-12 | `./gradlew :app:lintDebug` | **0 أخطاء** (كان خطأ واحداً سابقاً) |
| 2026-11-12 | `git grep -in <الاسم القديم>` | لا نتيجة في الملفات المتتبَّعة |
| 2026-11-12 | `git grep -inE <أسماء المستودعات الخارجية>` | لا نتيجة |
| 2026-11-12 | `git grep -n "github.com" -- docs README.md` | لا نتيجة |

## 8. القيود المعمارية

- `:core:domain` كوتلن نقية **بلا أي** اعتماد أندرويد — كل قرار قابل للاختبار على JVM.
- `:core:data` = Room + DataStore، وال真相 الوحيد لسلسلة الأيام هو `protected_days`.
- `:app` = Compose + الخدمات + الـ workers؛ الوصول إلى الحاويات عبر `LegacyContainer` (Hilt).
- **لا يُكتب** منصوص سياسة/خصوصية/عن التطبيق/وصف/ترخيص جديدة.
- **لا يُحذف**: ملفات مشتبه في كونها مشتقّة من GPL/AGPL، `applicationId`/`namespace`، مفاتيح DataStore/Room القديمة، مفاتيح التوقيع.
- `versionCode` لا يتغيّر إلا بإذن صريح.

## 9. الملفات المحذوفة

| التاريخ | الملف | السبب | التحقق |
|---|---|---|---|
| 2026-11-12 | `LICENSE` | المشروع ليس مفتوح المصدر، ولا نصوص ترخيص | `git grep` + بناء نظيف |
| 2026-11-12 | `scripts/repoint_hosting.py` | نص لمرة واحدة نُفِّذ بالفعل (كل استبدالاته تبديل نفسها) وغير مُشار إليه، ويحمل الاسم القديم | `git grep repoint_hosting` → لا نتيجة |
| 2026-11-12 | `CHANGES.md` §8.4 «المصادر» | قسم كامل بحقوق الاستشهاد بمستودعات خارجية | `git grep` → لا نتيجة للأسماء |
| 2026-11-12 | 73 استيراداً ميتاً في 17 ملف Kotlin | استيرادات لم يبقَ لها مرجع | `./gradlew test` نظيف |
| 2026-11-12 | `core/domain/.../lockdown/` (3 ملفات) | `:core:domain` كان بلا مجلد `lockdown` | اختبار الخصائص |
| 2026-11-12 | `app/.../system/EmergencyLockdownWorker.kt`، `.../EmergencyLockdownEnforcer.kt`، `.../tile/SawnLockdownTileService.kt`، `.../ui/lockdown/` (3 ملفات) | القفل الفوري | `./gradlew test` |
| 2026-11-12 | `core/data/.../LockdownSessionCodec.kt` | ترميز الجلسة كـ JSON | اختبار المتجر |
| 2026-11-12 | `app/src/main/res/drawable/ic_lockdown.xml` | أيقونة البلاطة | `lint` |

**الملفات التي عُدِّلت لا حُذفت** (تطهير نصي): `README.md`، `docs/SECURITY.md`، `docs/spec.md`،
`docs/install-ar.md`، `docs/install-en.md`، `docs/site-activation-ar.md`، `docs/site-activation-en.md`،
`docs/CHANGELOG.md`، `docs/update-hosting.md`، `scripts/verify-release.ps1`،
`core/domain/.../ApkSignaturePolicy.kt`.

## 9.1 الديون المُغلقة (2026-11-12)

| # | البند | ما قُفل | التحقق |
|---|---|---|---|
| 1 | `ImageScreeningOrchestrator.kt:84` — `capture()` تتطلب API 30 على minSdk 26 | حارس `SDK_INT < R` **نُقل إلى `maybeScreenRetry()`** — كان محمياً في `maybeScreen()` قبل إطارين، فكان lint لا يستطيع إثباته. الآن الحماية قريبة من الاستدعاء | `lintDebug` → **0 أخطاء** (كان 1) |
| 2 | ثوابت مولّدات النوافذ | `ScheduleWindows` صار كائناً مشتركاً، وثابت جديد **«the generated windows always satisfy their contract»** يفرض `start < end` و`start > end` على كل عيّنة | `./gradlew test` |
| 3 | `LICENSE` | أُعيد بسطرين: `Proprietary — All rights reserved.` | `git ls-files` |
| 4 | `AGENTS.md` في جذر المستودع | أُضيف — سياسة المشروع + Clean State Policy تسير مع المستودع | `git ls-files` |
| 5 | وسم `v2.2.0` | نُقل من `2ae3d41` إلى `c14bc46` (الحالة التي كان عليها `main` وقت النشر). الفرق بينهما **وثائقي فقط** (`docs/update-hosting.md`)، فالمُ artifact المنشور يبقى قابلاً لإعادة البناء. الـ Release ما زال مربوطاً بالوسم، و`sha256` في البيان المنشور يطابق بصمة الـ APK | `git ls-remote` + `gh release view` |

## 10. قواعد التحديث

1. `PROGRESS.md` يُحدَّث **بعد كل مهمة**: §1 (الحالة)، §3 (التالي)، §4 (السجل)، §7 (التحقق)، §9 (المحذوفات).
2. قبل الحذف: `git grep` (+ `git log --diff-filter=A` لملفات الجذر) — وبعده `./gradlew test`.
3. التسليم: شجرة نظيفة (`git status --short` فارغ) + `git ls-files --others --exclude-standard` فارغ + GitHub محدّث.
4. لا تُبدأ المهمة التالية قبل: شجرة نظيفة + GitHub محدّث + `PROGRESS.md` محدّث.
5. كل حذف يُسجَّل في §9 مع السبب والتحقق.
