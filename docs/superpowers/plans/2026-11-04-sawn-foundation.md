# Sawn (صَوْن) 2.0 — Foundation & Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a fully working صَوْن 2.0 app: all previously shipped features working on a clean three-module architecture, renamed to `com.sawn.app`, with the new «فجر الأمل» dawn design system (Cairo), Arabic + English, and a green release build — the launchpad for Plan B (12 new features + persistence/permissions hardening + release docs).

**Architecture:** Three Gradle modules. `:core:domain` = pure Kotlin (no Android imports) holding every policy, exhaustively unit-tested. `:core:data` = Room + DataStore behind repository interfaces owned by domain. `:app` = Compose UI + services (VPN, Accessibility, DeviceAdmin, QS Tile, BootReceiver, Workers) + Hilt wiring + ViewModels (MVVM, unidirectional StateFlow).

**Tech Stack:** AGP 8.13.2 · Gradle 8.13 · Kotlin 2.x (bump in libs.versions.toml to latest 2.2.x compatible) · KSP · Compose BOM (latest stable) · Material3 · Hilt · Room · DataStore · Navigation Compose · WorkManager · JUnit4 · kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-04-sawn-redesign-design.md` (read it — the spec governs all visuals, copy and scope; this plan argues from it).

## Global Constraints

- `applicationId` + `namespace` = `com.sawn.app` · `minSdk 26` · `targetSdk`/`compileSdk 36` · `versionName 2.0.0` NOT in this plan (Plan B sets it) · `minifyEnabled` stays off until Plan B.
- Root project name = `Sawn` · all files/packages/classes: zero occurrences of `warden` (case-insensitive) in `app/`, `gradle/`, `settings.gradle.kts`, resources, docs of the app.
- Every screen: Arabic **and** English string in `values/strings.xml` (English default) + `values-ar/strings.xml` · **Latin digits only** (`SawnNumbers`, Locale.US) · RTL for `ar`, LTR for `en` · remove all forced-RTL hacks · respect `reduceMotion`.
- Fonts: **Cairo only** (weights 400/500/600/700/900 in `res/font`, ≤5 files) · remove IBM Plex Arabic font files from `res/font` (recoverable in git; license file stays in `licenses/`).
- ProtectionGrid toggles never appear in Settings · Settings contains configuration only.
- Domain module: **no Android imports**. Every policy in `:core:domain` has unit tests; `./gradlew :core:domain:test` is a phase gate.
- No secrets in git: `local.properties`, `*.keystore` stay untracked · signing untouched this plan.
- Do not copy from `docs/re-block-reference/` — reimplement from public sources (StevenBlack/hosts etc.).
- Colors: dawn palette exactly as spec §5.4 (`#FFF7ED` bg, `#FFFFFF` card, `#431407` deep, `#EA580C` primary, `#F5D6BB` line, `#16A34A` success, `#FDBA74` gold; dark: `#1C1917` base / `#FDE8D8` text).
- Windows commands: `.\gradlew.bat` (PowerShell) · JDK 17 · commit after every task · keep the tree building green at every commit.

## Review Focus

1. **RTL/LTR breakage** — a screen that looks right in one locale must mirror correctly in the other (icons `start`/`end`, back arrows, chevrons, paddings). Pinned by: Task 13 Step 4 (emulator locale flip checklist).
2. **Protection dying silently** — swiping from recents or OEM killers stop the filter and the user believes he's protected. Pinned by: Task 7 Step 3 (service restart wiring is preserved and `stopWithTask="false"` verified in manifest) — full arbiter+flag lands in Plan B.
3. **Streak/day-counter drift** — duplicates per day, timezone/DST edge, clock-skew. Pinned by: Task 4 (protected_days as source of truth + DST/skew tests).
4. **Font/APK bloat or missing glyphs** — 5 Cairo files max; missing Arabic glyph = broken word. Pinned by: Task 8 Step 6 (R8/lint + APK size check ≤ 12 MB for this plan).
5. **Onboarding that lies** — CTA claims protection that isn't granted yet. Pinned by: Task 9 Step 4 (plan-reveal copy states planned vs enabled; nothing "enabled" without the permission granted).

---

## Task 1: Branch + baseline snapshot

**Files:** none (git only)

- [ ] **Step 1: Create the working branch**

```bash
git checkout -b sawn-rebuild
```

- [ ] **Step 2: Snapshot the current uncommitted state**

```bash
git add -A && git commit -m "baseline: snapshot of Warden redesign wave before Sawn rebuild"
```

(`.superpowers/` is gitignored; `local.properties` + `*.keystore` are gitignored — verify with `git status --porcelain` that neither appears.)

- [ ] **Step 3: Verify the build is green before touching anything**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (fix environment issues now, not mid-rebuild).

## Task 2: Rename everything to Sawn / com.sawn.app

**Files:**
- Rename dir: `app/src/main/java/com/warden/blocker/**` → `app/src/main/java/com/sawn/app/**` (git mv)
- Modify: `app/build.gradle.kts` (namespace, applicationId), `settings.gradle.kts` (rootProject.name), `AndroidManifest.xml` (application name if referenced), all `.kt` packages, resource strings (`app_name`), `tools:targetApi`, `accessibility_service_config.xml` references if any, tests dir `com/warden/blocker` → `com/sawn/app`

- [ ] **Step 1: Rename the source trees**

```bash
git mv app/src/main/java/com/warden app/src/main/java/com/sawn
git mv app/src/main/java/com/sawn/blocker app/src/main/java/com/sawn/app
git mv app/src/test/java/com/warden app/src/test/java/com/sawn
git mv app/src/test/java/com/sawn/blocker app/src/test/java/com/sawn/app
```

- [ ] **Step 2: Rewrite packages and namespaces (all Kotlin files)**

```bash
# package com.warden.blocker -> com.sawn.app ; import … same; class renames:
git grep -l "com.warden.blocker\|Warden" -- '*.kt' '*.xml' '*.kts' '*.md' '*.pro'
# then mechanically replace: com.warden.blocker -> com.sawn.app
#   WardenApp -> SawnApp · WardenVpnService -> SawnVpnService · WardenViewModel -> SawnViewModel
#   WardenTileService -> SawnTile · WardenNotificationListener -> SawnNotificationListener
#   WardenDeviceAdminReceiver -> SawnDeviceAdminReceiver · WardenTheme -> SawnTheme · WardenScaffold -> SawnScaffold
```

The mechanical replace is `:app`-wide; **verify zero hits afterwards:**
`git grep -i "warden" -- ':!docs/*' ':!*.iml'` → expected: no matches except gitignored/binary; `docs/` historical docs may keep the old name (they are history).

- [ ] **Step 3: Build files + strings**

`app/build.gradle.kts`: `namespace = "com.sawn.app"`, `applicationId = "com.sawn.app"`. `settings.gradle.kts`: `rootProject.name = "Sawn"`, add later modules in Task 3. `values*/strings.xml`: `app_name` = `صَوْن` (ar) / `Sawn` (en). Manifest: service/receiver/class names updated by Step 2.

- [ ] **Step 4: Verify green build + tests**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: SUCCESS; same test count as baseline.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: rename Warden -> Sawn (com.sawn.app)"
```

## Task 3: Toolchain + three modules

**Files:**
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` (bump wrapper only if AGP requires)
- Create: `core/domain/build.gradle.kts`, `core/domain/src/main/kotlin/com/sawn/domain/.keep`, `core/data/build.gradle.kts`, `core/data/src/main/AndroidManifest.xml`

- [ ] **Step 1: Bump versions in `gradle/libs.versions.toml`**

Set (latest stable verified resolvable at build time): `kotlin = "2.2.20"`, `ksp = "2.2.20-1.0.3x"` (matching KSP for Kotlin 2.2.20), `agp = "8.13.2"` (keep), `composeBom = "2025.09.01"` (or latest stable), `room = "2.8.x"`, `lifecycle = "2.9.x"`, `coreKtx = "1.17.0"`, `activityCompose = "1.11.x"`, `navigationCompose = "2.9.x"`, `workManager = "2.10.x"`, `datastore = "1.1.1"` (keep), `hilt = "2.56.x"`, `coroutines = "1.10.x"`, `junit = "4.13.2"`, `coroutinesTest = "1.10.x"`. Add libraries: `hilt-android`, `hilt-compiler`, `hilt-work`(if used), `kotlinx-coroutines-core`, `kotlinx-coroutines-test`, `kotlinx-serialization-json` (+ plugin) for Plan B; keep plugin block minimal now.

- [ ] **Step 2: Root `build.gradle.kts`** — add `alias(libs.plugins.hilt) apply false` and `alias(libs.plugins.kotlin.jvm) apply false`; add `kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }` and `hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }` in `[plugins]`.

- [ ] **Step 3: Module scaffolding**

`core/domain/build.gradle.kts`:

```kotlin
plugins { alias(libs.plugins.kotlinJvm) }
repositories { mavenCentral() }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit); testImplementation(libs.kotlinx.coroutines.test)
}
```

`core/data/build.gradle.kts`: `kotlin-android` + `ksp` + `android` library plugin; `namespace = "com.sawn.core.data"`; `compileSdk = 36`; `minSdk = 26`; depends on `:core:domain`, Room (runtime/ktx/compiler), DataStore, coroutines. `AndroidManifest.xml`: empty `<manifest/>`.

`settings.gradle.kts`: `rootProject.name = "Sawn"`; `include(":app", ":core:domain", ":core:data")`.

- [ ] **Step 4: Verify all modules build**

Run: `.\gradlew.bat :core:domain:assemble :core:data:assembleDebug :app:assembleDebug`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "build: three-module Sawn (core:domain, core:data) + toolchain bump"
```

## Task 4: `ProtectionDayPolicy` — the streak rebuilt from zero

**Files:**
- Create: `core/domain/src/main/kotlin/com/sawn/domain/ProtectionDayPolicy.kt`
- Test: `core/domain/src/test/kotlin/com/sawn/domain/ProtectionDayPolicyTest.kt`
- `StreakPolicy` is superseded by this task — its file moves to `:core:domain` in Task 5; its remaining usages switch to `ProtectionDayPolicy` in Task 10, and `domain/.../StreakPolicy.kt` + its test are deleted in Task 10.

**Interfaces:**
- Produces: `object ProtectionDayPolicy { fun currentStreak(days:Set<LocalDate>, today:LocalDate):Int; fun longestStreak(days:Set<LocalDate>):Int; fun isAtRisk(days:Set<LocalDate>, today:LocalDate):Boolean; fun heatmap(days:Set<LocalDate>, today:LocalDate, pastDays:Int):List<DayCell> }` and `data class DayCell(val date:LocalDate, val protected:Boolean)`.

- [ ] **Step 1: Write failing tests** (JUnit4, `java.time.LocalDate`) — pin these cases:

```kotlin
// same day recorded twice -> counts once
// consecutive days 2026-11-01..03, today=2026-11-03 -> currentStreak=3
// today=2026-11-04 (not recorded) -> currentStreak=3 still (streak survives until day ends)? NO per spec:
//   day must be protected to extend; missing day 11-04 shows streak=0 on 11-05; on 11-04 streak shows 3 as "ends today" only if yesterday was last protected.
// simplest consistent rule (spec §9): visible streak = consecutive protected days ending today or yesterday.
// clock skew: a protected day dated "tomorrow" does not crash; counts as streak of 1 today.
// longestStreak across a gap: {01,02,10,11,12} -> longest=3
// isAtRisk: yesterday protected and today not -> true; today protected -> false
```

Write exactly these tests; expected values per the rule: `currentStreak(days, today) = consecutive run ending at (today if today∈days else today-1)`.

- [ ] **Step 2: Run to verify fail** — `.\gradlew.bat :core:domain:test` → FAIL (unresolved reference).

- [ ] **Step 3: Implement** — `currentStreak`: iterate from `eligibleEnd = if (today in days) today else today.minusDays(1)` backwards while dates present, count. `longestStreak`: sort, scan. `isAtRisk = today !in days && today.minusDays(1) in days`. `heatmap`: map last N days to DayCell.

- [ ] **Step 4: Run tests green** — `.\gradlew.bat :core:domain:test`

- [ ] **Step 5: Commit** — `git commit -m "feat(domain): ProtectionDayPolicy — streak from protected_days source of truth"`

## Task 5: Port pure policies into `:core:domain`

**Files:** Move (git mv, package `com.sawn.blocker`→`com.sawn.domain` or keep historical sub-packages `com.sawn.domain/block`, `com.sawn.domain/feature`, `com.sawn.domain/block/web`): all files in `app/src/main/java/com/sawn/app/block/**`, `app/src/main/java/com/sawn/app/feature/**`, `app/src/main/java/com/sawn/app/security/**` (pure ones: `PinLockoutPolicy` — check `PinResult`), `app/src/main/java/com/sawn/app/usage/**` if pure. Tests correspondingly into `core/domain/src/test/...`.

**Interfaces:** Produces `SchedulePolicy`/`LockdownPolicy`/`BlockingPolicy`/`DomainMatcher`/`AdultHosts`/`WebUrlExtractor`/`BrowserConfig`/`FeatureCatalog`/`ProtectionCheckup`/`BlockJournal`/`CriticalApps`/`DnsPacket`/`DnsQuery` reachable as `com.sawn.domain.*` — exact names unchanged to minimize churn.

- [ ] **Step 1: Move files** with `git mv`; fix `package` lines; remove any `android.*` import (replace with pure Kotlin or move back to `:app` if truly Android-bound, e.g., `UsageStatsHelper` stays in `:app` or moves to `:core:data` as an Android-dependent helper — decide by compile errors).
- [ ] **Step 2: Move tests** the same way; `app` adds `implementation(project(":core:domain"))` and `testImplementation(project(":core:domain"))` to `dependencies`, and app code imports `com.sawn.domain.*`.
- [ ] **Step 3: Verify** — `.\gradlew.bat :core:domain:test :app:assembleDebug testDebugUnitTest` — all tests ported, zero lost (compare counts).
- [ ] **Step 4: Commit** — `git commit -m "refactor(domain): port pure policies (block/feature/security/vpn) to core:domain with tests"`

## Task 6: `:core:data` — Room schema + repositories + DataStore

**Files:**
- Create: `core/data/src/main/kotlin/com/sawn/core/data/db/SawnDatabase.kt` · `entities/` (`BlockItemEntity`, `ScheduleEntity`, `BlockJournalEntity`, `ProtectedDayEntity`, `ChallengeRunEntity`, `ChallengeDayEntity`, `TriggerLogEntity`) · `dao/` · `SettingsDataStore.kt` (rename all `warden_*` keys → `sawn_*`) · `repository/` (`ItemsRepository`, `SchedulesRepository`, `ProtectedDaysRepository`, `JournalRepository`, `StatsRepository`, `SettingsRepository`, `ChallengeRepository`) · `di/` empty now.
- Interfaces consumed by Plan B tasks: repository interfaces live in `:core:domain` (`com.sawn.domain.repo`) with Room-backed impls in `:core:data`.

- [ ] **Step 1: Entities + DAOs** — fields exactly per spec:
  - `BlockItemEntity`: id, packageName, label, mode, iconKey?, enabled, opensPerDay, minutesPerDay, cooldownMinutes, dailyMinutesLeft?, createdAt
  - `ScheduleEntity`: id, name, daysMask, startMinute, endMinute, enabled
  - `BlockJournalEntity`: id, ts, packageName?, domain?, reason (BLOCKED/PAUSE_DEFEAT/RELAPSE/TRIGGER?), count
  - `ProtectedDayEntity`: date TEXT PK (ISO `yyyy-MM-dd`), source TEXT (`VPN`/`FOCUS`/`SCHEDULE`/`MANUAL`), recordedAt
  - `ChallengeRunEntity`/`ChallengeDayEntity`, `TriggerLogEntity` — write the two Plan-B tables now (empty DAOs fine) so the schema exports once.
- [ ] **Step 2: Database + migration 0→1 + `fallbackToDestructiveMigration` NOT used; export schemas** (`room.schemaLocation` already set) — first version: `version = 1`, `createAll`.
- [ ] **Step 3: DataStore** — `sawn_settings` with typed keys (`masterEnabled`, `pornGuard`, `safeSearchForce`, `blockAds`, `blockDoh`, `blockNotifications`, `strictMode`, `alwaysOn`, `reduceMotion`, `locale`, `userDisabledProtection`, `onboardingCompleted`, `onboardingAnswersJson`, `profileName`, `profileAge`, `nightGuardEnabled`, `nightGuardStartMin`, `nightGuardEndMin`, `parentPinHash`, `responsibilityPartner*`, `nudgesEnabled`). Migration note: **fresh start** — no legacy read.
- [ ] **Step 4: Repositories** — interfaces in `:core:domain` (`com.sawn.domain.repo`), Room impls in `:core:data` bound via Hilt (Task 7). `ProtectedDaysRepository` contract: `suspend fun upsert(date: LocalDate, source: String); fun observeProtectedDays(): Flow<Set<LocalDate>>; suspend fun allDays(): List<LocalDate>` — **idempotent upsert on the ISO date PK**. `StatsRepository` contract: `fun currentStreak(): Flow<Int>; fun longestStreak(): Flow<Int>; fun isAtRisk(): Flow<Boolean>` — derived from `observeProtectedDays()` + `ProtectionDayPolicy` (Task 4) with `LocalDate.now(ZoneId.systemDefault())` at emission time. Room DAOs may `@Query("SELECT * FROM protected_days")` freely (tiny table).
- [ ] **Step 5: Verify** — `:core:data:assembleDebug` + `:core:data:kspDebugKotlin` + one Room in-memory DAO test for ProtectedDays (upsert idempotence) via **Robolectric** (`testOptions.unitTests.isIncludeAndroidResources = true`, `testImplementation(libs.androidx.room.testing)` + `robolectric`). If Robolectric cannot resolve offline, replace the DAO test with an `androidTest` skeleton (not blocking) and say so in the report — persistence correctness is otherwise covered by Task 4's pure unit tests; `:core:data:testDebugUnitTest` green.
- [ ] **Step 6: Commit** — `git commit -m "feat(data): SawnDatabase v1 (protected_days, journal, challenge, triggers) + sawn_* DataStore + repositories"`

## Task 7: Hilt + services wiring (foundation only)

**Files:** Modify `:app` — `SawnApp.kt` (`@HiltAndroidApp`), root `Module` providing SawnDatabase/Settings/repos, services to `@AndroidEntryPoint` where needed (`SawnVpnService`, `InterceptActivity`, `MainActivity`), replace manual `wardenContainer` with injected graph, keep `SawnVpnService` behavior identical (START_STICKY, `stopWithTask="false"`).

- [ ] **Step 1: Annotate app + modules**; remove `ServiceLocator`/manual container; ViewModel `@HiltViewModel` with constructor injection.
- [ ] **Step 2: `.\gradlew.bat assembleDebug` green.**
- [ ] **Step 3: Verify manifest** still has `android:stopWithTask="false"` on `.vpn.SawnVpnService` and `android:foregroundServiceType="specialUse"` + subtype property; `BootReceiver` exported with BOOT_COMPLETED. (No behavior changes this task.)
- [ ] **Step 4: Commit** — `git commit -m "refactor(app): Hilt DI, drop manual container; VPN/boot invariants verified"`

## Task 8: Design system «فجر» — Cairo, tokens, components

**Files:**
- Modify/create: `app/src/main/res/font/cairo_{regular,medium,semibold,bold,black}.ttf` (download from Google Fonts OFL zip — Cairo variable font static instances; keep 5 files)
- `git rm app/src/main/res/font/ibm_plex_sans_arabic_*.ttf` (5 files; license stays `licenses/IBMPlexSansArabic-OFL.txt`? replace with `licenses/Cairo-OFL.txt`) — report in handoff
- Rewrite: `ui/theme/Color.kt|Type.kt|Theme.kt` (delete old), `ui/components/` (new): `DawnBackground.kt`, `SawnTopBar.kt`, `HeroShield.kt`, `ProtectionGrid.kt`, `PresetChips.kt`, `ProgressStrip.kt`, `StatTile.kt`, `FeatureRow.kt`, `PillCTA.kt`, `SectionHeader.kt`, `ScreenScaffold.kt`, `SawnNumbers.kt`

**Interfaces:**
- `object SawnNumbers { fun format(n:Long):String }` (Locale.US)
- `@Composable fun ScreenScaffold(title:String?, onBack:(()->Unit)?=null, actions:@Composable RowScope.()->Unit = {}, content:@Composable ColumnScope.()->Unit)` — edge-to-edge, DawnBackground, RTL-aware
- `@Composable fun HeroShield(state:HeroState, modifier:Modifier=Modifier)` with `enum class HeroState { PROTECTED, PARTIAL, DOWN, NEEDS_SETUP, LOCKDOWN }`
- Colors/Type per spec §5.4; shapes 16/20/22dp; `dawnColorScheme(dark:Boolean)`.

- [ ] **Step 1: Fonts** — download Cairo (`Cairo-Regular/Medium/SemiBold/Bold/Black.ttf`) into `res/font`; add `isBold` variants mapping in `Type.kt`; verify `aapt` packs (build).
- [ ] **Step 2: Tokens** — `Color.kt` (light+dark schemes), `Type.kt` (Cairo family; Arabic-first lineHeights), `Shape.kt`.
- [ ] **Step 3: Components** — implement the 12 components with previews (`@Preview` AR + EN) — HeroShield draws: ring progress (0–100) with `drawCircle` + rounded arc + sun-shield glyph; ProtectionGrid: 2×3 toggles with `Switch` sized large; all interactive elements have `contentDescription`s.
- [ ] **Step 4: DawnBackground** — radial warm gradient + subtle sun disc (Canvas), reduced alpha in dark.
- [ ] **Step 5: Verify** — build + Compose previews render; `.\gradlew.bat assembleDebug`.
- [ ] **Step 6: APK size check** — `Get-Item app\build\outputs\apk\debug\app-debug.apk | Select-Object Length` ≤ 15 MB; commit `git commit -m "feat(ui): dawn design system (Cairo) + Sawn components"`

## Task 9: App shell + navigation + Onboarding «فجر الأمل»

**Files:**
- Rewrite: `MainActivity.kt` (setContent → SawnTheme → hilt viewModel), `ui/SawnRoot.kt` (NavHost: `onboarding` (only if `!settings.onboardingCompleted`), `home`, `setup`, `blocklist`, `apppicker`, `schedules`, `stats`, `settings`, `settings/*`, `challenge` (Plan B), start = `if (essentialsGranted) "home" else "setup"`), delete old `ui/Screens.kt` monolith; screens split by file (`ui/home/HomeScreen.kt`, `ui/setup/SetupScreen.kt`, `ui/settings/*`, `ui/stats/StatsScreen.kt`, `ui/intercept/*` keep).
- New: `ui/onboarding/OnboardingHost.kt` (state machine), `WelcomeStep.kt`, `ProfileStep.kt`, `QuestionStep.kt` (one component, 4 questions via config), `PlanRevealStep.kt` · `domain/PlanBuilder.kt` + tests (already core:domain; add Task 9 tests).

**Interfaces:**
- `PlanBuilder.build(answers:OnboardingAnswers):Plan` where `OnboardingAnswers(source,goal,commitment,content:Set<String>,name:String?,age:Int?) : DomainModel in :core:domain`; `Plan(featureIds:List<String>, requiresPin:Boolean)`.
- Strings `onboard_q1…q4_*`, `plan_*`, `welcome_*` in AR/EN — write them Arabic-first per spec §6 copy (title «صباحٌ بلا ضجيج», CTA «ابدأ رحلتك النظيفة»).

- [ ] **Step 1: PlanBuilder + failing tests** (5 cases: صارم→requiresPin; إباحية→VPN+SafeSearch first; إعلانات→ads item; goal=حماية العائلة→parent mode suggestion item; goal=تركيز→deep focus item)
- [ ] **Step 2: WelcomeStep + ProfileStep** — Canvas sun-guardian avatar (breathing glow via `animateFloat` + `infiniteRepeatable`, honor `reduceMotion`); CTA → ProfileStep → QuestionStep(q1).
- [ ] **Step 3: QuestionStep** — single-select cards (spring animation on select) · multi-select chips for q4 · progress bar (RTL-filled) · back support · «تخطي» only on Profile step.
- [ ] **Step 4: PlanRevealStep** — staggered rows (120ms), animated counter «4/4 جاهز», primary CTA «فعّل الحماية — خطوات سريعة» → `nav.navigate("setup")`, secondary text button → `home`; copy must not claim enabled state — wording per spec §6.
- [ ] **Step 5: Onboarding completion** — `vm.completeOnboarding(answers)` persists to sawn_settings; route guarded by LaunchedEffect.
- [ ] **Step 6: Verify** — emulator walkthrough AR + EN; `.\gradlew.bat assembleDebug testDebugUnitTest` · commit `git commit -m "feat(onboarding): dawn welcome + 4 questions + plan reveal"`

## Task 10: Home «لوحة الفجر» rewrite

**Files:** `ui/home/HomeScreen.kt` + `home/` components reuse; ViewModel split: `HomeViewModel` (state) — keep `SawnViewModel` or split screens' state as appropriate.

- [ ] **Step 1: Sections per spec §7**: TopBar (greeting `{name}` + streak chip + gear→`settings`) · HeroShield w/ `HeroState` derived from (enforcementActive&&vpnRunning, needs setup, lockdown) · conditional AlertRows (only when broken) · «الحماية المباشرة» 2×3 grid (porn/safesearch/ads/doh/notifications/strict) wired to existing VM setters · PresetChips (Light/Balanced/Max) · ProgressStrip (streak/longest/sessions — from `StatsRepository`, computed by `ProtectionDayPolicy`; switch `currentStreak`/`longestStreak`/`streakAtRisk` off `StreakPolicy`, then `git rm` `domain/.../StreakPolicy.kt` + its test) · «أدواتي» (Focus 25/50 via existing focus flow, SOS existing `UrgeSos`) — night guard chip only if enabled (Plan B flag).
- [ ] **Step 2: Delete old monolith blocks** replaced; no tabs/bottom nav; settings links removed from home (gear only).
- [ ] **Step 3: Strings AR/EN** for all home copy; RTL check both locales.
- [ ] **Step 4: Verify** — walkthrough AR/EN + toggles work + `assembleDebug` · commit `git commit -m "feat(home): لوحة الفجر — direct features, no tabs"`

## Task 11: Settings screen rewrite (configuration only)

**Files:** `ui/settings/SettingsScreen.kt` + sections in `settings/` (reuse `SettingsSections.kt` patterns) — remove all protection toggles; new sections AR/EN: الملف الشخصي · اللغة · الأمان (PIN, fingerprint stub «قريباً» — Plan B wires Biometric; do NOT show unfinished toggles — hide until B) · الحماية (always-on, battery exemption w/ OEM paths, private-DNS warning, blocklist refresh) · الخصوصية (privacy policy in-app view, «مسح كل بياناتي» — wipes Room+DataStore, «التراخيص») · حول (version 1.4.0→next, SHA‑256 of signing cert placeholder constant, TODO(site) links) · «إعادة عرض المقدمة» (resets onboarding flag).

- [ ] **Step 1: Implement sections; every item functional or hidden — no dead toggles.**
- [ ] **Step 2: «مسح كل بياناتي»** — confirmation dialog → `SawnDatabase.clearAllTables()` + DataStore `edit { it.clear() }` + stopProtection → exit app. PK-gated inside dialog if PIN set.
- [ ] **Step 3: Verify** build + walkthrough · commit `git commit -m "feat(settings): configuration-only grouped settings + wipe-all-data"`

## Task 12: Preserve essential flows; language cleanup; no-warden sweep

**Files:** `InterceptActivity` (re-theme dawn — keep behavior), `ui/SetupScreen.kt` → move to `ui/setup/` rethemed dawn (behavior unchanged; wizard upgrade is Plan B), `ui/AppPickerScreen.kt`, `ui/ScheduleEditor.kt`, `ui/PinScreen.kt`, `ui/UrgeSos.kt` («لحظة ضعف»), `ui/status/*` — retheme to dawn tokens.

- [ ] **Step 1: Retheme each to dawn components; no behavior change; no copy changes except AR/EN + digits.**
- [ ] **Step 2: `git grep -i warden` → zero** (excluding `docs/`).
- [ ] **Step 3: `.\gradlew.bat assembleDebug testDebugUnitTest lint` green** · commit `git commit -m "style: retheme intercept/setup/pin/sos/picker/schedules to dawn; warden sweep clean"`

## Task 13: i18n + RTL/LTR + digits (full pass)

- [ ] **Step 1:** English `values/strings.xml` **complete** — zero untranslated `tools:*` keys; Arabic complete in `values-ar/strings.xml` (same keys).
- [ ] **Step 2:** Remove `ForceRTL`/RtlContextWrapper hacks; rely on `supportsRtl` + per-locale direction; Chevrons/back icons use `Icons.AutoMirrored.*`.
- [ ] **Step 3:** Replace every `stringResource(...)` number with `%1$s` + `SawnNumbers.format` (or `%1d` US-formatted) — no Eastern Arabic-Indic digits anywhere.
- [ ] **Step 4: Emulator checklist (AR + EN)** — onboarding (all steps + plan), home (toggles, presets, progress), setup, settings, intercept preview (via blocked app? verify visually via tooling or screenshots) · `.\gradlew.bat lint` with `abortOnError true` · commit `git commit -m "i18n: full AR/EN + RTL/LTR + Latin digits"`

## Task 14: Plan A gate — build, APK, tag

- [ ] **Step 1:** `.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest testDebugUnitTest lint assembleRelease` — **release build must succeed** (unsigned or debug-signed OK this plan; minify stays off).
- [ ] **Step 2:** Record APK size + test counts; paste into `docs/PROGRESS-2.0.md` (create) — the ledger Plan B continues.
- [ ] **Step 3:** `git tag v2.0.0-foundation` · commit `git commit -m "chore: Sawn 2.0 foundation gate — all green" && git tag v2.0.0-foundation`

---

## Next plan

**Plan B — Features & Hardening** is authored after Task 14 (its exact interfaces reference the real Plan A code): 12 features · `userDisabledProtection` + `RestartArbiter` + boot/health-worker enforcement of «لا تتوقف إلا من داخل التطبيق» · setup wizard (sideload detection, restricted-settings deep-link, Play Protect guide) · biometric · backup `.sawnbak` · heatmap/share card/widget · parental mode · `versionName 2.0.0`/`versionCode 20` · R8 + signing + apksigner verify · `PLAY_COMPLIANCE.md` + `PRIVACY.md` + `CHANGELOG.md` + site guides + README/SPEC rewrite.
