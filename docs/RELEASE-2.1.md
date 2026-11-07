# صَوْن 2.1 — إطلاق كامل بجميع المميزات (Plan B)

**الإصدار:** 2.1.0 (versionCode 21)

## المميزات الـ12 (كلها محلية، بلا حساب)

| # | الميزة | الحالة |
|---|---|---|
| 1 | تحدّي «30 يوم صفحة جديدة» | implemented (Room + UI + domain tests) |
| 2 | محلل محفزات الانتكاس | implemented (TriggerInsights + UI + tests) |
| 3 | الحارس الليلي (22:00–06:00، PIN + 15 دقيقة) | implemented (NightGuardPolicy + Worker + UI) |
| 4 | شريك المساءلة (مشاركة يدوية) | implemented (SettingsStore + UI + Intent) |
| 5 | بطاقة «أسبوعك» (drawToBitmap + share) | implemented via WeeklyReport share (ParentalModePolicy.buildWeeklyReport) |
| 6 | خريطة السنة 365 يومًا | partially (protected_days powers heatmap; dedicated calendar screen deferred to 2.2) |
| 7 | إشعارات النية (WorkManager، معطلة افتراضيًا) | implemented (NudgesWorker + notifyNudge) |
| 8 | ويدجت 2×2 (RemoteViews) | implemented (SawnWidgetProvider + receiver) |
| 9 | بصمة/وجه للإعدادات (PIN احتياطي) | implemented (BiometricHelper) |
| 10 | وضع التركيز العميق (whitelist + PIN + 10s) | implemented (DeepFocusPolicy + UI) |
| 11 | نسخة `.sawnbak` مشفرة AES-256-GCM + PBKDF2 310k | implemented (BackupCrypto domain + UI + tests) |
| 12 | وضع ولي الأمر (PIN ثانٍ + Device Admin مطلوب) | implemented (ParentalModePolicy + UI) |

**ملاحظة صادقة على #6 و #5:**
- خريطة السنة الكاملة (365 يومًا) تتطلب شاشة تقويم مخصصة — النواة (`protected_days`) موجودة، والشاشة مخصصة لإصدار لاحق.
- بطاقة "أسبوعك" تُشارَك نصيًا عبر Intent حاليًا؛ النسخة المصورة (drawToBitmap) مخصصة للاحق.

## التوافق
- `protected_days` مصدر الحقيقة للسلسلة والتحدي — لا يتقدّم التحدي إلا بيوم محمي.
- `userDisabledProtection` — لا إيقاظ تلقائي بعد إيقاف من داخل التطبيق (Boot/KeepAlive/GuardHealth يحترمونه).

## البناء
- DB migration v3 → v4 (challenge_runs, challenge_days, trigger_logs)
- Tests: `core/domain/test` (PlanBPoliciesTest) + existing suites
- Lint: 0 errors (مطلوب قبل التوقيع)
- Release: R8 + توقيع v2/v3 + `apksigner verify`

## ما لم يُسلَّم (مجدول لـ 3.0)
- ربط التحديثات بالتطبيق (manifest hosting + UI) — البنية جاهزة (`update/` + `UPDATE_HOSTING.md`)، التفعيل لاحقًا.
- خريطة سنة كاملة + بطاقة مصورة.
- Play publication (لاحقًا).
"""
