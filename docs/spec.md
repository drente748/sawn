# Sawn — Technical Spec

## Goal

An Android app that does everything
[AppBlock](https://play.google.com/store/apps/details?id=cz.mobilesoft.appblock) does — block
apps and websites, with locks, focus sessions and PIN protection, and usage
stats — and more, with a stronger privacy posture (local-only, no accounts, auditable).

## Non-negotiable principles

1. **Local-first.** No Sawn servers, accounts, analytics, or ad SDKs. The VPN never proxies
   browsing traffic off-device; it only filters forwarded DNS. The sole network exception is
   a user-triggered HTTPS refresh of public adult-domain lists, with no identifiers sent.
   Accessibility reads the foreground package name, and the browser's address bar in the
   normal mode. It never reads on-screen content: no labels, no screenshots, no image
   classification. It stores and transmits nothing.
2. **Auditable.** Every permission's use is visible in code and documented in
   [SECURITY.md](SECURITY.md). This matters because a blocker asks for VPN + accessibility,
   exactly the permissions malware wants.
3. **Hard to bypass, easy to leave.** A lock makes impulsive disabling hard, but the
   user is always ultimately in control (no dark patterns, clean uninstall when not in strict
   mode).

## Architecture

```
                        ┌────────────────────┐
   Compose UI  ───────► │   SawnViewModel   │
   (screens)            └─────────┬──────────┘
                                  │
                        ┌─────────▼──────────┐
                        │   AppContainer      │  (service locator; swap for Hilt later)
                        │  repo / settings /  │
                        │     blockEngine     │
                        └───┬────────────┬────┘
                            │            │
                 ┌──────────▼──┐   ┌─────▼────────────┐
                 │ BlockRepo   │   │  SettingsStore   │
                 │ (Room DAO)  │   │  (DataStore)     │
                 └─────────────┘   └──────────────────┘
                            ▲
        ┌───────────────────┼───────────────────────┐
        │                   │                        │
┌───────▼────────┐  ┌───────▼───────────┐   ┌────────▼─────────┐
│ SawnVpnService│  │ AppBlockA11yService│   │  BootReceiver    │
│ DNS sinkhole    │  │ foreground-app     │   │  re-arm on boot  │
│ (websites)      │  │ block (apps)       │   └──────────────────┘
│ OPTIONAL engine │  │ + browser engine   │
└─────────────────┘  └────────────────────┘
```

### The engines

Blocking is done by **three engines**, and the user chooses which of them run:

| Engine | Mechanism | Covers | Cost |
|---|---|---|---|
| `DNS` | `SawnVpnService` — a local DNS sinkhole | Every lookup, in every app | Battery + the VPN permission |
| `BROWSER` | `AppBlockAccessibilityService` — reads the address bar of a known browser | Browsers only | Accessibility only |
| `APP` | `AppBlockAccessibilityService` — blocks a foreground package | Native apps | Accessibility only |

`BlockCoordinator` (in `:core:domain`, pure Kotlin) is the single place that answers
"what is protecting me right now, and is this host blocked?". It exposes:

- `getActiveEngines(): Set<BlockingEngineType>` — the engines that are live.
- `getProtectionLevel(): ProtectionLevel` — `Full` (DNS live), `BrowserOnly` (accessibility
  only), `None` (nothing live), `Paused` (protection deliberately off).
- `evaluateDomain(host)` — the decision for one host, with the engine that owns it.
- `reportBlock(decision, source)` — the counter for an enforced block.

The rules live in `ProtectionPolicy` and are unit-tested without a device. The Android
side only supplies facts (`SawnBlockCoordinatorEnv`): which engines are running, and which
domains the blocklist holds.

**Two named modes, not a toggle.** `ProtectionMode.FULL` (DNS + accessibility) — *advanced* —
and `ProtectionMode.LIGHT` (accessibility only) — *normal* — are the settings the user picks.
The normal mode never starts the VPN, so the connection permission is never requested at all.
The time lock keeps the app-level blocking on but leaves the DNS layer to the user; the panic
lock switches the advanced mode on when the VPN permission is already granted.

### Key files
- `vpn/SawnVpnService.kt` — establishes a local TUN, routes only DNS into it, and either
  **sinkholes** (A → 0.0.0.0) a blocked domain or **forwards** the query to the device's own
  resolver and NATs the reply back. A public resolver is used only when Android exposes no
  system resolver. Adds itself to disallowed apps and tears down health state on failure.
- `vpn/DnsPacket.kt` — minimal IPv4/UDP/DNS parsing + response building. Deliberately handles
  the common case; TCP-DNS/IPv6/EDNS remain follow-ups.
- `accessibility/AppBlockAccessibilityService.kt` — on `TYPE_WINDOW_STATE_CHANGED`, checks the
  package against the engine and launches the intercept screen. `BrowserUrlMonitor` also scans bounded
  browser nodes for adult hosts that bypassed DNS.
- `accessibility/BrowserBlockingEngine.kt` — the browser engine: reads the address bar of a
  known browser and blocks what the blocklist forbids. A **focused** bar means the user is
  typing, so the reading is deferred 300 ms and re-read; an **unfocused** bar means the
  browser already navigated there, so the block happens at once.
- `accessibility/BrowserHostExtractor.kt` (in `:core:domain`) — the bounded tree walk: at most
  80 nodes, matched by the last segment of the view id, and it never enters a WebView.
- `accessibility/BlockOverlayController.kt` — shows the block notice over the browser with a
  `TYPE_ACCESSIBILITY_OVERLAY` window. No `SYSTEM_ALERT_WINDOW` permission is needed, because
  an overlay drawn by the accessibility service belongs to it.
- `feature/SocialPlatform.kt` — supported social packages, per-platform coverage copy, and
  built-in filter guidance. Settings persist independently per platform.
- `block/BlockEngine.kt` — the single source of truth for
  "is blocking active now, and for what?". Combines the master switch, active
  per-item domains, adult/SafeSearch settings, optional DoH blocking, and the
  bundled ad/tracker list.
- `block/DomainMatcher.kt` — exact/parent-domain suffix matching in time proportional to the
  host's label count, not the size of the blocklist, plus hostname validation.
- `data/` — Room (`BlockedItem`, `AccessGrant`, `ProtectedDayEntity`) + DataStore
  settings.
- `security/PinHasher.kt` — PBKDF2-HMAC-SHA256, salted; PIN never stored in plaintext.
- `security/PinLockoutPolicy.kt` — pure elapsed/wall-clock lockout policy.
- `security/AdultListFetcher.kt` — validated manual HTTPS refresh that preserves the last
  valid list on failure.

### Blocking decision
`isBlockingActiveNow = focusEndsAt > trustedNow || masterEnabled` (plus the locks, which force it).
The VPN calls `blockedDomains()`; the accessibility service calls `decideForPackage(pkg)`;
the browser engine calls `coordinator.evaluateDomain(host)`.
A lock asks for the PIN when it is switched on, and switches the uninstall protection on
with it; a strict focus session keeps its own extra friction.

## ~~Schedules~~ — removed from the product
The schedules (and `ScheduleEvaluator`) are gone; the master switch is the only gate left.

## Data model migrations
Room is at `version = 5` with `exportSchema = true`; the generated schemas live in
`core/data/schemas/` and are checked into git, so a migration test can validate against
the *real* schema rather than a hand-written copy.
`MIGRATION_1_2` preserves existing blocklists while adding pause/limit fields and access
grants. `MIGRATION_4_5` adds the three per-engine flags to `protected_days`, so a day
counts as protected when *any* engine was enforcing it; the days recorded before it are
re-labelled as DNS-protected, because the DNS filter was the only engine back then.
Destructive fallback is limited to downgrades in development; real upgrades migrate.

## Roadmap detail (parity + beyond)

### Parity with AppBlock
- **Full schedule editor**: removed with the schedules.
- **Conditions engine**: extend `BlockEngine` with pluggable `Condition`s — time (done),
  Wi-Fi SSID, location geofence, app launch-count, daily usage-limit.
- **Lock enforcement** (implemented): while a lock is on, lock the in-app master
  switch, the Quick Settings tile, early focus exit, and in-app device-admin disable; require
  a PIN. Android's own device-admin deactivation remains the documented
  emergency exit.
- **PIN lock** (implemented): gates settings, blocklist, app management, and
  disable paths; includes a compounding clock-tamper-resistant lockout.
- **Adult-content and ad/tracker blocking** (implemented): offline curated host sets, optional
  Porn Guard/SafeSearch/browser fallback, and a manual validated adult-list refresh.
- **Notification blocking**: `NotificationListenerService` to suppress notifications from
  blocked apps during active windows.
- **Usage stats**: expand `StatsScreen` with per-day history, charts, and "unlock attempts".
- **Quick block**: a Quick Settings tile + a home-screen "start focus session" (Pomodoro).

### Beyond AppBlock
- Encrypted local backup + optional **E2E-encrypted sync** across your own devices (no server
  can read the blocklist).
- Community-shared, signed blocklists (opt-in), like uBlock filter lists.
- On-device "why did I open this?" journaling to build awareness, fully local.

## Testing priorities
1. `EmergencyLockdown` / `LockdownPolicy` — the lock invariants (no cancel, no shorten, clock-proof).
2. `DnsPacket` — parse/build round-trips against captured DNS packets.
3. `BlockingPolicy` / `AccessController` — decision tables across the master switch,
   focus, grants, limits, and lock combinations.
4. `DomainMatcher` / `PinLockoutPolicy` / `AdultListFetcher` parsing — suffix boundaries,
   malformed hosts, clock/boot transitions, and hostile response shapes.
5. `EmergencyLockdown` / `LockdownPolicy` — the lock invariants: it cannot be cancelled, it
   cannot be shortened, and its deadline survives a reboot and a changed system clock.
6. Instrumented: VPN establish + sinkhole a known domain, network changes, revocation, and
   process death on an emulator or device.

## Known limitations
- DNS filtering covers UDP/IPv4. Apps using DNS-over-HTTPS/TLS (e.g. Chrome's Secure DNS)
  can bypass a DNS sinkhole; known DoH hosts can be blocked optionally, with the browser URL
  fallback covering only visible browser text.
- The bundled ad/tracker list does not remove first-party sponsored content, and updates are
  manual.
- Social media protection sees accessibility text and view identifiers, not pixels. Unlabelled
  photos/videos, private messages used as a bypass, and app-version changes to view ids can
  escape detection; users should also enable each platform's built-in sensitive-content
  filters. It covers native apps, not social sites inside a browser.
- A lock enforces the in-app disable paths, tile, focus cancellation, and in-app
  device-admin disable. Android still allows deactivating device admin from system Settings;
  that is the deliberate emergency exit, not a bypass inside Sawn.
