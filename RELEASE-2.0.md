# صَوْن (Sawn) 2.0.0 — تقرير الإصدار النهائي

## الحالة: ✅ جاهز للإصدار

### البناء
- **assembleDebug**: ✅ (BUILD SUCCESSFUL)
- **assembleRelease**: ✅ (BUILD SUCCESSFUL) — 36.03 MB
- **lint**: ✅ (0 errors)
- **tests**: ✅ (`:core:domain:test` + `:core:data:testDebugUnitTest` + `:app:testDebugUnitTest` — BUILD SUCCESSFUL)
- **apksigner verify**: ✅ (v2 + v3 verified)

### التوقيع
- **Keystore**: `sawn-release.keystore` (2680 bytes — نسخة من backup)
- **Alias**: `warden`
- **SHA-256 (cert)**: `3E:2F:FE:2F:03:EF:0C:95:06:A8:63:FB:32:B6:3E:5E:8E:D2:A4:4E:D9:9E:81:9C:03:06:DA:8B:AE:2D:1D:86`
- **SHA-256 (file)**: `B2B9EC8D65AABC7482E703E4FA9B88AAAD580893232FBA4C20693CBB85BA17DD`

### الإصدار
- **versionCode**: 20
- **versionName**: 2.0.0
- **minSdk**: 26 · **targetSdk**: 36 · **compileSdk**: 36

### ما أنجز (Plan A + Plan B)
1. ✅ إعادة تسمية Warden → Sawn (`com.sawn.app`)
2. ✅ 3 وحدات Gradle (`:core:domain`, `:core:data`, `:app`)
3. ✅ Hilt DI + Dawn design system (Cairo font, warm palette)
4. ✅ Onboarding «فجر الأمل» (ترحيب + ملف + 4 أسئلة + خطة شخصية)
5. ✅ Home «لوحة الفجر» (صفحة واحدة، لا تبويبات)
6. ✅ Settings (ملف · لغة · PIN · أقسام · مسح · About)
7. ✅ عداد الأيام من `protected_days` (مصدر الحقيقة)
8. ✅ i18n AR/EN + RTL/LTR + أرقام لاتينية
9. ✅ `userDisabledProtection` flag — الحماية لا تتوقف إلا من داخل التطبيق
10. ✅ Restart arbiter (`BootReceiver` + `KeepAliveWorker` + `GuardHealthWorker`)
11. ✅ Setup wizard (sideload detection, restricted-settings deep-link, Play Protect)
12. ✅ Dormant infrastructure (ads/entitlement/update — معطلة)
13. ✅ وثائق: PRIVACY.md, PLAY_COMPLIANCE.md, CHANGELOG.md, install guides, site guides, README, SECURITY.md

### ما لم يُنفَّذ (Plan B — ميزات 12)
الـ 12 ميزة (تحدي 30 يوم، محلل محفزات، حارس ليلي، شريك مساءلة، بطاقة أسبوع، خريطة سنة، إشعارات نية، ويدجت، بصمة، تركيز عميق، نسخة `.sawnbak`، وضع ولي أمر) **لم تُنفَّذ** — تبقى كـ scaffold معطل. يتطلب إصدار 2.1+.

### الملفات (APK)
- `app/build/outputs/apk/release/app-release.apk` (36.03 MB)
- Location: `C:\Users\pc\Desktop\claude code\warden\app\build\outputs\apk\release\`

### Git
- Branch: `sawn-rebuild`
- Last commit: `55f8a2b feat(plan-b): userDisabledProtection flag + restart arbiter enforcement + site guides + 2.0.0/v20`
- Working tree: clean