# صَوْن (Sawn) 2.0 — دليل إنجاز المهمة الأساسية (Plan A)

الفرع: `sawn-rebuild` · التسمية: `2.0.0` (تُرفع في Plan B) · التسليم: تطبيق «صَوْن» يعمل بالمميزات الحالية بالبنية والتصميم الجديدين

## ما أنجز

- **rename كامل**: `com.sawn.app` في `com.warden.blocker` → كل المصادر/الموارد/البناء/التوقيع المتغير (نفس keystore، بصمة مختلفة لكن لا يوجد تحديث من 1.x — تثبيت نظيف موثق)
- **3 وحدات**: `:core:domain` (Kotlin صافٍ + اختبارات، بلا Android) · `:core:data` (Room v3 + DataStore + المستودعات + اختبارات Robolectric) · `:app` (Compose + الخدمات + Hilt)
- **Hilt** (توفير موحّد للمفردات عبر AppModule + نقطة دخول EntryPoint للحفاظ على توافق المواقع القديمة)
- **نظام تصميم «فجر الأمل»**: Cairo (خط متغير، OFL)، لوحة ألوان دافئة، خلفيات متدرجة ناعمة، HeroShield/DawnCard/ProgressStrip/PillCTA/ProtectionToggleCard/PresetChips/FeatureRow/ToolTile/MetricCard/SectionHeader/ProtectionWeekChart/SawnNumbers (أرقام لاتينية)
- **الترحيب الجديد**: ترحيب + ملف محلي (اسم/عمر) + 4 أسئلة (مصدر · هدف · التزام · محتوى) + «خطتك الشخصية» بتعاقب متحرك — كل الإجابات محلية 100%
- **الشاشة الرئيسية «لوحة الفجر»**: تحية + درع مرسوم + مفاتيح مباشرة بلا تبويبات + شرائط تقدّم داكنة + أدوات
- **الإعدادات**: ملفي · اللغة (AR/EN/النظام عبر AppCompat) · الأمان (PIN حالي + إزالة) · الأقسام (الحماية/المتصفح/الخصوصية) · مسح كل البيانات · عن صَوْن (الإصدار + بصمة SHA-256)
- **عداد الأيام من الصفر**: جدول `protected_days` (مصدر الحقيقة) — `ProtectionDayPolicy` باختبارات (سلسلة، أطول سلسلة، في خطر، خريطة حرارية)
- **i18n كاملة**: عربي في `values-ar/` + إنجليزي في `values/` (lint: 0 أخطاء، MissingTranslation 0) — أرقام لاتينية دائمًا، RTL/LTR حسب اللغة
- **Build أخضر**: assembleDebug · testDebugUnitTest · lint (0 خطأ) · assembleRelease (يُخرج APK موقّع بـ v2/v3)

## أدوات تحققت من عملها
- `:core:domain:test` — 23+✓ (+14 ProtectionDayPolicy)
- `:core:data:testDebugUnitTest` — 5/5 ✓
- `:app:testDebugUnitTest` — ✓ (نفس عدد ما قبل إعادة البناء)
- `lint` — **صفر أخطاء** (لا MissingTranslation)
- `assembleRelease` — يُنتج APK موقّعًا بـ v2/v3 (قياس الحجم في التشغيل القادم)

## المتروكة للخطة B (12 ميزة + التحصين)
- التحدي 30 يوم · محلل المحفزات · الحارس الليلي · شريك المساءلة · بطاقة أسبوعك · خريطة السنة · إشعارات النية · ويدجت · بصمة · التركيز العميق · نسخة `.sawnbak` مشفّرة · وضع ولي الأمر
- `userDisabledProtection` + `RestartArbiter` + معالجة الإيقاظ بعد السحب من recents
- معالج الأذونات (الإعدادات المقيدة) · Always-on VPN · Private DNS · Play Protect دليل التثبيت
- النسخة الموقعة بالكامل (release, R8 كامل, versionName 2.0.0 / versionCode 20) + وثائق الامتثال (PRIVACY.md, PLAY_COMPLIANCE.md, CHANGELOG.md) + SPEC/README مُعاد كتابتها

## قيود معروفة صادقة
- لا يوجد جهاز/محاكي متاح هنا — لم يُختبر على جهاز فعلي (التثبيت/التفعيل/البقاء في الخلفية)
- نسخة 2.0 تفصل كليًا عن تثبيت 1.x (تثبيت نظيف — إلغاء تثبيت Warden قديمًا موثق في دليل التثبيت)
- طبقة الإصدار الجديد لم يُختبر على جهاز (التحقق هنا = build + tests + lint فقط)
- 104 موارد غير مستخدمة (سلاسل قديمة من موجات سابقة) — لم تُنظّف لأنها غير خطرة؛ تُنظّف في Plan B
