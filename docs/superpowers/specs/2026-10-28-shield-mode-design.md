# Shield Mode — Design

**Date:** 2026-10-28
**Status:** Revised — awaiting approval (hardening added)
**Scope:** One feature: make app blocking strong by default, with maximum-strength
hardening that works without root or Device Owner. No monetization, redesign,
localization, billing, or unrelated fixes.
**Release:** `0.3.3` (`versionCode` 6)

## 1. Intent

Today Warden only blocks apps the user explicitly adds to the blocklist. That puts the
burden on the user to find and add every distracting app, and new apps are unprotected
until the user remembers to add them.

Shield Mode reverses the default: while protection is active, **every foreground app is
blocked unless it is essential or the user explicitly allowed it**. The user no longer
has to enumerate what to block; they only choose rare exceptions.

The user asked for three things:

1. Strong blocking without adding every app manually.
2. Email always allowed.
3. A permanent way to allow specific apps as exceptions.

This design delivers those three things and nothing else.

## 2. Goals

- Default-deny app access while protection is active.
- Shield is on by default; the master switch remains off by default, so the user opts
  into protection once and gets comprehensive blocking immediately.
- Newly installed apps are blocked automatically.
- Essential phone functions always work: phone, messages, email, camera, clock/alarm,
  settings, contacts, keyboard, launcher, system UI, permission/install/file dialogs.
- The user can add and remove app exceptions at any time, with the existing PIN gate.
- Shield cannot be switched off casually during strict mode or a focus session.
- Existing blocklist, schedules, focus, strict mode, VPN, and website blocking keep working.
- Harden website filtering against the common DoH/DoT/hardcoded-resolver bypasses.
- Detect and surface the bypasses Android does not let a normal app prevent.
- No database migration and no loss of the user's current blocklist.

## 3. Non-goals

- Blocking notifications by default. Notification blocking stays opt-in and requires the
  existing notification-access grant.
- Blocking network traffic of allowed apps beyond the DNS/DoH hardening described here.
- Per-app temporary unlocks from the Shield intercept screen.
- Usage-stats fallback when Accessibility is disabled.
- Work-profile enforcement.
- Device Owner or root enforcement; this release targets a normal phone.
- Full SNI/raw-IP website blocking, which would require a full-tunnel TCP proxy.
- Absolute bypass prevention. Android lets a device owner or a determined user override a
  normal app; the UI must never imply otherwise.
- Replacing the existing blocklist UI or onboarding.
- Making Shield tamper-proof against someone who can change Android system settings.

## 4. Anti-goals

- Locking the user out of the phone, keyboard, dialer, emergency calling, permission
  prompts, file pickers, or app installation.
- Breaking email, alarms, or the camera.
- Blocking Warden itself or its intercept screen.
- Silently claiming protection when Accessibility or the VPN is down.
- Claiming Shield is impossible to bypass.
- Destroying or rewriting the user's existing blocklist data.

## 5. User-facing behavior

Terminology used below:

- **Shield enabled** — the persisted `shield_mode` setting.
- **Shield active** — Shield is enabled AND (the master switch is on OR a focus session is
  live). This is Shield's own gate and is deliberately stronger than the schedule-aware
  blocklist gate.
- **Existing blocking active** — `BlockingPolicy.isActive(...)` is true: master on with
  always-on or a live schedule, or a focus session forcing protection on. This gate
  continues to govern the existing blocklist, feature blocking, and website filter.
- **Website filter healthy** — `WardenVpnService.running` is true.

Why the split: if the user turns Shield on while "Always on" is off and no schedule is
running, Shield must still block apps. Otherwise the feature silently does nothing in a
configuration the user can easily reach. Existing blocklist and website rules keep their
current schedule semantics. Shield app blocking also does not depend on the VPN; a dead
VPN means websites may load, not that apps are unblocked.

### 5.1 Turning Shield on

- Shield is **on by default** for a fresh or upgraded install (a missing
  `shield_mode` key reads as `true`). The master switch is still off by default, so
  nothing is blocked until the user turns protection on. This matches the requirement:
  once protection is activated, blocking is comprehensive without adding apps.
- The Home screen gets a Shield card with a switch and a short explanation.
- Turning Shield on:
  1. Persists `shield_mode = true`.
  2. Sets `master_enabled = true` **first**, because app blocking is Accessibility-based
     and must not depend on VPN consent.
  3. Attempts to start the VPN best-effort for website filtering. If consent is denied or
     the tunnel fails, the master switch stays on: Shield still blocks apps, and the
     health UI says the **website filter** is paused, not that app blocking is off.
- This deliberately decouples "protection on" from "VPN healthy". The existing master
  switch still controls whether protection is active; it no longer requires a successful
  VPN start to block apps. The Quick Settings tile follows the same order.
- If Shield is turned on while a PIN exists, no PIN is needed: it only strengthens
  enforcement. Turning it off still uses the PIN/lock rules in §5.3.
- Enabling Shield never requires a PIN.
- Enabling Shield does **not** insert every installed app into the blocklist. It is a
  policy layer, not thousands of database rows.

### 5.2 While Shield is on

- Shield is active when it is enabled and the master switch is on or a focus session is
  live. It does **not** depend on the "Always on" setting or a schedule: turning Shield on
  is an explicit instruction to block apps, so it must not silently wait for a schedule.
- If Shield is active, opening any app that is not essential and not on the user's
  allowlist shows a full-screen Shield intercept. This works even if the VPN is down:
  app blocking is Accessibility-based.
- The intercept offers only:
  - **Open Warden** (to manage exceptions or turn Shield off, subject to PIN/locks).
  - **Hold to go home**.
- There is no "continue for 1 minute" and no temporary grant from Shield. Exceptions are
  deliberate and are managed inside Warden.
- Newly installed apps are blocked without being added anywhere.
- If the master switch is off and no focus session is running, Shield is dormant. The Home
  card says it is ready and explains that protection is off.
- Existing blocklist rules, feature blocking, and website blocking keep their current
  schedule-aware gate. Shield only takes precedence for apps it blocks.
- Protection honesty: if Accessibility, the VPN, Private DNS, or another VPN weakens
  enforcement, the Home card and the Protection health screen say so. Shield never claims
  live enforcement from the persisted flag alone.

### 5.3 Turning Shield off

- If a PIN exists, the user must pass the existing PIN dialog and brute-force lockout.
- Disabling Shield is refused while effective strict mode is on.
- Disabling Shield is refused while **any** focus session is active, strict or not.
  When Shield is on, the switch is disabled and labelled with the reason. The refusal is
  also enforced atomically in the settings store, so a focus session starting between the
  UI check and the write cannot be undercut. Turning Shield **on** is always allowed.
- Turning Shield off does not delete exceptions. Re-enabling restores them.
- Turning Shield off does not touch the blocklist, schedules, or master switch.

### 5.4 Allowed apps (exceptions)

- A new **Allowed apps** screen lists the user's exceptions.
- Adding an app uses a picker of launchable apps, excluding essentials and apps already
  allowed.
- The same screen has an **Advanced: add package name** field for non-launchable packages
  (for example an OEM in-call screen). The package is validated, trimmed, and rejected if
  it is essential or blank. This is the escape hatch for a missing OEM package; it is
  behind the same PIN/lock rules as the rest of the screen.
- Removing an app is immediate.
- Exceptions persist across restarts and Shield off/on.
- If a PIN exists, editing the allowlist requires unlocking the screen with the PIN once
  per visit (same pattern as Settings).
- During strict mode or any active focus session, the screen is **read-only**: the user
  can review exceptions but cannot add or remove them. A banner explains why.
- An exception only exempts an app from Shield. If the same app also has a blocklist
  rule, its normal rule still applies (pause, limits, or hard block). The list and picker
  show a small "Has its own block rule" note so this is not surprising.
- Essential apps are already always allowed and cannot be added as exceptions because
  they are filtered out of the picker.

### 5.5 Interaction matrix

| State | Shield off | Shield on |
|---|---|---|
| Master off, no focus | Existing rules only | Dormant ("Ready") |
| Master on | Existing rules only | Default-deny + existing rules |
| Master on, always-on off, no schedule | Existing rules usually inactive | Shield still blocks apps; existing rules/websites follow the schedule gate |
| Master on, VPN down | Existing app rules still apply; website filter paused | Shield blocks apps; website filter paused |
| Effective strict mode on | No Shield-specific lock | Disabling Shield refused |
| Any focus session active | No Shield-specific lock | Disabling Shield and editing exceptions refused |
| PIN set | Master off requires PIN (existing) | Shield off requires PIN; exception editing requires PIN |
| Accessibility off | App blocking cannot work | Shield cannot enforce; UI warns |

## 6. Enforcement semantics

### 6.1 Decision order

`AccessController.decideForPackage(packageName)` becomes:

1. If `packageName` is essential → `Allow` (defense in depth; the Accessibility service
   already skips these before calling the controller).
2. If Shield is active, the package is not allowlisted, and it is not essential →
   `ShieldBlock(packageName)`.
3. If existing blocking is not active now (`BlockEngine.isBlockingActiveNow()` is false)
   → `Allow`.
4. Otherwise, run the existing per-item rules exactly as today:
   `Allow`, `AllowUntil`, `Pause`, `HardBlock`, or `LimitReached`.

Consequences:

- Shield's gate is master-on-or-focus, not the schedule gate. Turning Shield on with
  "Always on" off still blocks apps.
- Shield is checked before the blocklist and before grants. A non-allowlisted app with a
  `PAUSE` rule or a live grant gets the stronger Shield wall.
- An allowlisted app falls through to its normal rule, and that rule still follows the
  schedule-aware gate. If it has no rule, it opens.
- An essential app never reaches Shield or the blocklist.
- Active access grants do not punch through Shield. Enabling Shield overrides old grants;
  turning Shield off lets those grants apply again if they have not expired.
- Website blocking and feature blocking are untouched.

### 6.2 What "every app" means

Shield applies to every package the Accessibility service sees in the foreground,
including apps that are not in the launcher picker, hidden apps, cloned apps, and newly
installed apps. The picker only lists launchable apps for convenience; enforcement is not
limited to that list.

### 6.3 Essential apps

Essential apps are always allowed and are never intercepted. The set is the union of:

1. **Warden itself** (`context.packageName`).
2. **All launchers** — every activity matching `ACTION_MAIN` + `CATEGORY_HOME`, not only
   the currently resolved default. With multiple launchers installed and no chosen
   default, resolving one package is not enough to prevent a lockout.
3. **All installed input methods** (`InputMethodManager.inputMethodList`), whether
   currently enabled or not, so switching keyboards can never lock the user out.
4. **Every handler of these intents**, queried through `PackageManager`:
   - Dialer: `ACTION_DIAL` with `tel:`.
   - Messages: `ACTION_SENDTO` with `smsto:`.
   - Email: `ACTION_SENDTO` and `ACTION_VIEW` with `mailto:` — this is how email is
     "added": every installed mail app that handles mail links is allowed.
   - Camera: `MediaStore.ACTION_IMAGE_CAPTURE`.
   - Clock/alarm: `AlarmClock.ACTION_SET_ALARM` and `AlarmClock.ACTION_SHOW_ALARMS`.
   - Browser packages are resolved separately (`ACTION_VIEW` + `https:`) and subtracted
     from **every** handler set above. A browser must not become essential just because it
     accepts `mailto:`, `tel:`, or another scheme.
5. **A static safety set** (with common OEM variants) covering:
   - Framework/system UI: `android`, `com.android.systemui`.
   - Settings and OEM security/permission centers: `com.android.settings`,
     `com.samsung.android.settings`, `com.miui.securitycenter`, `com.coloros.safecenter`,
     `com.oplus.safecenter`, `com.vivo.permissionmanager`, `com.huawei.systemmanager`,
     `com.hihonor.systemmanager`.
   - Telephony/emergency: `com.android.phone`, `com.android.server.telecom`,
     `com.android.incallui`, `com.samsung.android.incallui`, `com.android.emergency`,
     `com.google.android.emergency`, `com.android.cellbroadcastreceiver`,
     `com.google.android.cellbroadcastreceiver`, `com.android.stk`, `com.android.stk2`.
   - Permission/install/file/share UI: `com.android.permissioncontroller`,
     `com.google.android.permissioncontroller`, `com.android.packageinstaller`,
     `com.google.android.packageinstaller`, `com.android.documentsui`,
     `com.google.android.documentsui`, `com.android.providers.media.module`,
     `com.google.android.providers.media.module`, `com.android.keychain`,
     `com.android.certinstaller`, `com.android.vpndialogs`, `com.android.captiveportallogin`,
     `com.android.intentresolver`, `com.google.android.intentresolver`,
     `com.android.credentialmanager`.
   - Setup/provisioning: `com.android.setupwizard`, `com.google.android.setupwizard`,
     `com.android.managedprovisioning`.
   - Contacts: `com.android.contacts`, `com.google.android.contacts`,
     `com.samsung.android.contacts`.
   - Bluetooth/SIM/NFC: `com.android.bluetooth`, `com.google.android.bluetooth`,
     `com.android.nfc`, `com.google.android.nfc`.
   - Common dialer/messaging/camera/clock/email fallbacks:
     `com.android.dialer`, `com.google.android.dialer`, `com.android.messaging`,
     `com.google.android.apps.messaging`, `com.samsung.android.messaging`,
     `com.android.mms`, `com.android.camera2`, `com.google.android.GoogleCamera`,
     `com.samsung.android.camera`, `com.android.deskclock`,
     `com.google.android.deskclock`, `com.samsung.android.app.clockpackage`,
     `com.sec.android.app.clockpackage`, `com.miui.clock`, `com.android.alarmclock`,
     `com.google.android.gm`, `com.microsoft.office.outlook`,
     `com.samsung.android.email.provider`, `com.android.email`.
   - Google Play services/framework, which can show system UI and is not launchable:
     `com.google.android.gms`, `com.google.android.gsf`.
6. **Dynamic results are cached for 60 seconds** to keep the Accessibility hot path cheap.
   A launcher or IME change may take up to a minute to be reflected; the cache is cleared
   on app process restart.

Design notes:

- `com.android.vending` (Play Store) is deliberately **not** essential. It is a
  distraction surface and can be allowlisted if needed.
- Browsers are deliberately **not** essential. This is the point of Shield. The intent
  resolvers subtract browser packages from every handler set, so a browser that accepts
  `mailto:` or `tel:` does not become a hole. An email OAuth flow that opens a browser
  will be blocked; the user can allow the browser temporarily or finish setup before
  enabling Shield.
- The static list is a safety floor, not an exhaustive OEM database. The **Advanced: add
  package name** field in Allowed apps is the escape hatch for a missing non-launchable
  OEM package. It is better to let the user allow one package than to weaken default-deny
  for everyone.
- Essential apps are excluded from both app pickers (blocklist and Shield exceptions),
  matching the current treatment of critical apps. Existing blocklist rows for packages
  that become essential remain in the database but become inert; no destructive migration
  is performed.

### 6.4 Existing blocklist and data

- Shield adds no new Room entity and no schema version bump.
- The user's existing app and website entries are preserved byte-for-byte.
- Turning Shield off restores the previous behavior immediately.
- If the user had explicitly blocked an app that is now essential (for example Gmail), it
  will no longer be intercepted because essential apps are skipped before the controller.
  This is the intended meaning of "email always allowed".

## 7. UI design

### 7.1 Home Shield card

Place a card below the hero card and above the focus card.

Content:

- Title: **Shield**.
- Status (the switch reflects the setting; the copy never claims live enforcement from
  the flag alone):
  - Shield off: "Off — only the apps and sites you list are blocked."
  - Shield on, master off, no focus: "Ready — turn protection on to enforce Shield."
  - Shield on, focus active: "On — focus is enforcing Shield right now."
  - Shield on, master on: "On — every app is blocked unless it is essential or you
    allowed it."
- A note under the status when master is on: "Shield ignores the always-on/schedule
  setting; websites and your own blocklist still follow it."
- A line showing the number of exceptions and a link/button:
  **Allowed apps (N)**.
- A **Protection health** link/button. It opens the checklist described in §13.4.
- A small always-allowed note: "Phone, messages, email, camera, clock, settings and the
  keyboard always work."
- An error-coloured warning when Accessibility is off: "Accessibility is off — Shield
  cannot block apps until setup is complete."
- A separate warning when protection is active and the website filter is not running:
  "Website filter is not running — blocked sites may load. App blocking still works."
- A warning when Private DNS is not off: "Private DNS is on — encrypted DNS may bypass
  website filtering." A button opens the relevant system setting.
- A warning when another VPN is active: "Another VPN is active — website filtering is
  off. App blocking still works."
- These warnings are actionable but never pretend the app can override Android system
  settings. The Protection health screen lists every check and its fix.
- A switch:
  - Turning on: persist Shield, then call the existing `onToggleBlocking(true)` path.
    Turning on is always allowed, including during strict mode or focus; it only
    strengthens enforcement.
  - Turning off: if a PIN exists, show `VerifyPinDialog`; otherwise call the store.
  - While Shield is on and strict mode or any focus session is active, the switch's off
    action is disabled and a label explains: "Locked by strict mode" / "Locked during
    focus".
  - If the store refuses an off request (for example a focus session started between the
    UI check and the write), the switch stays on and a short message explains why.

The existing "Protection paused" card must be corrected to say **Website filter paused**:
app blocking is Accessibility-based and may still be working; only DNS filtering is down.

Hero card copy:

- If Shield is on, do not say "N apps under guard" based only on the blocklist. Replace it
  with "Shield is on — every app is blocked by default while protection is on; websites
  follow your blocklist." This avoids understating what is enforced.
- Otherwise keep the current copy.
- The master switch, always-on, and schedules keep their existing meaning for the
  blocklist and website filter. Shield deliberately uses master-or-focus as its own gate
  so it cannot silently wait for a schedule.
- This feature does not add a new live schedule evaluator to the Home screen.

### 7.2 Allowed apps screen

Route: `shield_apps`.

States:

0. **Settings not ready:** show a spinner, like Settings, so the screen never flashes an
   unlocked list before the PIN state is known.
1. **Locked by strict/focus:** read-only list + banner "Exceptions are locked during
   strict mode / focus. You can review them but not change them."
2. **PIN set and not unlocked:** centred "Allowed apps are locked" + "Unlock with PIN"
   button using `VerifyPinDialog`.
3. **Editable:** list + "Add app" button + an **Advanced: add package name** field.

List rows:

- App icon, label, package name.
- "Has its own block rule" note when the package is in the existing app blocklist.
- Remove icon button (disabled in the locked state).
- Empty state: "No exceptions yet. Every non-essential app is blocked."
- Footer note: "Essentials are always allowed: phone, messages, email, camera, clock,
  settings, keyboard, launcher."

"Add app" navigates to `shield_picker`. The advanced field accepts a package name such as
`com.samsung.android.incallui`, validates the format, rejects blank/essential names, and
adds it directly. It is collapsed by default so normal users never need it.

### 7.3 Shield app picker

Reuse `AppPickerScreen` with a mode parameter:

- `BLOCKLIST` keeps today's behavior exactly.
- `SHIELD_ALLOWLIST`:
  - Title: "Allow an app".
  - Subtitle: "Shield blocks every app by default. Apps you allow here get through Shield;
    their own block rules still apply."
  - List launchable apps excluding essentials.
  - Already-allowed apps show a check and are not clickable.
  - Blocklisted apps show "Has its own block rule".
  - Tapping an app adds it to the allowlist.
- Search stays the same.

### 7.4 Shield intercept screen

`InterceptActivity` gains `KIND_SHIELD` and `EXTRA_PACKAGE`.

- Resolve the app label with `AppsHelper.labelFor`, falling back to the package name, then
  to "This app".
- Title: "Shield is on".
- Body: "<App> is blocked. Warden's Shield blocks every app unless it is essential or you
  allowed it."
- Primary action: **Open Warden** — opens `MainActivity` and finishes the intercept.
- Secondary action: the existing hold-to-go-home control.
- Back goes home, as today.
- No continue, no grant, no timer.

`AccessController.ShieldBlock` carries the package name. The service passes it through
`EXTRA_PACKAGE`. Item lookup is skipped for Shield.

### 7.5 Quick Settings tile

No functional change. Existing behavior already applies:

- PIN set → turning protection off opens the app instead.
- Strict mode / strict focus → turning protection off is refused; the app opens.

Optionally the tile subtitle can say "Shield" when Shield is on; this is cosmetic and not
required for acceptance.

## 8. Data and components

### 8.1 SettingsStore

New DataStore keys:

- `SHIELD_MODE: Boolean = true` (default on; the master switch still defaults off)
- `SHIELD_ALLOWLIST: Set<String> = emptySet()`

Every read uses `p[SHIELD_MODE] ?: true` and `p[SHIELD_ALLOWLIST] ?: emptySet()`, including
`ProtectionSnapshot`, so the default is consistent across flows and enforcement.

`BLOCK_DOH` now defaults to `true` when the key is missing, so Shield's network hardening
is on by default. Any explicit `false` value is preserved, and the Settings switch still
lets the user turn it off. `ProtectionSnapshot.blockDoh` and `BlockEngine` must use the
same default.

New/changed API:

```kotlin
val shieldMode: Flow<Boolean>
val shieldAllowlist: Flow<Set<String>>

suspend fun setShieldMode(enabled: Boolean)          // enabling only
suspend fun disableShield(now: Long): Boolean        // atomic strict/focus guard
suspend fun addShieldException(packageName: String): Boolean
suspend fun removeShieldException(packageName: String)
suspend fun shieldConfig(): ShieldConfig             // one consistent read
```

`disableShield` performs the effective-strict and focus checks inside the same
`dataStore.edit` transaction as the write, returning false when refused. This mirrors
`disableProtection`.

`addShieldException`:

- Trims the package name.
- Rejects blanks, names longer than 255 characters, and strings that are not a valid
  package name (`android` or dot-separated identifiers; no spaces or slashes).
- Rejects essential packages as defense in depth.
- Writes a new set (never mutates the stored set in place).
- Returns whether it was added.

`ProtectionSnapshot` gains `shieldMode` and `shieldAllowlist` so the tile/health/UI can
read one consistent state. `BlockEngine` ignores the new fields.

`shieldConfig()` reads one preferences snapshot and computes `active = masterEnabled ||
StrictPolicy.isFocusActive(focusEndsAt, now)`. `now` is the max of the system clock and
the high-water mark from that same snapshot, so the read is consistent without a second
DataStore pass. This is the Shield gate used by `AccessController`, not the schedule-aware
`BlockEngine` gate.

### 8.2 ShieldPolicy

New pure file `app/src/main/java/com/warden/blocker/block/ShieldPolicy.kt`:

```kotlin
data class ShieldConfig(
    val enabled: Boolean = false,
    val allowlist: Set<String> = emptySet(),
    /** Master switch on OR a focus session is live — Shield's own gate. */
    val active: Boolean = false,
)

object ShieldPolicy {
    fun blocks(
        config: ShieldConfig,
        packageName: String,
        essentialPackages: Set<String>,
    ): Boolean = config.enabled &&
        config.active &&
        packageName !in essentialPackages &&
        packageName !in config.allowlist
}
```

This is the unit-tested core. No Android dependencies.

### 8.3 EssentialApps

New file `app/src/main/java/com/warden/blocker/util/EssentialApps.kt`:

```kotlin
object EssentialApps {
    fun packages(context: Context): Set<String>
    fun isEssential(context: Context, packageName: String): Boolean
}
```

Implementation:

- Static safety set + Warden + **all** HOME handlers + IMEs + intent handlers.
- Browser packages are resolved separately (`ACTION_VIEW` + `https:`) and subtracted from
  every handler set.
- 60-second in-memory cache keyed by `SystemClock.elapsedRealtime()`.
- `isEssential` uses the cached set.
- `packages()` never throws; every resolver call is wrapped in `runCatching`.
- Package visibility is declared in the manifest for every queried intent.

`CriticalApps.kt` remains as a deprecated delegate to `EssentialApps.isEssential` so no
existing reference or documentation breaks. `AppsHelper` and the Accessibility service
switch to `EssentialApps`.

### 8.4 AccessController

- Add `ShieldBlock(val packageName: String) : AccessDecision`.
- Add constructor parameters (defaults preserve existing tests/callers):

```kotlin
private val shieldConfig: suspend () -> ShieldConfig = { ShieldConfig() },
private val essentialPackages: suspend () -> Set<String> = { emptySet() },
```

- Implement the decision order from §6.1.
- `EssentialApps` caches its set, so the defense-in-depth essential check is cheap even
  when Shield is off.
- `ShieldBlock` has no `BlockedItem`; no item lookup is needed for it.

`WardenApp` wires:

```kotlin
shieldConfig = { settings.shieldConfig() },
essentialPackages = { EssentialApps.packages(app) },
```

### 8.5 Accessibility service

- Replace `CriticalApps.isCritical(this, pkg)` with
  `EssentialApps.isEssential(this, pkg)`.
- Handle `AccessDecision.ShieldBlock`:
  - Verify the app is still foreground (existing `isForeground` guard).
  - Launch `InterceptActivity` with `KIND_SHIELD`, item id `-1`, no reason/escalations,
    and `EXTRA_PACKAGE = decision.packageName`.
- Extend `launchIntercept` with an optional package name extra.
- Keep the existing window-generation and expiry-job cancellation logic. A Shield
  intercept does not schedule an expiry recheck.
- **Feature-scan collision:** the service also runs `maybeScanFeatures` for Reels/Shorts
  and can launch a `KIND_FEATURE` intercept for a package that Shield is about to block.
  Extend the existing 2-second cache (`cachedBlockingActive`, `cachedFeatureKeys`) with
  the cached `ShieldConfig` and essential set. Before feature detection, return early
  when `ShieldPolicy.blocks(...)` is true for the package. This keeps Shield as the only
  intercept for non-allowlisted apps, while feature blocking still works inside
  allowlisted apps.
- **Split-screen / picture-in-picture (best effort):** on `TYPE_WINDOW_STATE_CHANGED` and
  `TYPE_WINDOWS_CHANGED`, inspect `windows` for visible `TYPE_APPLICATION` windows whose
  package Shield blocks. If one is visible but not focused, show the Shield intercept.
  Only Shield-blocked packages are checked this way; normal blocklist rules stay
  foreground-only to avoid false positives in recents/split-screen. If Android does not
  expose the window, this is a documented residual limitation.
- **Accessibility watchdog:** override `onUnbind`/`onDestroy` to post a Shield health
  notification when the service goes away. This is the real-time signal; the periodic
  worker in §13 is the backstop.
- Warden's own package is essential, so the intercept activity cannot cause a loop.

### 8.6 InterceptActivity

- Read `EXTRA_PACKAGE`.
- Add `KIND_SHIELD` to the `when` before the item-dependent branches.
- Render `ShieldBlockScreen` with the app label, "Open Warden", and hold-to-home.
- Skip the `repository.item(itemId)` lookup when the kind is Shield.
- Because the activity is `singleTask`, implement `onNewIntent` to replace the current
  intercept spec. A later Shield intent must replace an earlier feature screen instead of
  being silently ignored.

### 8.7 WardenViewModel

New state and actions:

```kotlin
val shieldMode: StateFlow<Boolean>
val shieldAllowlist: StateFlow<Set<String>>
val shieldLocked: StateFlow<Boolean>   // strict OR any focus active

fun setShieldMode(enabled: Boolean)
fun disableShieldNow(): Boolean        // suspend; atomic store guard
fun addShieldException(packageName: String, onResult: (Boolean) -> Unit = {})
fun removeShieldException(packageName: String)
```

`addShieldException` exposes the store result so the advanced field can show an inline
error for invalid/duplicate/essential names. The picker ignores the callback because it
only offers eligible packages.

`shieldLocked` combines `strictMode`, `focusEndsAt`, and a ticking/peeked now. The Home
screen already computes `focusActive` from `focusEndsAt`, so it may compute the lock
locally instead of adding a flow; either way the store guard is authoritative.

### 8.8 Screens and navigation

- `Screens.kt`: add the Home Shield card, `ShieldAllowlistScreen`, and
  `ShieldHealthScreen`.
- `MainActivity.kt`: add routes `shield_apps`, `shield_picker`, and `shield_health`;
  pass the picker mode.
- `AppPickerScreen.kt`: add an enum mode; keep the blocklist default unchanged.
- `BlocklistScreen`: for an app item whose package is now essential, show "Always allowed
  by Shield" next to the row. Keep the row so the user can remove the stale entry; its
  switch is inert. This prevents a Gmail entry from looking active when email is always
  allowed.

### 8.9 Manifest

Add `<queries>` entries for package visibility on Android 11+:

- `ACTION_DIAL` + `tel:`
- `ACTION_SENDTO` + `smsto:`
- `ACTION_SENDTO` + `mailto:`
- `ACTION_VIEW` + `mailto:`
- `ACTION_VIEW` + `https:` (browser detection; subtracted from all handler sets)
- `android.media.action.IMAGE_CAPTURE`
- `android.intent.action.SET_ALARM`
- `android.intent.action.SHOW_ALARMS`
- `android.net.VpnService` (detect apps that can create a VPN and warn before allowing
  them)

`accessibility_service_config.xml` gains `typeWindowsChanged` so split-screen/PiP window
changes are delivered. `flagRetrieveInteractiveWindows` is already set.

`BootReceiver` also listens for `android.intent.action.MY_PACKAGE_REPLACED` so an app
update re-checks posture and re-arms the VPN.

No new permissions.

### 8.10 Version and docs

- `app/build.gradle.kts`: `versionCode = 6`, `versionName = "0.3.3"`.
- `DEVICE_TESTING.md`: add a Shield section covering essentials, exceptions, PIN,
  strict/focus locks, new apps, the Accessibility-off warning, Private DNS, another VPN,
  DoH IP blocking, split-screen/PiP, notification blocking, and device-admin
  deactivation.

## 9. Edge cases

1. **No handler for an intent:** the static fallback covers common apps; if a device has
   no email app, there is nothing to allow and nothing breaks.
2. **Package visibility:** all queried intents are declared in `<queries>`; resolver
   failures return an empty set rather than crashing.
3. **Multiple launchers or no chosen default:** all HOME handlers are essential, so the
   Home button cannot land on a Shield wall.
4. **Launcher or IME changes:** up to 60 seconds of stale cache. The old launcher may
   remain allowed briefly; the new one is resolved on the next cache refresh.
5. **A browser registers `mailto:` or another scheme:** browsers are subtracted from every
   handler set, so they do not become essential. Email apps that are not browsers still
   are.
6. **A browser is also the user's webmail:** the user can allow it explicitly; it is not
   allowed automatically.
7. **Email OAuth / links:** they open a browser, which Shield blocks. The user can allow
   the browser or finish account setup before enabling Shield. This is documented, not
   silently special-cased.
8. **Play Store and system updates:** Play Store is blocked; Settings and GMS are allowed.
   The user can allow Play Store as an exception.
9. **Split screen / picture-in-picture:** Accessibility window events are the enforcement
   point. A shielded app that is visible but never gains window focus may not be
   intercepted. This is an existing Accessibility limitation and is not solved here.
10. **Accessibility turned off or Warden force-stopped:** Shield cannot enforce. The Home
    setup card and the Shield card warn. Android system Settings is always allowed, which
    is also an emergency exit.
11. **Missing non-launchable OEM package:** the user can add its package name through the
    advanced field in Allowed apps. This is the escape hatch; the static list cannot cover
    every OEM.
12. **Work profile:** apps in a separate profile may not be visible to the personal-profile
    Accessibility service. Out of scope.
13. **Uninstalled allowlisted app:** the package remains in the set and is shown as
    "Not installed"; it can be removed. It does not break anything.
14. **Existing blocklist rows for newly essential apps:** inert, preserved. No migration.
15. **Focus starts while a PIN dialog is open:** the atomic store guard refuses the write.
16. **Shield on, master off and no focus, then master on:** Shield applies immediately
    because the setting persisted.
17. **Shield off, master on:** prior behavior returns immediately.
18. **Master on, always-on off, no schedule:** Shield still blocks apps; existing
    blocklist/website rules remain schedule-gated.
19. **Strict flag without a PIN:** `StrictPolicy` treats it as ineffective, so it does not
    lock Shield either.
20. **VPN consent denied:** the master stays on; app blocking works, the website filter is
    reported paused, and the UI never claims website filtering is healthy.
21. **VPN tunnel dies while master is on:** app blocking continues; the health card says
    the website filter is down and offers a restart.
22. **Feature scan and Shield race:** the Accessibility service skips feature detection for
    packages that Shield will block, so a Reels/Shorts screen cannot replace the Shield
    wall for a non-allowlisted app.
23. **Upgrade while protection is already on:** Shield defaults to on, so the first app
    launch after the update is blocked unless essential or allowlisted. This is the
    requested behavior; the Shield card and Allowed apps screen are the way out.
24. **A new essential app is installed inside the cache window:** it may be treated as
    non-essential for up to 60 seconds and blocked. Reopening after the cache refresh
    fixes it; the advanced field is the immediate escape. This is rare and preferable to
    running several PackageManager queries on every window event.

## 10. Security and limitations

- Shield is an Accessibility-based app blocker, not an OS firewall. It cannot stop a
  determined user from disabling Accessibility, force-stopping Warden, or uninstalling it
  (unless device admin is active).
- The PIN gates Warden's own controls, not Android system settings.
- Shield does not block background sync or network access of allowed apps. Notifications
  are canceled only when the existing opt-in is enabled and notification access is
  granted.
- Shield does not replace the DNS/VPN website filter.
- The UI must say "blocks every app by default", never "impossible to bypass".
- Hardening closes the common DoH/DoT/hardcoded-resolver paths, but it cannot block a
  site opened by raw IP, a shared-CDN DoH endpoint, a proxy/Tor path, a resolver IP that
  is not in the curated list, or a second VPN the user starts after Warden's is revoked.
  These are stated in Protection health, not hidden.
- Notification blocking is opt-in and requires notification access; it is not a security
  boundary and can hide messages the user might want.
- Essential apps are trusted by package name. A malicious app that spoofs a package name
  cannot install over a system package without the platform rejecting it; the risk is OEM
  packages missing from the static list, which makes the feature more permissive, not
  less safe for the user's data.
- Allowlist package names are not sensitive; they remain in the existing unencrypted
  DataStore, like the rest of Warden's settings.

## 11. Acceptance criteria

1. With Shield on and the master switch on (or a focus session live), opening a
   non-essential, non-allowlisted app shows the Shield intercept on the next Accessibility
   window event.
2. A fresh install has Shield on and the master switch off; turning protection on blocks
   apps immediately without visiting a setup list.
3. A newly installed app is blocked without being added to any list.
4. Phone, messages, every non-browser `mailto:` email handler, camera, clock/alarm,
   settings, contacts, keyboard, every installed launcher, System UI, permission dialogs,
   package installer, share sheet, and file picker are not intercepted.
5. The user can add an exception (from the picker or the advanced package field), see it
   in Allowed apps, open the app, remove the exception, and see it blocked again.
6. An allowlisted app with an existing blocklist rule still obeys that rule.
7. Disabling Shield requires the PIN when one is set, and is refused during effective
   strict mode or any focus session.
8. Editing exceptions requires the PIN when one is set, and is read-only during strict
   mode or any focus session.
9. Turning Shield on sets the master switch and starts app blocking even if VPN consent is
   denied; the website filter is clearly labelled paused. A tunnel failure does not stop
   app blocking.
10. With Shield on and the master on, turning "Always on" off and having no active schedule
    still leaves Shield blocking apps.
11. Turning Shield off restores the previous blocklist behavior with no data loss.
12. No Room schema change and no destructive migration.
13. A Shield-blocked app that also has feature blocking shows the Shield intercept, not
    the feature intercept, even if a feature intercept was already open.
14. With `blockDoh` on and the VPN running, a DoH client using a listed hardcoded
    resolver IP cannot reach it, while ordinary browsing still works.
15. Private DNS that is not off produces a Protection health warning; app blocking still
    works and the UI does not claim website filtering is healthy.
16. Another VPN taking over produces a notification and a health warning; app blocking
    continues, and Warden attempts one restart.
17. Accessibility turned off produces a health notification and a Home warning; the UI
    never claims apps are being blocked.
18. A Shield-blocked app visible in split-screen or picture-in-picture is intercepted on
    a best-effort basis; when Android does not expose the window, that limitation is
    documented.
19. With notification blocking enabled and access granted, notifications from
    Shield-blocked apps are canceled.
20. While Shield is on and device admin is active, Warden refuses to disable uninstall
    protection; deactivation from Android Settings is detected and warned about.
21. The UI never claims absolute or unbypassable protection.
22. All unit tests pass and `assembleRelease` produces a signed `0.3.3` (6) APK.
23. No commits are made; unrelated working-tree changes are untouched.

## 12. Verification

- New JVM tests for `ShieldPolicy`: disabled, inactive, unlisted, allowlisted, essential,
  allowlist+essential, and the Shield-before-blocklist ordering contract at the policy
  level.
- New JVM tests for `ShieldHealthPolicy`: healthy, Accessibility off, VPN down, Private
  DNS on, another VPN, admin off, notification access missing, and combinations.
- New JVM test that every `DohIps` literal parses as a numeric IPv4/IPv6 address and that
  the list has no duplicates.
- Full `testDebugUnitTest` (46 existing + new).
- The `EssentialApps` resolver and `DevicePosture` reads are Android-dependent and are
  verified by build + the manual checklist; no Robolectric dependency is added.
- `assembleRelease` and `apksigner verify`.
- Copy the APK to the desktop.
- Manual hardware checklist added to `DEVICE_TESTING.md`; no device is connected, so
  Accessibility, VPN route changes, Private DNS, another VPN, split-screen/PiP,
  notification blocking, tile, and lock-screen behavior are reported as untested on
  hardware.

## 13. Hardening — maximum strength without root

This section was added after the first review at the user's request: blocking must be as
strong as a normal Android app can make it, including the common ways people bypass
website and app blockers.

### 13.1 Threat model and honest limits

Target: a user who wants to stop their own impulsive bypass attempts, not a determined
attacker with physical access and advanced tools.

On a non-rooted, non-Device-Owner phone, Android lets the user:

- disable Accessibility;
- start another VPN, which revokes Warden's tunnel;
- change Private DNS;
- force-stop Warden or clear its data;
- deactivate device admin;
- boot into safe mode;
- factory reset.

Warden can detect some of these and warn, but cannot prevent them. The UI must never
claim "impossible to bypass". Device Owner and root support are out of scope for this
release.

Residual bypasses after this hardening:

- a site opened by raw IP address (no DNS lookup);
- DoH/DoT to a resolver IP not in the curated list, or a shared-CDN endpoint;
- a proxy, Tor, or second VPN the user starts after Warden's tunnel is revoked;
- IPv6 traffic to a resolver not in the curated list;
- cached DNS/AAAA records until TTL;
- apps in another user profile or work profile;
- a screen reader or accessibility trick that disables the service.

### 13.2 Network hardening

1. **`DohIps`** — a new pure object with curated IPv4 and IPv6 addresses of well-known
   public DNS, DoH, and DoT resolvers. The list is a release-time constant, not a network
   lookup at runtime, so it works offline and cannot be poisoned.
2. **Routes when `blockDoh` is on:**
   - Add a /32 route for each IPv4 address and a /128 route for each IPv6 address.
   - The existing packet loop filters IPv4 UDP 53 to those addresses exactly as it
     filters the virtual resolver: blocked names are sinkholed, allowed names are
     forwarded to the device's real resolver. This closes the "hardcoded 8.8.8.8" path.
   - IPv4 TCP, UDP 443/853, and all IPv6 packets to those routes are read and dropped
     because the loop only forwards IPv4 UDP 53. This closes DoH/DoT over a listed
     resolver.
   - Add a ULA IPv6 address to the tunnel for the IPv6 routes. If `establish()` fails,
     retry once without IPv6 routes and continue IPv4-only; report the degraded mode in
     Protection health.
3. **Rebuild on policy change:** the VPN watcher tracks the last `blockDoh` value. When it
   changes, rebuild the tunnel once so the route set matches. Do not rebuild on every
   blocklist edit. The watcher itself must be started once and survive rebuilds. Update
   the Settings copy: the switch now reconnects the tunnel briefly instead of saying
   "applies next time blocking starts".
4. **TCP DNS is fail-closed:** the virtual resolver still answers only UDP. TCP DNS to it
   times out. This is deliberate: a TCP query cannot sneak a blocked name past the
   filter. The rare cost is that a truncated UDP response that falls back to TCP may
   fail; document it.
5. **Private DNS posture:** on API 28+, read the `private_dns_mode` global setting
   through `Settings.Global.getString(contentResolver, "private_dns_mode")` (`off`,
   `opportunistic`, `hostname`) and the optional `private_dns_specified_hostname`. Use
   string keys rather than hidden constants. If it is not `off`, show "Private DNS is on —
   encrypted DNS may bypass website filtering" and an action that opens the Private DNS
   setting (with a Wireless-settings fallback). Never turn it off automatically; that
   needs Device Owner/ADB.
6. **Other VPN / revoke:** override `WardenVpnService.onRevoke()` to set `running = false`,
   post a high-priority "Website filter stopped" notification with an action that opens
   Warden, and attempt one restart when Shield is active and consent already exists. If
   another VPN holds the slot, the health screen reports degraded instead of fighting.
7. **Always-on VPN guidance:** best-effort read of the `always_on_vpn_app` secure setting
   through `Settings.Secure.getString(contentResolver, "always_on_vpn_app")`.
   If Warden is not the always-on VPN, mark it as a checklist item with a button to
   `Settings.ACTION_VPN_SETTINGS` and instructions to enable "Always-on VPN" and
   "Block connections without VPN". If the value is unreadable, show "Unknown" rather
   than a false claim. This release cannot set it automatically. Label it "Android
   always-on VPN (system)" in the UI so it is not confused with Warden's "Always on"
   blocking mode.
8. **No kill switch without always-on:** if the tunnel drops, app blocking continues
   because it is Accessibility-based; the UI says website filtering is paused. A real
   always-on kill switch requires the user to enable Android's always-on VPN (or Device
   Owner in a future release).
9. **Default change:** `BLOCK_DOH` defaults to true when unset, so the above is on by
   default. An explicit `false` is preserved, and the Settings switch remains.

### 13.3 App hardening

1. **Uninstall protection while Shield is on:** the Settings device-admin card becomes a
   Shield health item. While Shield is on and admin is active, the in-app "turn off"
   action is refused (turn Shield off first). The user can still deactivate admin from
   Android Settings; Protection health detects that and warns.
2. **Accessibility watchdog:** `onUnbind`/`onDestroy` posts a health notification. A
   `ShieldHealthWorker` (WorkManager, 15-minute periodic, unique work) is the backstop,
   and `BootReceiver`/`MY_PACKAGE_REPLACED` re-check and restart the VPN after a reboot
   or app update. One notification is maintained, not one per check.
3. **Split-screen / picture-in-picture:** inspect `AccessibilityService.windows` for
   visible `TYPE_APPLICATION` windows of Shield-blocked packages on window events. If a
   blocked app is visible but not focused, show the Shield intercept. Only Shield-blocked
   packages are checked this way; normal blocklist rules stay foreground-only to avoid
   false positives in recents. `TYPE_WINDOWS_CHANGED` may arrive with a null package, so
   handle it before the current package-name early return. If Android does not expose the
   window, document the gap.
4. **Notification blocking:** when the existing `blockNotifications` setting is on and
   notification access is granted, cancel notifications from Shield-blocked apps too,
   not only blocklist apps. Use `settings.shieldConfig()` + `ShieldPolicy` for Shield and
   keep the existing `BlockEngine` check for blocklist rules: Shield's gate is
   master-or-focus, not the schedule-aware blocklist gate. Cache `ShieldConfig` and the
   essential set for two seconds so a busy notification stream does not hammer DataStore.
   This is opt-in and may hide messages; the Settings copy must say so.
5. **Bypass-app warnings:** in the allowed-app picker and the advanced package field,
   warn when the candidate declares a `VpnService` or resolves `ACTION_VIEW` for
   `https:` (a browser). The warning says the app can weaken website filtering. It does
   not block the choice; Shield already blocks these apps by default.
6. **No forced allowlist of VPN/DNS apps:** the warning is enough. A user who needs a
   work VPN can allow it knowingly.

### 13.4 Protection health UI

Add a `shield_health` route and `ShieldHealthScreen`, linked from the Home Shield card.

`DevicePosture` reads, without storing anything:

- Accessibility enabled;
- `WardenVpnService.running`;
- Private DNS mode;
- whether a VPN transport is active while Warden is not running;
- device-admin active;
- notification access;
- best-effort Android always-on VPN owner.

A pure `ShieldHealthPolicy` maps `ShieldConfig` + `DevicePosture` + `blockDoh` to an
ordered list of warnings. The screen shows one row per check with a status and an action:

- Accessibility → open Accessibility settings.
- Website filter → open Warden or retry.
- Private DNS → open Private DNS/Wireless settings.
- Android always-on VPN (system) → open Android VPN settings.
- Uninstall protection → device-admin prompt.
- Notification blocking → notification-listener settings.
- Another VPN → explain that website filtering is off and app blocking continues.

The Home card shows only critical warnings (Accessibility off, VPN down, Private DNS,
other VPN) and the health link. Healthy copy never claims more than "Apps are blocked by
default; websites follow your blocklist and the current website-filter health."

### 13.5 Components

- `block/ShieldHealthPolicy.kt` — pure warning mapping; unit-tested.
- `vpn/DohIps.kt` — curated IPv4/IPv6 literals; unit-tested for parseability.
- `vpn/WardenVpnService.kt` — routes, rebuild, `onRevoke`, no duplicate watchers.
- `accessibility/AppBlockAccessibilityService.kt` — windows inspection, unbind health.
- `system/WardenNotificationListener.kt` — Shield-blocked notifications.
- `system/ShieldHealthWorker.kt` — periodic backstop and single health notification.
- `system/BootReceiver.kt` — boot/package-replaced re-arm and health check.
- `util/DevicePosture.kt` — Android-dependent posture reads.
- `ui/Screens.kt`, `MainActivity.kt`, `WardenViewModel.kt` — health route and card.
- `AndroidManifest.xml` / `accessibility_service_config.xml` — VPN query and
  `typeWindowsChanged`.
- `SettingsStore` — `BLOCK_DOH` default and the Shield keys.

### 13.6 Edge cases

1. **Curated IP list goes stale:** a new DoH endpoint or changed anycast address can
   bypass until the next Warden release. This is a residual bypass, not a silent claim.
2. **Blocking public resolver IPs affects non-DNS traffic to those IPs:** for example a
   service hosted on 1.1.1.1. The user can turn off "Block DNS-over-HTTPS"; the tradeoff is
   documented in the setting.
3. **Tunnel rebuild failure:** keep the last working configuration if possible; if the
   new tunnel cannot establish, report "Website filter paused" and keep app blocking.
4. **IPv6 route failure:** retry once without IPv6 routes; report IPv4-only mode. Do not
   add a default IPv6 route, because that would break IPv6-only networks.
5. **Health worker latency:** WorkManager's minimum is 15 minutes. Real-time signals are
   `onUnbind` and `onRevoke`; the worker is a backstop.
6. **Split-screen false positives:** only Shield-blocked packages, only window events,
   and always exclude essentials/IMEs/launchers. If the window list is unavailable, do
   nothing rather than intercept the wrong app.
7. **Notification cancellation hides important messages:** opt-in, clearly labelled, and
   off by default.
8. **Admin deactivation from Android Settings:** detected on next posture read; cannot
   be prevented without Device Owner.
9. **Accessibility off:** no app blocking is possible. The health notification is the
   only signal; the Home card must say "not enforcing".
10. **Force-stop, clear data, safe mode, factory reset:** unpreventable by a normal app.
11. **Work profile / cloned apps:** out of scope; accessibility in the personal profile
    may not see them.
12. **Raw-IP or SNI-only browsing:** a site opened by IP, or a DoH endpoint on a shared
    CDN IP, is not blocked. Full SNI filtering would need a full-tunnel TCP proxy and is
    out of scope.

### 13.7 Acceptance criteria

Covered by §11 items 14–21. The UI must always distinguish "Shield is configured" from
"Shield is enforcing now".

### 13.8 Verification

Covered by §12. In addition, the manual checklist in `DEVICE_TESTING.md` must include:

- DoH by hardcoded IP (if a test client is available) and normal browsing afterward;
- Private DNS on/off;
- starting a second VPN and returning to Warden;
- disabling Accessibility and seeing the health notification;
- split-screen and PiP with a blocked app;
- notification blocking with and without the access grant;
- deactivating device admin from Android Settings;
- toggling `blockDoh` and confirming the tunnel rebuilds without losing app blocking.

No device is connected, so these are reported as untested on hardware.

## 14. Out of scope

- Notification blocking by default (it remains opt-in and needs notification access).
- Usage-stats fallback.
- Per-app temporary access from Shield.
- Work profile / cloned-app enforcement.
- Device Owner or root enforcement; a future release can add a Device Owner path.
- Full SNI/raw-IP website blocking, which would need a full-tunnel TCP proxy.
- Absolute bypass prevention; see §13.1 for the residual bypasses.
- Redesign, localization, ads, billing, onboarding changes.
- Fixing unrelated bugs.
- Any `git commit`.
