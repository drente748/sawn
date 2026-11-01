# إصلاح المشاكل الثلاث الكبرى — خطة تنفيذ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** جعل جدولة/جلسات التركيز تُحدّث فلترة VPN فوراً، وإعادة حجب التطبيق عند انتهاء التصريح المؤقت، وتفعيل الوضع الصارم فعلياً على المفتاح الرئيسي وبلاطة الإعدادات السريعة وجلسات التركيز.

**Architecture:** نُخرج قرارات الحجب النقية إلى `BlockingPolicy` و`StrictPolicy` لتكون قابلة لاختبار JVM، ثم نربطها بـ `BlockEngine` و`AccessController` و`WardenVpnService` وخدمة إمكانية الوصول وواجهة Compose. التحديث الدوري للـ VPN يتم عبر مؤقّت 30 ثانية بجانب مراقبة Flows، وإعادة التقييم عند انتهاء التصريح تتم عبر مؤقّت داخل خدمة إمكانية الوصول.

**Tech Stack:** Kotlin، Coroutines/Flow، Jetpack Compose، Room، DataStore، JUnit4، Gradle.

**Spec:** `docs/superpowers/specs/2026-10-28-three-blocker-fixes.md`

## Global Constraints

- لا تغيّر بنية المشروع أو الأنماط الحالية (Compose + Room + DataStore + container).
- احترم كل التغييرات غير المرتبطة الموجودة في working tree؛ لا تعِد كتابة ملف كامل.
- **لا تنفّذ `git commit`** — المستودع يحتوي تغييرات مستخدم غير مرتبطة، والخطة تتحقق بالاختبارات والبناء فقط.
- كل تغيير سلوكي يسبقه اختبار فاشل (TDD)، إلا ربط الخدمات/الواجهة الذي يُغطّى بالبناء والاختبار اليدوي.
- لا تُضف مكتبات جديدة.
- بعد كل مهمة: `.\gradlew.bat testDebugUnitTest --console=plain`.
- التحقق النهائي: اختبارات + `assembleRelease` + توقيع APK، ثم فحص يدوي وفق `DEVICE_TESTING.md`.

## Review Focus

1. **بداية/نهاية جدول والـ VPN يعمل:** يجب أن يتغيّر مجموعة النطاقات خلال 30 ثانية دون لمس المفتاح.
2. **انتهاء تصريح والتطبيق في المقدمة:** يجب أن تظهر شاشة الاعتراض مجدداً، لا أن يبقى التطبيق مفتوحاً.
3. **إعادة منح تصريح بعد الانتهاء:** يجب جدولة الانتهاء التالي، لا الاعتماد على حدث نافذة مفقود.
4. **الوضع الصارم بلا PIN:** يجب ألا يُفعّل أصلاً، وإلا صار قابلاً للتعطيل من الإعدادات بلا تحقق.
5. **إلغاء جلسة تركيز صارمة:** يجب ألا يوقف الـ VPN؛ الرفض غير المتزامن الحالي يوقف الـ VPN قبل معرفة النتيجة.

---

### Task 1: سياسة الحجب + تحديث فلتر الـ VPN

**Files:**
- Create: `app/src/main/java/com/warden/blocker/block/BlockingPolicy.kt`
- Create: `app/src/test/java/com/warden/blocker/block/BlockingPolicyTest.kt`
- Modify: `app/src/main/java/com/warden/blocker/block/BlockEngine.kt`
- Modify: `app/src/main/java/com/warden/blocker/vpn/WardenVpnService.kt`

**Interfaces:**
- Produces: `BlockingPolicy.isActive(masterEnabled: Boolean, alwaysOn: Boolean, focusEndsAt: Long, now: Long, anyScheduleActive: Boolean): Boolean`
- Consumes: `ScheduleEvaluator.anyActive(schedules, cal)` (موجود).

- [ ] **Step 1: اكتب الاختبار الفاشل**

```kotlin
package com.warden.blocker.block

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingPolicyTest {
    private val now = 1_000_000_000L

    @Test fun focusOverridesMasterOff() =
        assertTrue(BlockingPolicy.isActive(false, false, now + 60_000, now, false))

    @Test fun expiredFocusDoesNotOverride() =
        assertFalse(BlockingPolicy.isActive(false, false, now - 1, now, false))

    @Test fun masterOffIgnoresSchedule() =
        assertFalse(BlockingPolicy.isActive(false, false, 0L, now, true))

    @Test fun alwaysOnWithMaster() =
        assertTrue(BlockingPolicy.isActive(true, true, 0L, now, false))

    @Test fun activeScheduleWithMaster() =
        assertTrue(BlockingPolicy.isActive(true, false, 0L, now, true))

    @Test fun idleWhenNothingActive() =
        assertFalse(BlockingPolicy.isActive(true, false, 0L, now, false))
}
```

- [ ] **Step 2: شغّل الاختبار وتأكد من الفشل**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.warden.blocker.block.BlockingPolicyTest" --console=plain`
Expected: FAIL — `BlockingPolicy` غير موجود.

- [ ] **Step 3: نفّذ `BlockingPolicy.kt`**

```kotlin
package com.warden.blocker.block

/** Pure "is blocking enforced right now?" rule, shared by the engine and tests. */
object BlockingPolicy {
    fun isActive(
        masterEnabled: Boolean,
        alwaysOn: Boolean,
        focusEndsAt: Long,
        now: Long,
        anyScheduleActive: Boolean,
    ): Boolean = focusEndsAt > now ||
        (masterEnabled && (alwaysOn || anyScheduleActive))
}
```

- [ ] **Step 4: أعد كتابة `BlockEngine.isBlockingActiveNow` لتستخدم السياسة**

استبدل جسم الدالة فقط. استخدم `nowCal?.timeInMillis` إن مُرّر، وإلا `settings.trustedNow()`، ومرّر `ScheduleEvaluator.anyActive(repo.enabledSchedules(), cal)` إلى `BlockingPolicy.isActive`. لا تغيّر `blockedDomains()`.

- [ ] **Step 5: وسّع مراقبة `WardenVpnService`**

في `watchBlocklistChanges()` استبدل `combine` الحالية بـ:

```kotlin
val changes = combine(
    repo.items, repo.schedules,
    settings.masterEnabled, settings.alwaysOn, settings.blockDoh,
) { _, _, _, _, _ -> Unit }

scope.launch {
    combine(changes, settings.focusEndsAt) { _, _ -> Unit }
        .collect { refreshBlocklist() }
}
scope.launch {
    while (isActive) {
        delay(BLOCKLIST_TICK_MS)
        refreshBlocklist()
    }
}
```

أضف `import kotlinx.coroutines.delay` و`private const val BLOCKLIST_TICK_MS = 30_000L` داخل `companion object`. أبقِ `refreshBlocklist()` كما هي.

- [ ] **Step 6: تحقق**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL، واختبارات `BlockingPolicyTest` الستة ناجحة.

---

### Task 2: إعادة الحجب عند انتهاء التصريح

**Files:**
- Modify: `app/src/main/java/com/warden/blocker/block/AccessController.kt`
- Modify: `app/src/test/java/com/warden/blocker/block/AccessControllerTest.kt`
- Modify: `app/src/main/java/com/warden/blocker/accessibility/AppBlockAccessibilityService.kt`

**Interfaces:**
- Produces: `AccessDecision.AllowUntil(val expiresAt: Long)`
- Produces: `AccessController.decide(item, activeGrantExpiresAt: Long?, opensToday, lastGrantAt, usedMinutesToday, now, pauseDefeats = 0)`
- Consumes: `repo.activeGrant(item.id, now)?.expiresAt` (موجود).

- [ ] **Step 1: حدّث اختبارات `AccessControllerTest`**

غيّر التوقيع في كل الاستدعاءات من `hasActiveGrant: Boolean` إلى `activeGrantExpiresAt: Long?` (استخدم `null` حيث لا يوجد تصريح). استبدل اختبار التصريح النشط وأضف اختبار الانتهاء:

```kotlin
@Test fun activeGrantAllowsUntilExpiry() {
    val expiry = now + 300_000L
    assertEquals(
        AccessDecision.AllowUntil(expiry),
        AccessController.decide(app(), expiry, 0, null, 0, now),
    )
}

@Test fun expiredGrantNoLongerAllows() {
    val d = AccessController.decide(app(), now - 1L, 0, null, 0, now)
    assertTrue(d is AccessDecision.Pause)
}
```

- [ ] **Step 2: شغّل الاختبار وتأكد من الفشل**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.warden.blocker.block.AccessControllerTest" --console=plain`
Expected: FAIL — لا يوجد `AllowUntil` والتوقيع القديم.

- [ ] **Step 3: عدّل `AccessController`**

أضف إلى sealed interface:

```kotlin
/** Let the app through until [expiresAt] (an active temporary grant). */
data class AllowUntil(val expiresAt: Long) : AccessDecision
```

في `decideForPackage`:

```kotlin
val grant = repo.activeGrant(item.id, now)
// ...
return decide(item, grant?.expiresAt, opens, last, used, now, defeats)
```

في `decide` غيّر المعامل إلى `activeGrantExpiresAt: Long?` واستبدل فحص التصريح:

```kotlin
if (activeGrantExpiresAt != null && activeGrantExpiresAt > now) {
    return AccessDecision.AllowUntil(activeGrantExpiresAt)
}
```

- [ ] **Step 4: عدّل خدمة إمكانية الوصول**

1. احذف السطر `if (pkg == packageName) return` من `onAccessibilityEvent` (يجب أن يُحدّث حدث نافذة Warden نفسه `lastPackage`).
2. أضف `private var expiryJob: Job? = null` واستورد `kotlinx.coroutines.Job`.
3. اجعل `handleAppBlock`:

```kotlin
private fun handleAppBlock(pkg: String) {
    if (pkg == lastPackage) return
    lastPackage = pkg
    expiryJob?.cancel()
    expiryJob = null
    interceptedFeatureKey = null
    if (CriticalApps.isCritical(this, pkg)) return
    evaluate(pkg)
}
```

4. انقل جسم القرار إلى `evaluate(pkg: String)`، وبعد `decideForPackage` تحقق `if (lastPackage != pkg) return@launch` قبل أي إجراء. أضف الحالة:

```kotlin
is AccessDecision.AllowUntil -> scheduleExpiryRecheck(pkg, decision.expiresAt)
```

5. أضف:

```kotlin
private fun scheduleExpiryRecheck(pkg: String, expiresAt: Long) {
    expiryJob?.cancel()
    expiryJob = scope.launch {
        val remaining = expiresAt - wardenContainer.settings.trustedNow()
        delay(remaining.coerceAtLeast(0L) + EXPIRY_GRACE_MS)
        if (lastPackage == pkg) evaluate(pkg)
    }
}
```

وأضف `const val EXPIRY_GRACE_MS = 250L` في companion.

- [ ] **Step 5: تحقق**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL. يجب أن يبقى اختبار `activeGrantAllowsUntilExpiry` و`expiredGrantNoLongerAllows` ناجحين.

---

### Task 3: تفعيل الوضع الصارم

**Files:**
- Create: `app/src/main/java/com/warden/blocker/block/StrictPolicy.kt`
- Create: `app/src/test/java/com/warden/blocker/block/StrictPolicyTest.kt`
- Modify: `app/src/main/java/com/warden/blocker/ui/WardenViewModel.kt`
- Modify: `app/src/main/java/com/warden/blocker/MainActivity.kt`
- Modify: `app/src/main/java/com/warden/blocker/ui/Screens.kt`
- Modify: `app/src/main/java/com/warden/blocker/system/WardenTileService.kt`

**Interfaces:**
- Produces: `StrictPolicy.isMasterLocked(strictMode: Boolean, masterEnabled: Boolean): Boolean`
- Produces: `StrictPolicy.effectiveFocusStrict(globalStrictMode: Boolean, requestedStrict: Boolean): Boolean`
- Produces: `StrictPolicy.canCancelFocus(focusStrict: Boolean, focusEndsAt: Long, now: Long): Boolean`
- Produces: `WardenViewModel.strictLocked: StateFlow<Boolean>`
- Produces: `WardenViewModel.canDisableProtectionNow(): Boolean`
- Produces: `WardenViewModel.cancelFocus(onCancelled: () -> Unit = {})`

- [ ] **Step 1: اكتب اختبار `StrictPolicyTest`**

```kotlin
package com.warden.blocker.block

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictPolicyTest {
    private val now = 1_000_000_000L

    @Test fun strictLocksMasterWhileProtectionOn() =
        assertTrue(StrictPolicy.isMasterLocked(true, true))

    @Test fun strictDoesNotLockWhenProtectionOff() =
        assertFalse(StrictPolicy.isMasterLocked(true, false))

    @Test fun nonStrictNeverLocks() =
        assertFalse(StrictPolicy.isMasterLocked(false, true))

    @Test fun globalStrictMakesFocusStrict() =
        assertTrue(StrictPolicy.effectiveFocusStrict(true, false))

    @Test fun strictActiveFocusCannotBeCancelled() =
        assertFalse(StrictPolicy.canCancelFocus(true, now + 60_000, now))

    @Test fun expiredStrictFocusCanBeCleared() =
        assertTrue(StrictPolicy.canCancelFocus(true, now - 1, now))

    @Test fun nonStrictFocusCanBeCancelled() =
        assertTrue(StrictPolicy.canCancelFocus(false, now + 60_000, now))
}
```

- [ ] **Step 2: شغّل الاختبار وتأكد من الفشل**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.warden.blocker.block.StrictPolicyTest" --console=plain`
Expected: FAIL — `StrictPolicy` غير موجود.

- [ ] **Step 3: نفّذ `StrictPolicy.kt`**

```kotlin
package com.warden.blocker.block

/** Pure strict-mode rules shared by the app, the tile, and focus logic. */
object StrictPolicy {
    fun isMasterLocked(strictMode: Boolean, masterEnabled: Boolean): Boolean =
        strictMode && masterEnabled

    fun effectiveFocusStrict(globalStrictMode: Boolean, requestedStrict: Boolean): Boolean =
        globalStrictMode || requestedStrict

    fun canCancelFocus(focusStrict: Boolean, focusEndsAt: Long, now: Long): Boolean =
        !focusStrict || focusEndsAt <= now
}
```

- [ ] **Step 4: عدّل `WardenViewModel`**

1. أضف `strictLocked` عبر `combine(container.settings.strictMode, container.settings.masterEnabled) { s, m -> StrictPolicy.isMasterLocked(s, m) }` مع `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)`.
2. أضف:

```kotlin
suspend fun canDisableProtectionNow(): Boolean =
    !StrictPolicy.isMasterLocked(
        container.settings.strictMode.first(),
        container.settings.masterEnabled.first(),
    )
```

3. في `startFocus` اقرأ `strictMode.first()` ومرّر `StrictPolicy.effectiveFocusStrict(globalStrict, strict)` إلى `settings.startFocus`.
4. استبدل `cancelFocus` بـ:

```kotlin
fun cancelFocus(onCancelled: () -> Unit = {}) = viewModelScope.launch {
    val focusStrict = container.settings.focusStrict.first()
    val endsAt = container.settings.focusEndsAt.first()
    val now = container.settings.trustedNow()
    if (!StrictPolicy.canCancelFocus(focusStrict, endsAt, now)) return@launch
    container.settings.clearFocus()
    FocusEndWorker.cancel(getApplication())
    onCancelled()
}
```

5. في `setStrictMode` ارفض التفعيل بلا PIN:

```kotlin
fun setStrictMode(enabled: Boolean) = viewModelScope.launch {
    if (enabled && !container.settings.hasPin.first()) return@launch
    container.settings.setStrictMode(enabled)
}
```

6. في `clearPin` ارفض الإزالة أثناء الوضع الصارم:

```kotlin
fun clearPin() = viewModelScope.launch {
    if (container.settings.strictMode.first()) return@launch
    container.settings.clearPin()
}
```

- [ ] **Step 5: عدّل `MainActivity`**

استورد `androidx.lifecycle.lifecycleScope` و`kotlinx.coroutines.launch`. اجعل الإيقاف غير المتزامن محكوماً بالسياسة:

```kotlin
fun toggleBlocking(enabled: Boolean) {
    if (enabled) {
        withVpn { VpnController.start(this@MainActivity); vm.setMasterEnabled(true) }
    } else {
        this@MainActivity.lifecycleScope.launch {
            if (!vm.canDisableProtectionNow()) return@launch
            VpnController.stop(this@MainActivity)
            vm.setMasterEnabled(false)
        }
    }
}
```

واجعل `cancelFocus` لا يوقف الـ VPN إلا بعد نجاح الإلغاء:

```kotlin
fun cancelFocus() {
    vm.cancelFocus {
        if (!vm.masterEnabled.value) VpnController.stop(this@MainActivity)
    }
}
```

- [ ] **Step 6: عدّل `HomeScreen` و`HoldToGuardSwitch`**

1. أضف `val strictLocked by vm.strictLocked.collectAsStateWithLifecycle()`.
2. مرّر `locked = strictLocked` إلى `HoldToGuardSwitch`.
3. أضف المعامل `locked: Boolean`، وفي بداية الدالة:

```kotlin
if (enabled && locked) {
    Text("🔒 Strict lock on", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary)
    return
}
```

4. أصلح عنوان بطاقة التركيز ليعتمد على `focusStrict`:

```kotlin
focusEndsAt > System.currentTimeMillis() ->
    if (focusStrict) "Focus active · strictly" else "Focus active"
```

- [ ] **Step 7: عدّل شاشة الإعدادات**

1. نص بطاقة الوضع الصارم: `"Locks the master switch while protection is on. Requires a PIN."`
2. بدّل `onCheckedChange`:

```kotlin
onCheckedChange = { on ->
    if (on && !hasPin) {
        showSetPin = true
    } else {
        vm.setStrictMode(on)
        if (on && !adminActive) adminLauncher.launch(AdminManager.enableIntent(context))
    }
}
```

3. عطّل زر إزالة PIN أثناء الوضع الصارم: `OutlinedButton(onClick = ..., enabled = !strict)`، ونصه `"Turn off strict mode first"` عند `strict`.

- [ ] **Step 8: عدّل `WardenTileService`**

1. استورد `com.warden.blocker.block.StrictPolicy`.
2. في `turnOff`:

```kotlin
val settings = applicationContext.wardenContainer.settings
val enabled = settings.masterEnabled.first()
val strictLocked = StrictPolicy.isMasterLocked(settings.strictMode.first(), enabled)
if (strictLocked || settings.hasPin.first()) {
    openApp()
    return
}
```

3. في `refreshTile` أضف `val strict = settings.strictMode.first()` واجعل العنوان الفرعي:

```kotlin
subtitle = when {
    strict && enabled -> "Strict lock"
    enabled -> "Blocking on"
    else -> "Off"
}
```

- [ ] **Step 9: تحقق**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL، و46 اختباراً ناجحاً (منها 6 BlockingPolicy و10 AccessController و17 StrictPolicy).

---

### Task 4: تحديث الوثائق والتحقق النهائي

**Files:**
- Modify: `README.md`
- Modify: `SPEC.md`
- Modify: `DEVICE_TESTING.md`
- Modify: `SECURITY.md` (فقط حيث يدّعي أن الوضع الصارم غير مفعّل)

- [ ] **Step 1: حدّث الادعاءات**

- `SPEC.md`: احذف السطر `Strict mode is a stored flag; enforcement is not wired yet.` وحدّث بند roadmap ليقول إن القفل مفعّل، مع إبقاء "emergency exit" كعمل مستقبلي.
- `README.md`: وضّح أن الوضع الصارم يقفل المفتاح الرئيسي ما دامت الحماية مفعّلة، وأن جلسة التركيز تصبح صارمة تلقائياً عند تفعيل الوضع الصارم.
- `SECURITY.md`: لا تدّعِ أن الوضع الصارم يمنع إلغاء تفعيل Device Admin من إعدادات النظام؛ اذكر أنه يمنعه داخل التطبيق فقط.
- `DEVICE_TESTING.md`: أضف السيناريوهات الثلاثة أدناه.

- [ ] **Step 2: أضف خطوات الفحص اليدوي**

```markdown
### 7. Schedule / focus filter refresh
- Block example.com. With blocking ON and the VPN running, create a schedule that starts
  one minute from now and covers example.com.
- ✅ At the start time, example.com must fail to load within 30 seconds without toggling Warden.
- Start and cancel a focus session. ✅ The same domain must become blocked/unblocked immediately.

### 8. Timed access expiry
- Open a PAUSE app, wait out the pause, choose "1 min", and stay in the app.
- ✅ After ~1 minute, the intercept screen must appear again over the app.
- Choose "1 min" again. ✅ A second expiry must fire after that minute.
- While a grant is active, go Home and wait past expiry. ✅ No intercept should appear in the background.

### 9. Strict mode
- Set a PIN, enable strict mode, turn protection on.
- ✅ Home shows a strict lock and cannot turn protection off.
- ✅ The Quick Settings tile opens Warden instead of turning protection off.
- Start a focus session with strict unchecked. ✅ It is stored strict and cannot be ended early.
- ✅ Turning strict mode off requires unlocking Settings with the PIN.
- ✅ Remove PIN is disabled while strict mode is on.
- ✅ Device-admin uninstall protection cannot be disabled in-app while strict mode is on.
```

- [ ] **Step 3: شغّل التحقق الكامل**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL، 46 اختباراً، 0 فشل.

Run: `.\gradlew.bat assembleRelease -x lintVitalRelease --console=plain` (مهلة طويلة، 30 دقيقة)
Expected: `app/build/outputs/apk/release/app-release.apk` أحدث من وقت بدء البناء.

- [ ] **Step 4: تحقق من الـ APK**

Run: `& "C:\Users\pc\tools\android-sdk\build-tools\34.0.0\apksigner.bat" verify --verbose "app\build\outputs\apk\release\app-release.apk"`
Expected: `Verifies` و`Verified using v2 scheme ... true`.

انسخه إلى `C:\Users\pc\Desktop\Warden-release.apk` وسلّم المستخدم تعليمات التثبيت اليدوي والفحص.

## Self-Review

- **Spec coverage:** المشاكل الثلاث مغطاة في Tasks 1–3، والوثائق/الفحص في Task 4.
- **Type consistency:** `AllowUntil(expiresAt)`, `activeGrantExpiresAt`, `strictLocked`, `disableProtection(now)`, `cancelFocus(onCancelled)` مستخدمة بنفس الأسماء في كل المهام.
- **No commit steps:** بسبب تغييرات المستخدم غير المرتبطة.
- **Review Focus mapping:** 1 → Task 1 ticker + manual step 7؛ 2 → Task 2 tests + manual step 8؛ 3 → Task 2 `lastPackage` + manual step 8؛ 4 → Task 3 PIN guard + manual step 9؛ 5 → Task 3 `canCancelFocus` + callback + manual step 9.
