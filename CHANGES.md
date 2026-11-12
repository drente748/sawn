# CHANGES — `feat/optional-vpn`

Branch: `feat/optional-vpn` (base: `sawn-rebuild` / `origin/main` @ `c14bc46`)
Status: **preparation branch — no APK/AAB was produced, signed, or published.**

This branch makes the DNS/VPN filter **optional**. Sawn gains a second protection mode
that needs no connection permission at all, and every layer of the app now asks one
single question — *"what is protecting me right now?"* — through one coordinator instead
of each engine deciding on its own.

---

## 1. What changed

### 1.1 A single coordinator (`:core:domain`, pure Kotlin)

New `BlockCoordinator` interface and its production implementation
`DefaultBlockCoordinator`:

| Member | Meaning |
|---|---|
| `getActiveEngines(): Set<BlockingEngineType>` | `DNS`, `BROWSER`, `APP` — the engines live right now |
| `getProtectionLevel(): ProtectionLevel` | `Full` / `BrowserOnly` / `None` / `Paused` |
| `evaluateDomain(host): BlockDecision` | The decision for one host, and which engine owns it |
| `reportBlock(decision, source)` | The counter for an enforced block |

The rules live in a separate pure object, `ProtectionPolicy`, so the mapping from engine
state to the badge is unit-tested on its own and cannot drift from the coordinator.

`BlockingEngineType`, `ProtectionLevel`, `BlockSource`, `BlockDecision`,
`ProtectionMode` are all in `:core:domain` — no Android types.

The Android side is `SawnBlockCoordinatorEnv` (`:app`): it supplies the facts (is the
tunnel up? is accessibility enabled? what is on the blocklist?) and nothing else.

### 1.2 The two modes

`ProtectionMode` is `FULL` (DNS + accessibility) or `LIGHT` (accessibility only).

`SettingsStore` gained two switches, exposed as `vpnEnabled` and
`browserBlockingEnabled`, with `protectionMode` derived from them. The light mode never
starts the VPN: `VpnController.start()` refuses **before** asking the system for
anything, so the connection permission is never requested in that mode.

`SettingsStore.initializeEngineMode()` runs once on the first launch of this version and
decides the initial mode:

- a **fresh install** starts in the **light** mode — no VPN permission is demanded
  unasked;
- an **upgraded install keeps the filter it already had** — the `onboarding_done` flag is
  the witness that the app already ran, so the DNS engine stays on. Silently dropping a
  running protection on upgrade would be the worst possible surprise.

A live time lock overrides both: it demands the full stack back.

### 1.3 The browser engine (`:app`)

`BrowserBlockingEngine` reads the address bar of a known browser and blocks what the
blocklist forbids. It is the engine that makes the light mode useful.

The tree walk (`BrowserHostExtractor`, in `:core:domain` so it is unit-tested without a
device):

- **at most 80 nodes** — a heavy page can never freeze the accessibility service;
- **never enters a WebView** — the engine reads the browser's own chrome, never page
  content, so it cannot read what the user sees on a page;
- nodes are matched by the **last segment of the view id** (`com.android.chrome:id/url_bar`
  → `url_bar`), so one list of ids covers every browser that uses the same widget, and an
  OEM rebuild that renames the package still matches.

`BrowserRegistry` names the browsers Sawn advertises and tests against — Chrome, Firefox,
Samsung Internet, Edge, Brave, DuckDuckGo, Opera — and keeps the whole existing table
(116 browsers) reachable.

`HostNormalizer` turns whatever the bar shows into a bare host: a full URL, a breadcrumb
(`example.com › page`), a bare title in mixed case. A search query is not a host and is
rejected.

**Typing vs. visiting.** A *focused* bar means the user is still typing: the reading is
deferred 300 ms and then re-read, so a half-typed domain is never blocked. An *unfocused*
bar means the browser already navigated there: the block happens at once.

### 1.4 The block notice

`BlockOverlayController` draws the notice with a
`WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` window — **not**
`SYSTEM_ALERT_WINDOW`. An overlay drawn by the accessibility service belongs to it, so
the user is never asked for the "draw over other apps" permission. The window is
transparent, non-focusable and non-touchable: it must not steal the keyboard and must not
trap the user.

A notice alone would leave the blocked page on screen behind it, so the block also sends
the browser **back** — the block has to *leave* the page, not merely cover it. The notice
removes itself after four seconds: it is a message, not a wall.

The adult-content list is left to the existing monitor, which already shows a full
intercept screen for it; two screens for one page would be a mess.

### 1.5 The badge

`ProtectionBadge` on the home screen says what is actually running: full protection,
browser-only, off, or paused. It is read from the live runtime (the tunnel's state and the
accessibility service's state), not from the settings, so it never promises more than is
running.

### 1.6 The streak follows the engines

`protected_days` gained three flags — `hasDnsProtection`, `hasBrowserProtection`,
`hasAppProtection` — and a day counts as protected when **any** engine was enforcing it.
The flags are merged, so a day protected first by the DNS filter and later by the browser
engine keeps both. `MIGRATION_4_5` adds the columns and re-labels the days recorded
before it as DNS-protected, because the DNS filter was the only engine back then — the
streak of an existing user is not reset by the upgrade.

### 1.7 Boot restore respects the mode

`BootRestorePolicy` now takes `vpnEnabled`. With the light mode there is no VPN to
restart, so the boot does not start one — asking for the connection permission after a
reboot would be the app demanding a permission the user deliberately refused. A live time
lock still forces the full stack back.

`VpnController` holds the gate: `start()` refuses before anything is asked of the system,
so the connection permission is never requested in the light mode. `SawnVpnService` checks
it too (defense in depth, against a stale intent or a race with a mode switch), and the
Quick Settings tile, the setup screen, and the focus-session flow all go through the same
gate.

### 1.8 The settings screen offers two named options

Not a switch: a switch hides what the other side of it means. The two options state the
promise each one makes, and the description of the light one says plainly what it does
**not** cover.

The onboarding asks the same question before the permissions, so the later permission
prompt is never a surprise.

### 1.9 The health check follows the mode

`GuardHealthWorker` only watches the DNS filter when it is *expected* — in the light mode
its absence is not a failure and must not raise a notification the user cannot act on.
It runs at the 15-minute floor WorkManager allows, and a **strict-mode** check every
3 minutes on top (a self-rescheduling one-time request, because WorkManager refuses a
periodic interval below 15 minutes).

---

## 2. Decisions

| Decision | Why |
|---|---|
| `BlockCoordinator` in `:core:domain`, its Android side in `:app` | The rules must be testable without a phone. The container only supplies facts. |
| `ProtectionPolicy` split out of the coordinator | The engine→badge mapping is the rule most likely to be edited later; keeping it separate keeps it honest. |
| The light mode uses `TYPE_ACCESSIBILITY_OVERLAY`, not `SYSTEM_ALERT_WINDOW` | "Draw over other apps" is one of the scariest permissions an app can ask for, and a real barrier to distribution. An accessibility overlay needs no extra permission. |
| The tree walk is capped at 80 nodes and skips WebViews | A page can hold thousands of nodes; the service must never stall. Skipping WebViews is also the *honest* limit of the light mode: it cannot see inside a page. |
| The bar is read by the last segment of the view id | One id list then covers every browser using that widget, and survives a package rename by an OEM. |
| A focused bar waits 300 ms; an unfocused bar blocks at once | A half-typed domain must never be blocked, but a page that has already loaded must be stopped without giving the user a window to click past. |
| A fresh install starts light, an upgraded install keeps its DNS filter | The whole point is to stop demanding the scary permission unasked — but never to take away a protection a user already has. |
| The light mode is a *named* mode, not a toggle | It is a promise about coverage, not a preference. A switch would hide the difference; two named options state it. |
| The streak counts a day when *any* engine was active | The streak should follow the protection that actually happened, not the engine it happened to use. |
| `BiometricHelper` deleted | Dead code: nothing referenced it, and it was never wired to a screen. |

---

## 3. Testing

| Suite | What it covers |
|---|---|
| `BlockCoordinatorTest` (Kotest) | The coordinator's decisions and the four protection levels |
| `HostNormalizerTest` (Kotest) | Full URL, breadcrumb, plain text, upper case, search query |
| `BrowserHostExtractorTest` (Kotest) | The bounded walk, the WebView skip, the id matching, the typing signal |
| `LockdownPolicyPropertyTest` (Kotest) | The lock can only be extended, never shortened |
| `ScheduleEvaluatorMidnightTest` (Kotest) | Windows that wrap past midnight |
| `DomainMatcherBoundaryTest` (Kotest) | The subdomain boundary |
| `BootRestorePolicyTest` | The mode-aware boot restore |
| `SettingsStoreEngineModeTest` | The engine switches, the mode, and the fresh-vs-upgraded rule |
| `SawnDatabaseMigrationSqlTest` (JVM) | The 4→5 migration, driven over real SQLite |
| `SawnDatabaseMigrationTest` (instrumented) | The same migration, validated against the **real** exported Room schema |

`Kotest` was added to `:core:domain` (JUnit Platform + the vintage engine, so the
existing JUnit 4 suites keep running alongside it).

The migration is checked twice, on purpose:

- `SawnDatabaseMigrationSqlTest` (JVM) drives the **same** `MIGRATION_4_5` object over a
  real SQLite database that starts at the previous schema. It runs on every
  `./gradlew test`, so a broken migration is caught before it reaches a phone.
- `SawnDatabaseMigrationTest` (instrumented) is the official check: it uses Room's
  `MigrationTestHelper` to validate the migrated schema against the **real** exported
  schema JSON — the same file the shipped build uses, not a hand-written copy that could
  drift away from it. It needs a device, so it is the developer's run
  (`./gradlew connectedAndroidTest`).

Room's `MigrationTestHelper` reads the exported schemas from the assets, so the build
copies `core/data/schemas` into the test assets of both source sets.

---

## 4. Honest limits

The light mode is **not** the full mode, and the app says so everywhere it matters
(`README.md`, `SECURITY.md`, `privacy.md`, the settings screen, the onboarding, and the
home-screen badge):

- A page opened **inside another app** (a WebView) is **not** blocked.
- **SafeSearch** and **DoH blocking** do not work — they are DNS features.
- A browser that does not expose its address bar is not covered until it is added.
- The browser engine is a race the user can lose: it reacts to an event and reads the bar
  a moment later. The DNS filter acts before any page is drawn, and has no such gap.

---

## 5. Verification

Run on this branch (Gradle 8.13, JDK 21, Windows):

| Command | Result |
|---|---|
| `./gradlew test` | **BUILD SUCCESSFUL** — 291 tests in `:core:domain`, 17 in `:core:data`, 34 in `:app`; 0 failures |
| `./gradlew :app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew :app:compileReleaseKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew :app:lintDebug` | The **same** findings as before this branch — no new one from the files added or changed here |

Not run, and why: `assembleRelease` / `bundleRelease` (forbidden by the task — no APK or AAB
was produced, and no signing happened), `connectedAndroidTest` (needs a device; the
developer's job).

---

## 6. Not done here (deliberately)

- **No APK, AAB, signing, or publishing.** The task forbids it, and none was produced.
- **No `versionCode` / `versionName` change.** The Room schema moved to version 5; the
  app version stays at 2.2.0 / 31 as required.
- **No push, no PR, no tag.** The branch is local, for human review.
- `connectedAndroidTest` was not run (it needs a device); the migration test therefore
  also runs as a JVM/Robolectric test in `:core:data`, which `./gradlew test` does execute.
- **Lint**: the pre-existing error (`ImageScreeningOrchestrator.kt`, `capture()` needs
  API 30) is **not** fixed here — it is outside this branch's scope, and fixing it would
  touch the on-device screenshot path.

---

## 7. Files

**New (`:core:domain`)**
`block/BlockCoordinator.kt`, `block/DefaultBlockCoordinator.kt`, `block/ProtectionMode.kt`,
`block/web/BrowserSpec.kt`, `block/web/BrowserRegistry.kt`,
`block/web/HostNormalizer.kt`, `block/web/BrowserHostExtractor.kt`

**New (`:core:data`)** — `test/kotlin/com/sawn/core/data/SawnDatabaseMigrationSqlTest.kt`,
`test/kotlin/com/sawn/core/data/SettingsStoreEngineModeTest.kt`,
`androidTest/kotlin/com/sawn/core/data/SawnDatabaseMigrationTest.kt`

**New (`:app`)** — `accessibility/BrowserBlockingEngine.kt`,
`accessibility/BlockOverlayController.kt`, `block/SawnBlockCoordinatorEnv.kt`,
`block/SawnEngines.kt`, `ui/status/ProtectionBadge.kt`,
`ui/onboarding/steps/ProtectionModeStep.kt`

**New (tests, `:core:domain`)** — `BlockCoordinatorTest`, `HostNormalizerTest`,
`BrowserHostExtractorTest`, `BrowserRegistryTest`, `LockdownPolicyPropertyTest`,
`ScheduleEvaluatorMidnightTest`, `DomainMatcherBoundaryTest`

**Deleted** — `app/src/main/java/com/sawn/app/system/BiometricHelper.kt` (dead code) and
the `androidx.biometric` dependency that only it used.

**Changed** — `SettingsStore`, `SawnDatabase` (+`MIGRATION_4_5`), `ProtectedDayEntity`,
`ProtectedDaysDao`, `RoomProtectedDaysRepository`, `ProtectedDaysRepository`,
`BootRestorePolicy`, `SawnViewModel`, `HomeScreen`, `SettingsSections`,
`AppBlockAccessibilityService`, `VpnController`, `BootReceiver`, `GuardHealthWorker`,
`KeepAliveWorker`, `SawnApp`, `AppModule`, `OnboardingHost`, `FeatureDetector`
(`UiNode.className`), `A11yNode`, `BackupRepository`, the version catalog, and the
`:core:domain` / `:core:data` build files.

**Docs** — `README.md`, `docs/spec.md`, `docs/SECURITY.md`, `docs/privacy.md`.

---

# 8. الفئة الأولى — الأمان والموثوقية

Three gaps closed on top of the optional-VPN branch. None of them is a new feature:
this is the *ground* — the part that has to be solid before anything else is built on
it.

## 8.1 The updater verifies the signature itself

**The gap.** `UpdateInstaller` checked the manifest's SHA-256, the package name and the
version code — but not the **signature**. A SHA-256 proves the file matches *the
manifest*; it does not prove the file was signed by *us*. If the manifest is replaced,
or served from a compromised page, an APK carrying our package name and a higher
version code passes the checksum. Android's installer would then refuse it (the
signature does not match the installed app) — but only until the user uninstalls the
real app first, and then the forged APK installs cleanly. That is the "unsigned APK
installed by updater (RCE)" hole.

**What changed.**

| Where | What |
|---|---|
| `core/domain/.../update/ApkSignaturePolicy.kt` (new) | The verdict, as pure Kotlin: `ACCEPTED` / `UNSIGNED` / `FOREIGN_KEY` / `UNREADABLE` |
| `app/.../update/UpdateInstaller.kt` | `verifyApkSignature(context, apkFile)`, called **before** any installer call |
| `app/.../update/ApkSigningCertificates.kt` (new) | The API 28+ certificate reader, kept apart so Android 8 still links |
| `app/src/main/res/values{,-ar}/strings.xml` | «فشل التحقق من توقيع التحديث — تم إلغاء التثبيت» |

- The archive's certificates are read through
  `getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)` and compared, as SHA-256, with
  the **installed app's own signing lineage**
  (`getPackageInfo(...).signingInfo.signingCertificateHistory`).
- The candidate is accepted when it shares **any** fingerprint with our lineage — the
  same rule Android's own installer applies, and the reason a key rotation keeps
  working instead of bricking the updater for everybody on the old key.
- The order is fixed: checksum → package → version → **signature** → installer. A file
  that is not ours never reaches `PackageInstaller` / `ACTION_VIEW`.
- The log line carries the **verdict only** (`verdict=FOREIGN_KEY`) — never the
  certificate, never the file.

**Why the anchor is the installed app, not a compiled-in hash.** A fingerprint baked into
`BuildConfig` or a string resource lives inside the very file an attacker would
repackage. The installed app's signature is held by the system, out of reach.

**API levels.** `android.content.pm.SigningInfo` does not exist below API 28, so it
lives in its own class and is only ever touched on a system that has it; below API 28
the legacy `GET_SIGNATURES` array is read instead (there is no lineage to read there —
key rotation does not exist on Android 8 either).

## 8.2 The accessibility watchdog comes back on its own

**The gap.** The watchdog noticed a dead protection, but slowly, silently, and it could
not tell "the user revoked the permission" from "the OEM killed the service".

**What changed.**

- **A grace period of 30 seconds** (`GuardHealthPolicy.PROCESS_GRACE_MS`). The system
  binds the accessibility service a moment after the process is alive; a check landing
  in that window would report a protection that is merely waking up. The process age
  comes from `Process.getStartUptimeMillis()` — recorded by the system, so it is exact
  and immune to clock changes.
- **The strict check runs every 3 minutes** and the ordinary one every 15
  (`GuardHealthWorker.STRICT_INTERVAL_MINUTES` / `PERIODIC_INTERVAL_MINUTES`). The fast
  one is a self-rescheduling one-time request, because WorkManager refuses a periodic
  interval below 15 minutes; it now runs the *same* health check, not just a timer, and
  it is started when strict mode is switched on.
- **A bounded self-heal.** The watchdog asks the system to rebind the service
  (`startService` on the accessibility component — the only route available without
  `WRITE_SECURE_SETTINGS`), at most `GuardHealthPolicy.MAX_SELF_HEAL_ATTEMPTS` (3)
  times, with `BackoffPolicy.EXPONENTIAL`. The attempt count travels with the request
  (`StrictGuardHealthWorker.ATTEMPT_KEY`), so the fast chain cannot loop for ever.
- **An ongoing notice.** The notification is `PRIORITY_HIGH` + `setOngoing(true)` (it
  cannot be swiped away), and its tap opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`
  directly — the next tap gets the service back on.
- **`AppBlockAccessibilityService.connected`** — a `StateFlow` that says whether the
  service is *bound*, not merely permitted. "Permitted but not bound" is the state an
  OEM leaves behind after killing the service, and it is the one that can be healed.

**The honest limit.** On some devices (Pixel + `am force-stop`) the
`accessibility_enabled` setting itself is cleared, so no app can revive the service —
the user must re-enable it. That is an Android limitation, not an app bug, and it is now
written down in `docs/SECURITY.md`.

## 8.3 The invariants, as properties

The three rules that must never break are now *properties*, checked over thousands of
generated inputs instead of a handful of examples.

| Rule | Why it matters if it breaks |
|---|---|
| `LockdownPolicy`: a live lock is never shortened | The user could shorten the very lock they set for themselves |
| `LockdownPolicy`: an extension is cumulative | The duration is added to the time that is *left*, not to the moment of the press |
| `ScheduleEvaluator`: every minute inside the window is active | The blocking would silently stop in the middle of the window the user set |
| `ScheduleEvaluator`: a window crossing midnight belongs to two days | The small hours of the next day would be unprotected |
| `DomainMatcher`: a subdomain of a blocked domain is blocked | The blocklist would not cover the site it names |
| `DomainMatcher`: a domain that merely shares the suffix is not blocked | The blocklist would block innocent sites |

No test reads the wall clock: the "now" is always an explicit parameter.

`LockdownPolicy.extend(currentUntil, durationMs, now)` moved the extension rule into
the pure policy (it was inline in the view model) so the property has something to
test; the view model now calls it.

## 8.5 الاختبارات الجديدة

| Suite | What it covers |
|---|---|
| `ApkSignaturePolicyTest` (Kotest, `:core:domain`) | Our key / a foreign key / unsigned / a rotated key in both directions / a look-alike prefix / an unreadable lineage / the fingerprint format |
| `UpdateInstallerSignatureTest` (Robolectric, `:app`) | The real `verifyApkSignature` against a faked package manager: ours accepted, a foreign key refused, unsigned refused, an unreadable archive refused, a corrupt archive refused without throwing, and refused when *our* certificate cannot be read |
| `GuardHealthPolicyTest` (Kotest, `:core:domain`) | The grace period, the four states, and the bound on the self-heal |
| `GuardHealthWorkerTest` (JUnit, `:app`) | The cadence: the strict check every 2-3 minutes, the ordinary one every 15-30 |
| `GuardHealthNotifierTest` (Robolectric, `:app`) | The notice is ongoing, high priority, and opens the accessibility settings; it is cleared when the service is back |
| `LockdownPolicyPropertyTest` (extended) | **Invariant 1** a live lock is never shortened; **Invariant 2** an extension is cumulative |
| `ScheduleEvaluatorPropertyTest` (new) | **Invariant 1** every minute inside a window is active; **Invariant 2** a window crossing midnight belongs to two days |
| `DomainMatcherPropertyTest` (new) | **Invariant 1** a subdomain is blocked; **Invariant 2** a domain that only shares the suffix is not |

The app-level signature test runs on API 26 — the only Robolectric system image
available offline — so it exercises the legacy certificate path. The key-rotation
rule lives in `ApkSignaturePolicyTest`, where the lineage is compared directly.

## 8.6 التحقق

| Command | Result |
|---|---|
| `./gradlew test` | **BUILD SUCCESSFUL** — 320 tests in `:core:domain`, 34 in `:core:data`, 88 in `:app`; 0 failures |
| `./gradlew :app:compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew :app:compileReleaseKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew :app:lintDebug` | **1 error, 52 warnings** — the *same* pre-existing error as before this work (`ImageScreeningOrchestrator.kt`, `capture()` needs API 30). **No finding mentions any file added or changed here.** |

Not run, and why: `assembleRelease` / `bundleRelease` (forbidden by the task — no APK or
AAB was produced, and no signing happened), `connectedAndroidTest` (needs a device; the
developer's job).

## 8.7 الملفات

**New (`:core:domain`)** — `update/ApkSignaturePolicy.kt`, `system/GuardHealthPolicy.kt`

**New (`:app`)** — `update/SigningCertificates.kt` (the one reader of the signing
certificates: the archive, the installed app, and the modern/legacy API split)

**New (tests)** — `core/domain/.../update/ApkSignaturePolicyTest.kt`,
`core/domain/.../system/GuardHealthPolicyTest.kt`,
`core/domain/.../block/ScheduleEvaluatorPropertyTest.kt`,
`core/domain/.../block/DomainMatcherPropertyTest.kt`,
`app/src/test/.../update/UpdateInstallerSignatureTest.kt`,
`app/src/test/.../system/GuardHealthNotifierTest.kt`,
`app/src/test/.../system/GuardHealthWorkerTest.kt`

**Changed** — `UpdateInstaller`, `GuardHealthWorker`, `GuardHealthNotifier`,
`AppBlockAccessibilityService`, `SawnViewModel`, `SettingsScreen` (the About card now
uses the shared certificate reader), `LockdownPolicy`,
`LockdownPolicyPropertyTest`, both `strings.xml`, `CHANGES.md`, `README.md`,
`docs/SECURITY.md`, `docs/device-testing.md`.

**Not touched** — `docs/BlockingFeatureEvaluation.md` (an unrelated working document
that was already in the tree as an untracked file). Every other document **was**
updated, `docs/device-testing.md` included — it now carries the manual scenario for a
re-signed APK.

## 8.9 تطهير الهوية

A separate branch (`feat/identity-purge`) removed the old identity and the leftovers
from the repository. Nothing about the app's behaviour changed: no code path was
touched, only text, two dead files, and dead imports.

| Removed | Why |
|---|---|
| `LICENSE` | The project is not open source, and it carries no licence text |
| `scripts/repoint_hosting.py` | A one-shot migration that already ran (every replacement now maps a string to itself), unreferenced, and it carried the old name |
| `CHANGES.md` §8.4 "المصادر" | The whole credits table — external repositories are not part of this project's record |
| 73 unused imports in 17 Kotlin files | Imports with no remaining reference (the `getValue`/`setValue` operator imports were kept — `by` needs them) |

Text that carried the old name was rewritten rather than deleted where the sentence had
to keep its meaning: the install guides now say "the previous app", the changelog says
"the previous name", and the About card in the settings reads the signing certificate
through the same reader the updater uses.

The `verify-release.ps1` stale-hosting check kept its diagnostic but no longer names
the old hosting: it now matches *any* GitHub Pages manifest URL, so it still catches a
build that points at an earlier host.

No policy, privacy, about, description, or licence text was written. The files that
were **not** touched are listed in `PROGRESS.md` §6, with the reason for each.

## 8.10 إغلاق الديون المعلّقة

Five debts carried over from the earlier rounds, closed on the same branch.

| # | The debt | How it was closed |
|---|---|---|
| 1 | `ImageScreeningOrchestrator.kt:84` — `capture()` is API 30 on a `minSdk 26` app | The `SDK_INT < R` guard moved **into `maybeScreenRetry()`**, the method that calls the API-30 code. It was already unreachable below API 30 (the guard was two frames up in `maybeScreen()`), so this is not a behaviour change — it puts the protection next to the call and lets lint prove it. **The lint gate is now error-free.** |
| 2 | The property generators could produce a window with `start == end` | The generators moved into a shared `ScheduleWindows` object, and a new property asserts their own contract (`start < end` for a day window, `start > end` for a night window) over every sample. A generator that breaks its contract now fails in a test that names the cause, instead of failing a property that looks like an evaluator bug. |
| 3 | `LICENSE` was removed with the identity purge | Restored as a two-line proprietary notice. |
| 4 | The project policy lived outside the repository | `AGENTS.md` now sits in the repository root: the project's identity, the module boundaries, the clean-state policy, the verification commands, and what this workflow may not do. |
| 5 | The `v2.2.0` tag pointed one commit behind the released `main` | The tag moved from `2ae3d41` to `c14bc46` — the state `main` was in when the release went out. The difference between the two is a single documentation file, so the published APK stays reproducible from the tag; the GitHub release stays attached, and the `sha256` in the published manifest still matches the APK asset. |

## 9. القفل الفوري (Emergency Lockdown) + زر الذعر

A button in the app and a Quick Settings tile that start a **full** lockdown — every app,
every browser, every site — for 15, 30 or 60 minutes. The lock cannot be cancelled and
cannot be shortened before its timer ends, and it comes back after a reboot.

### 9.1 `:core:domain` — the rule, pure

| File | What it holds |
|---|---|
| `lockdown/LockdownSession.kt` | The session: `id`, `startedAt`, `endsAt`, `durationMinutes`, `reason`, plus the boot count and the wall deadline that survive a reboot |
| `lockdown/EmergencyLockdown.kt` | `start`, `canCancel`, `extend`, `isActive`, `remainingMillis` |

The rule is one-sided, and that is the whole feature:

- `canCancel` returns **false**, always. The person who pressed the button must not be
  the person able to undo it.
- `extend` only ever moves the deadline **later**, and grows the recorded duration with
  it — so `endsAt - startedAt == durationMinutes * 60_000` holds at every moment.
- The deadline is a **monotonic** clock reading (`SystemClock.elapsedRealtime()`), not
  wall time: moving the system clock must not shorten the lock. A monotonic clock
  resets at every boot, so the session also carries the wall deadline and the boot
  count, and the app re-anchors the session on the next start.

`EmergencyLockdownTest` proves all of it as properties (Kotest): the session ends
exactly when it says; the lock can never be cancelled; extending never shortens it.

### 9.2 `:core:data` — one session, no migration

`SettingsStore` gained `emergencyLockdownSession` (a `Flow<LockdownSession?>`),
`saveLockdownSession` and `clearLockdownSession`. The session is stored as **JSON** in
DataStore — one key, no Room table and no migration: a lockdown is a short-lived thing,
and the session is the only thing that has to survive a reboot.

### 9.3 `:app` — the enforcement

- **`EmergencyLockdownWorker`** — a one-time `WorkManager` request armed with the
  session's duration. WorkManager keeps it across a reboot, so the lock ends on time
  even if the device was off: a lock that could be ended by unplugging the phone would
  not be a lock.
- **`EmergencyLockdownEnforcer`** — one place that starts, extends and reads the lock.
  The engines read it from a hot path (a DNS packet, an accessibility event), so the
  session is cached in a volatile field and kept current by a collector.
- **`SawnVpnService`** — under the lock every host is blocked except the device's own
  (`mtalk.google.com` and friends); the blocklist is irrelevant while the lock runs.
- **`AppBlockAccessibilityService`** — under the lock every app is blocked, except the
  system essentials: phone, messages, camera, keyboard, NFC, TalkBack, and everything
  `CriticalApps` already protects.
- **`BootRestorePolicy`** — a live panic lock outranks every other flag (exactly like a
  time lock) and demands the full stack back; the boot receiver re-anchors the session
  and re-arms the timer.
- **`SawnLockdownTileService`** — a tap in the shade starts the 15-minute lock; the
  tile shows `STATE_ACTIVE` while it runs. The tile can only *start* a lock.
- **`EmergencyLockdownScreen` / `EmergencyLockdownViewModel` / `AddToHomeButton`** — the
  screen: three durations to start with, one button to extend, and **no cancel button
  anywhere**, because there is no cancel.

### 9.4 انحراف عن المواصفة

The specification asked for a **long press** on the tile to start a 30-minute lock.
Android does not deliver a long press to a `TileService` as a separate event — the
system calls `onStartListening()` when the panel opens, and there is no `onLongClick`
to hook — so the long press cannot be told apart from a tap reliably. Rather than
guess with a timer, the tile starts the 15-minute lock on a tap, and the duration
picker lives in the app where the user can see what they are choosing.


## 13. قرارات المنتج في الحجب (بلا كود)

The product decisions that came out of the blocking section, and the documentation
updated to match them. **No code was touched** — the implementation is the next step.

| Decision | Consequence |
|---|---|
| **Everything in-app is removed** | The 14 in-app features (Reels, Shorts, TikTok feed…), the social content detector (6 platforms) and the on-device NSFW image screening are all out of the product. The app now reads **no** screen content at all — no labels, no screenshots, no image classification. The privacy promise gets simpler and stronger, the app gets faster, and the TFLite model goes with it. |
| **The modes are renamed** | «كامل/خفيف» → **«متقدّم/عادي»** (9 strings, the badge, the onboarding). |
| **The DNS filter is the user's choice, even under a lock** | The time lock and the panic lock keep the **app-level** blocking on; they no longer force the DNS layer on, and switching it off is allowed with a warning. `forceLockdownCore`'s forced `VPN_ENABLED` goes with them. |
| **Strict mode is the one exception** | Strict mode demands the advanced mode and its key is PIN-gated — it is a deliberate choice behind a PIN, not a temporary lock. |
| **The honest promise of a lock** | «this app stays blocked», not «every site stays blocked». The panic lock's text is corrected to match. |

The documentation updated: `feature-map.md`, `browser-engine.md`, `dns-engine.md`,
`per-app-rule.md`, `README.md`, `SECURITY.md`, `spec.md`, `privacy.md`,
`play-compliance.md`, `device-testing.md`, `site-activation-{ar,en}.md`, and a banner on
the design-exploration HTML.

---

## 12. مواصفة محرك المتصفحات في الوضع الخفيف + فلترة DNS

Two more behaviour specs, written but not implemented.

**`docs/browser-engine.md`** — the light mode's browser engine, with the decisions taken
for the strongest protection the privacy promise allows: no "continue anyway" button
at all, the notice says once per host that network-level filtering is not running in
this mode, no browser counts in the interface, the coverage is the whole
`BrowserConfig` table rather than the seven advertised browsers, an unknown browser is
named in the protection checkup instead of being a silent hole, and no blocking by
title or page content. The one hole — a page opened inside another app — is closed by
the per-item rule instead, which blocks the containing app and keeps the privacy
promise.

**`docs/dns-engine.md`** — the VPN/DNS engine, the strongest one: only the resolver
space is routed (no other traffic), the blocked name is answered `0.0.0.0` rather than
dropped silently, AAAA never gets a real address, SafeSearch is a rewrite that the
user's own block overrides, and **no site name is ever logged** — so there is no
website history at all. The absolute protection lives in the accessibility layer, not
here: only that layer knows which app is in the foreground, and blocking every name
would break the phone.

---

## 11. مواصفة «قاعدة الحجب الخاصة بالعنصر»

`docs/per-app-rule.md` — the behaviour spec for a per-item rule, **written but not
implemented**: an element in the blocklist may carry either a **recurring window** (days
+ from/to) or an **allowance** (a daily budget). Outside the rule the element behaves
as it does today; inside it, it is blocked.

Nine decisions are settled in the document — among them: the rule cannot be changed or
removed before its first scheduled period ends, and `enabled = false` does not cancel
it; the rule forces the block **even when the user has turned protection off**; the
three-minute escape **consumes the allowance** and is capped by what is left; the old
per-item limit knobs are **unified into** the allowance; the notification fires when
the restriction **lifts**, not when it starts; the same model applies to websites; and
the rule applies to the **app as a whole**, not to a slice inside it.

This is a specification, not a change: no code was touched.

---

## 10. خريطة الميزات

`docs/feature-map.md` — the inventory every feature, by category, taken **from the code**
rather than from memory: the two blocking engines and their lists, the 14 in-app
features, the six social platforms, the schedules and conditions, the four locks, the
tools, the progress data, the external surfaces (two tiles, the widget, the
notifications, the boot restore, the update channel), what is stored, and what each
permission buys.

Each entry carries the file it lives in, the states it can be in, and the surface the
interface has to show — so the design and theme phase has one reference instead of
guessing from the screens.

It also lists what **does not exist** (no account, no subscription, no ads, no cloud
sync, no tracking), so nothing gets designed around a feature that is not there, and
five open questions that the design phase has to answer.

---

## 8.8 مراجعة بعد المراجعة الأولى

Four points came back from a review of the first pass. Three were real and are fixed
here; the fourth was a misreading.

| Point | Verdict | What changed |
|---|---|---|
| `verifyApkSignature` had no internal try/catch | **Real** | Both certificate reads are wrapped now: a corrupt or truncated archive is **refused**, not thrown at the caller. A new test drives a garbage file through it. |
| `docs/device-testing.md` "not touched" | **Not true** | It was updated in the first pass — the manual scenario for a re-signed APK is in section 19. The "Not touched" line above refers only to `docs/BlockingFeatureEvaluation.md`. |
| `NOTIF_ACCESSIBILITY` was private | **Real** | It is `internal` now, and the test addresses the notice by its id instead of by "the only notification posted". |
| `SettingsScreen.kt` read the certificates on its own | **Real** | One reader now: `SigningCertificates`. The updater and the About card cannot disagree about how a certificate is read. |

The unification also fixed a small bug in the About card: the signing history is ordered
**oldest → newest**, so the card was showing the *oldest* key of the lineage — the one a
key rotation replaced. It now shows the certificate that signed this build, which is the
one the published fingerprint refers to.



