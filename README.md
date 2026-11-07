# Sawn 2.0

**صَوْن (Sawn) — a local, account-free, tracking-free Android blocker.** Blocks websites (via an
on-device DNS filter), apps, adult content, ads/trackers, and the endless scroll (Reels/Shorts
only), with schedules, focus sessions, strict mode, time lock, and a streak rebuilt from a
single source of truth. Arabic-first with full English; Latin digits everywhere.

> **Distribution:** side-loaded APK from the owner's website in this release. Google Play
> listing comes later. See [`docs/install-ar.md`](docs/install-ar.md) /
> [`docs/install-en.md`](docs/install-en.md).

---

## How blocking works

Sawn uses **two independent layers**, so there's no single point of bypass:

| Layer | Mechanism | Blocks |
|-------|-----------|--------|
| **Website filtering** | A local, on-device `VpnService` that intercepts DNS and blackholes queries for blocked domains. Non-blocked queries are forwarded to an upstream resolver. | Websites in **every** browser and app |
| **App blocking** | An `AccessibilityService` detects when a blocked app comes to the foreground and shows a full-screen block screen. | Native apps |

The VPN is **local-only** — DNS is filtered on-device, allowed lookups go to the resolver
already configured on your network (with a fallback only when Android exposes none), and no
query is logged. Other traffic is not proxied. Sawn adds itself to the disallowed-apps list
so it never filters its own traffic. See [`SPEC.md`](SPEC.md) for the full architecture and
privacy model.

## Design

"Dawn of Hope" — warm sunrise gradient (burnt orange `#EA580C` primary, cream `#FFF7ED`
background, dark ink `#431407`), Cairo variable font (Google, OFL), drawn `HeroShield`,
`ProgressStrip`, `DawnCard`. No stock illustrations; everything is Compose Canvas.

## Architecture

Three Gradle modules:

- `:core:domain` — pure Kotlin (no Android): `ProtectionDayPolicy`, `SchedulePolicy`,
  `TriggerInsights`, `ChallengePolicy`, `BackupCrypto`, `PlanBuilder`, `AdultHostMatcher`.
  Fully unit-tested.
- `:core:data` — Room (v3, `protected_days` source of truth) + DataStore + repositories
  behind domain interfaces.
- `:app` — Compose UI + services (VPN/Accessibility/DeviceAdmin/Tile/BootReceiver/Workers)
  + ViewModels (StateFlow + events) + Hilt.

## Feature status (2.0.0)

**Rebuilt from scratch (Plan A — foundation):**
- [x] Rename Warden → Sawn (`com.sawn.app`), three-module Gradle, Hilt, Dawn design system
- [x] Onboarding: welcome + local profile (name/age) + 4 questions + "Personal Plan" reveal
- [x] Home "لوحة الفجر": single page, no tabs, all features visible
- [x] Settings: profile, language (AR/EN/system), PIN, sections, wipe-all-data, About (SHA-256)
- [x] Streak rebuilt from `protected_days` (source of truth) — `ProtectionDayPolicy`
- [x] Full i18n AR/EN, RTL/LTR, Latin digits via `SawnNumbers`
- [x] `userDisabledProtection` flag: protection stops **only** from inside the app

**Plan B (hardening):**
- [x] Restart arbiter: `BootReceiver` + `KeepAliveWorker` + `GuardHealthWorker` honor the flag
- [x] Setup wizard: sideload detection, restricted-settings deep-link, Play Protect guide
- [x] Site activation guides (AR/EN)
- [ ] 12 new features (challenge, triggers, night guard, accountability, week card,
      year heatmap, intent nudges, widget, biometric, deep focus, `.sawnbak` backup,
      parental mode) — scaffolded as dormant infrastructure; full implementation in
      a follow-up release
- [ ] Biometric unlock, encrypted backup, widget, heatmap — same as above

**Dormant infrastructure (prepared, disabled):**
- `ads/AdsController.kt` — no ads today; future: external ad networks for non-paying users only
- `entitlement/EntitlementService.kt` — FREE/PAID; today everything is free
- `update/UpdateChecker.kt` — disabled; no network calls

## Build

```
./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./gradlew lint assembleRelease
```

Target: `minSdk 26`, `targetSdk 36`, `versionName 2.0.0`, `versionCode 20`.

## Privacy

No account, no server, no telemetry. Everything stays on-device. See
[`docs/PRIVACY.md`](docs/PRIVACY.md) and [`docs/PLAY_COMPLIANCE.md`](docs/PLAY_COMPLIANCE.md).

## License

Code: see repository license. Cairo font: SIL Open Font License 1.1 (`app/licenses/Cairo-OFL.txt`).