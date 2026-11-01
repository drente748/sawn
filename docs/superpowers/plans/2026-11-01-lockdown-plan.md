# Warden Lockdown — خطة التنفيذ (القفل الزمني)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** قفل زمني مطلق يمنع إيقاف الحماية والحذف لمدة يختارها المستخدم، ثابت عبر إعادة التشغيل، مع بطاقة داعمة قبل أي تأكيد.

**Architecture:** طبقة سياسة نقية `LockdownPolicy` (TDD) + مفتاحان في DataStore + نقاط رفض في كل مسارات الإيقاف (الرئيسية/Sections/PIN/admin) + تفعيل بخطوتين (بطاقة → Device Admin → كتابة القفل) + BootReceiver يعيد التسليح. الواجهة فوق الثيم الفاتح القائم.

**Tech Stack:** Kotlin، Jetpack Compose (Material 3)، DataStore، Device Admin (AdminManager القائم)، WorkManager (لا جديد).

**Spec:** `docs/superpowers/specs/2026-11-01-lockdown-design.md`

## Global Constraints
- قفل مطلق: لا رفع قبل انتهاء المدة ولا برقم سري.
- التفعيل بخطوتين: بطاقة+تأكيد → شاشة Device Admin → كتابة فقط بعد موافقة النجاح (onActivityResult).
- تفعيل بلا PIN؛ القفل يفرض masterEnabled=true وbest-effort VPN.
- قفل جديد أثناء نشط: يعلّق قاعدة جديدة من الآن (تأكيد عبر البطاقة).
- حدود الإدخال اليدوي: 1 دقيقة → 12 شهراً.
- العدّ عبر `trustedNow()` (نمط StrictPolicy الحالي).
- البطاقة النفسية تعرض أرقاماً حقيقية فقط (streak + جلسات التركيز) بأسلوب داعم غير لائم.
- البقاء على الثيم الفاتح (WardenPalette) وRTL.
- versionName 1.0.1 / versionCode 13 في نهاية الموجة.

## Review Focus
- أي مسار إيقاف انسي متى القفل «تسريب رفض»: `HoldToGuardSwitch`، `AdminProtectionCard`، `PinCard`، `setStrictMode` لأسفل؛ تكتبها الاختبارات في Task 2/4.
- توقيت كتابة القفل: لا قيمة تُكتب قبل موافقة admin — test في Task 3.
- reboot لا يُفلت القفل (BootReceiver يعيد الربط) — Task 5.
- removeFrom Lancaster: مدة يدوية خارج [1min, 12mo] تُرفض — Task 2.

---

### Task 1: مفتاحا DataStore + تدفقات القفل

**Files:**
- Modify: `app/src/main/java/com/warden/blocker/data/SettingsStore.kt`

**Interfaces:**
- Produces: `LOCKDOWN_UNTIL: longPreferencesKey("lockdown_until")` (default 0) + `untilLockdownEnds(): Flow<Long>` + `suspend fun setLockdownUntil(endsAtMs: Long)` (يكتب 0 للإلغاء) + `fun trustedNow(): Long` (موجود سلفاً — إعادة استعمال).
- Produces: `LockdownState(until: Long)` — سطر بسيط في نفس الملف للتدفقات.

- [ ] **Step 1:** أضف المفتاح والتدفق والكاتب بنمط `PORN_GUARD` — default 0.
- [ ] **Step 2:** Run: `.\gradlew.bat :app:compileDebugKotlin` — Expected: PASS.
- [ ] **Step 3:** Commit: `feat(lockdown): datastore keys + flows`

### Task 2: LockdownPolicy (نقي، TDD)

**Files:**
- Create: `app/src/main/java/com/warden/blocker/block/LockdownPolicy.kt`
- Test: `app/src/test/java/com/warden/blocker/block/LockdownPolicyTest.kt`

**Interfaces:**
- Produces: `object LockdownPolicy { fun isActive(until: Long, now: Long): Boolean; fun guardBlocked(until: Long, now: Long): Boolean (== isActive); fun validateManual(amount: Long, unit: DurationUnit): Long? (millis, null خارج [1min..12mo]) ; enum class DurationUnit { MINUTES, HOURS, DAYS, WEEKS, MONTHS } }`
- Consumes: لا شيء أندرويدي — صرف JVM.

- [ ] **Step 1: Write the failing test**
  - `isActive(0, _) == false`
  - `isActive(future, before) == true`; `isActive(future, future) == false` (الحد الدقيق: until > now)
  - `validateManual(1, MINUTES) != null`; `validateManual(0, MINUTES) == null`; `validateManual(13, MONTHS) == null`; `validateManual(12, MONTHS) != null`
- [ ] **Step 2:** Run: `:app:testDebugUnitTest --tests LockdownPolicyTest` — Expected: FAIL (unresolved).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run — Expected: PASS.
- [ ] **Step 5:** Commit: `feat(lockdown): pure LockdownPolicy + tests`

### Task 3: ViewModel + التفعيل بخطوتين (بطاقة → admin → كتابة)

**Files:**
- Modify: `app/src/main/java/com/warden/blocker/ui/WardenViewModel.kt`
- Modify: `app/src/main/java/com/warden/blocker/ui/SettingsControls.kt`

**Interfaces:**
- Consumes: `LockdownPolicy.validateManual`, `AdminManager.isActive/enableIntent`, `vm.setLockdown` (جديد).
- Produces: `vm.lockdownUntil: StateFlow<Long>`; `vm.placeLockdown(endsAtMs: Long, onNeedAdmin: (Unit) -> Unit)` — لا تكتب حتى استدعاء `vm.commitLockdown()` من onActivityResult بعد التحقق `AdminManager.isActive(context)`; `vm.supportCardData(): Pair<Int, Int>` (streak, sessions).

- [ ] **Step 1:** ViewModel: `lockdownUntil` StateFlow + دوال place/commit وفوقها منطق masterEnabled=true عند commit.
- [ ] **Step 2:** `SettingsControls`: بطاقة «قفل الحماية» جديدة (Protection): عرض countdown حي إذا نشط، وإلا شبكة أزرار (30د/1س/6س/يوم/3أ/أسبوع/أسبوعان/شهر/3ش/6ش) + إدخال يدوي (رقم+وحدة) مع تحقق LockdownPolicy.
- [ ] **Step 3:** الضغط = بطاقة داعمة ( diálogo) بأسلوب داعم: عرض streak/جلسات الاختصار + 10ث قبل «تأكيد القفل» — بعدها: إن admin غير فعال → `adminLauncher.launch(ACTION_ADD_DEVICE_ADMIN)`; عند العودة الناجحة → `commitLockdown()`.
- [ ] **Step 4:** Run: `:app:compileDebugKotlin` PASS ثم `:app:test` PASS.
- [ ] **Step 5:** Commit: `feat(lockdown): two-step activation + supportive card`

### Task 4: نقاط الرفض في كل مسارات الإيقاف

**Files:**
- Modify: `app/src/main/java/com/warden/blocker/ui/Screens.kt` (HoldToGuardSwitch + شاشة الرئيسية عبارة القفل)
- Modify: `app/src/main/java/com/warden/blocker/ui/SettingsControls.kt` (تعطيل admin/PIN cards أثناء القفل)
- Modify: `app/src/main/java/com/warden/blocker/ui/settings/SettingsSections.kt` (قسم الحماية: مفاتيح الجناح disabled gdy lock نشط)

**Interfaces:**
- Consumes: `LockdownPolicy.isActive(until, trustedNow())`, `vm.lockdownUntil`.
- Produces: كل مسار الإيقاف عندما القفل نشط: معطّل بنص «القفل نشط — متبقٍ X».

- [ ] **Step 1:** `HoldToGuardSwitch`: إذا قفل نشط: عرض «مقفل حتى …» + تعطيل off (منع onToggleBlocking(false)).
- [ ] **Step 2:** `AdminProtectionCard` & `PinCard`: أزرار الطف管理局 تعطيل + نص السبب.
- [ ] **Step 3:** Main hero: سطر «قفل نشط — X متبقية» تحت الدرع.
- [ ] **Step 4:** Run compile+test PASS. Commit: `feat(lockdown): refusal points at every off-path`

### Task 5: BootReceiver + استمرارية القفل

**Files:**
- Modify: `app/src/main/java/com/warden/blocker/system/BootReceiver.kt`

**Interfaces:**
- Consumes: `SettingsStore.untilLockdownEnds()`, `LockdownPolicy.isActive`، `AdminManager`.
- Produces: عند boot: إن القفل نشط: تأكيد masterEnabled=true + best-effort سلاح VPN + إثبات القفل نشط (لا كتابة جديدة).

- [ ] **Step 1:** تنفيذاً: قراءة المدة و-fork أفعال الحماية المسلحة.
- [ ] **Step 2:** Run compile+test PASS.
- [ ] **Step 3:** Commit: `feat(lockdown): boot persistence`

### Task 6: الإصدار 1.0.1 + APK

**Files:**
- Modify: `app/build.gradle.kts` (versionCode 13, versionName 1.0.1)

- [ ] **Step 1:** تحديث الإصدار.
- [ ] **Step 2:** `:app:test` PASS ثم `assembleRelease` (ملاحظة: قد يُوقف بسبب ضغط الذاكرة — أعد تفقّده).
- [ ] **Step 3:** نسخ إلى `C:\Users\pc\Desktop\Warden-1.0.1.apk`.
- [ ] **Step 4:** Commit: `chore(release): 1.0.1 (13)`
