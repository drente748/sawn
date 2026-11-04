# Warden

**A local, open-source website, app, adult-content and ad/tracker blocker for Android.** No
account, no telemetry, no ads. Every feature ships unlocked: no subscription, no paywall, and no
feature gated behind an upgrade. A community-owned alternative to closed blockers like AppBlock.

> Working name — "Warden" is a placeholder and easy to rename (see [Renaming](#renaming)).

---

## Why

Most Android blockers gate their most useful features (strict mode, multiple schedules,
website blocking) behind a subscription. Warden makes the whole feature set free and the
code auditable — important for an app that, by design, needs deep device permissions.

## How blocking works

Warden uses **two independent layers**, so there's no single point of bypass:

| Layer | Mechanism | Blocks |
|-------|-----------|--------|
| **Website filtering** | A local, on-device `VpnService` that intercepts DNS and blackholes queries for blocked domains. Non-blocked queries are forwarded to an upstream resolver. | Websites in **every** browser and app |
| **App blocking** | An `AccessibilityService` detects when a blocked app comes to the foreground and shows a full-screen block screen. | Native apps |

The VPN is **local-only** — DNS is filtered on-device, allowed lookups go to the resolver
already configured on your network (with a fallback only when Android exposes none), and no
query is logged. Other traffic is not proxied. Warden adds itself to the disallowed-apps list
so it never filters its own traffic. See [`SPEC.md`](SPEC.md) for the full architecture and
privacy model.

## Feature status

**Implemented:**
- [x] Website blocking via local DNS-filtering VPN (all browsers/apps, no root)
- [x] App blocking via accessibility service
- [x] **Mindful pause** (ScreenZen-style): breathe + intention prompt + wait, then earn
      time-boxed access — *"Continue for 1 / 5 / 15 min"*
- [x] **Per-app limits**: opens-per-day, daily time limit, cooldown between opens
- [x] Installed-app picker + per-item config editor (hard block vs pause + limits)
- [x] Full **schedule editor** (name, per-day toggles, start/end time pickers, overnight windows)
- [x] **Strict mode** + **device-admin uninstall protection**
- [x] **PIN lock** (PBKDF2, salted) gating settings, blocklist, schedules, app/feed
      management, and disabling
- [x] **Adult-content blocking** with an offline core set, SafeSearch forcing, browser URL
      fallback, and an optional manual refresh from maintained open lists
- [x] **Ad & tracker host blocking** via a bundled offline list (can be disabled)
- [x] **Focus streak** + screen-time dashboard ("track your wins")
- [x] **In-app feed blocking** — block just Reels / Shorts / Explore / For You / Spotlight
      while the rest of the app keeps working (accessibility node detection)
- [x] **Social media protection** — a per-app switch for Reddit, Facebook, Instagram, X,
      TikTok and YouTube that blocks detected NSFW screens, known adult communities and
      explicit searches, plus in-app pointers to each platform's own stronger filters
- [x] **Protection-health indicator + self-healing filter** — Warden automatically restarts
      the filter if Android kills it, and Home warns you if it still can't run, so you're never
      *silently* unblocked (the #1 complaint about AppBlock & ScreenZen)
- [x] **Never locks you out** — launcher, settings, dialer and Warden itself can't be blocked
- [x] **Hardened security** — local DNS filtering, PBKDF2-310k PIN + clock-tamper-resistant
      lockout, PIN-protected management screens, FLAG_SECURE + anti-tapjacking, no cleartext,
      no logging of domains. See [SECURITY.md](SECURITY.md)
- [x] **Quick Settings tile** — one-tap blocking toggle from the shade (respects the PIN and
      strict-mode locks)
- [x] **Focus sessions** — a Pomodoro-style timer that forces blocking on for a set duration
      (optional strict mode with no early exit; a session started while strict mode is on is
      automatically strict), independent of the master switch
- [x] **Notification blocking** — silence notifications from blocked apps while blocking is active
- [x] **Local protection journal** - a per-day count of refused attempts with a seven-day chart on Home (a counter only: no site or app names, nothing leaves the device)
- [x] **One-tap protection presets** - light / balanced / maximum profiles that set every switch at once
- [x] **Protection checkup** - a weighted readiness score across every layer, with a direct fix path for each missing permission
- [x] **Survives a recents swipe** - after the task is removed the filter re-posts its notification and a keep-alive worker re-arms it, not only after a reboot
- [x] **DNS-over-HTTPS blocking** — optionally sinkhole encrypted-DNS resolvers to close the browser bypass
- [x] **Always-on or schedule-only** modes, a first-run **setup** screen, app icons throughout,
      and **data-loss-safe upgrades** (real Room migrations)
- [x] Master on/off with VPN consent flow, restart-after-reboot

**Roadmap (see `SPEC.md`):**
- [ ] Conditions beyond time: Wi-Fi network, location, launch-count
- [ ] Tune in-app feed detection signals against current app versions (needs real devices —
      see [DEVICE_TESTING.md](DEVICE_TESTING.md))
- [ ] Per-item schedule assignment
- [ ] Import/export & optional end-to-end encrypted sync
- [ ] Scheduled, signed, diff-based list updates with rollback protection

### How it compares
| | AppBlock | ScreenZen | **Warden** |
|---|---|---|---|
| Website blocking (all apps) | ✓ | limited | ✓ (DNS VPN) |
| App blocking | ✓ | ✓ | ✓ |
| Mindful pause / intention | — | ✓ | ✓ |
| In-app feed blocking (Reels/Shorts) | — | ✓ | ✓ |
| Open/time/cooldown limits | ✓ | ✓ | ✓ |
| Schedules | ✓ | ✓ | ✓ |
| Strict mode + uninstall lock | ✓ | ✓ | ✓ |
| Focus / Pomodoro sessions | — | ✓ | ✓ |
| Notification blocking | ✓ | — | ✓ |
| Encrypted-DNS (DoH) blocking | — | — | ✓ |
| Adult-content blocking | ✓ | — | ✓ |
| Ad & tracker host blocking | partial | — | ✓ |
| Warns when enforcement stops | — | — | ✓ |
| Can't accidentally lock you out | — | — | ✓ |
| Open-source & auditable | — | — | ✓ |
| PIN lock | ✓ | — | ✓ |
| Streaks | — | ✓ | ✓ |
| Price | freemium | free | **free + open-source** |

## Building

You'll need the **JDK 17** and the **Android SDK** (both easiest via Android Studio).

```bash
# 1. Install Android Studio (bundles JDK + SDK):
#    https://developer.android.com/studio
# 2. Open this folder in Android Studio — it will sync Gradle and write local.properties.
# 3. Or from the command line, once ANDROID_HOME points at your SDK:
./gradlew assembleDebug
./gradlew installDebug   # to a connected device / emulator
```

### Release signing

Release credentials must not be committed. Copy the four `WARDEN_*` entries from
`local.properties.example` into the ignored `local.properties`, or provide the same names as
environment variables, then run `./gradlew assembleRelease`. Keep the existing keystore for
upgrades: changing it creates an APK Android will not install over the previous release.

### Required permissions (all granted by the user, none abused)
- **VPN consent** — for the local DNS filter (system dialog on first enable).
- **Accessibility** — to detect the foreground app and inspect bounded on-screen labels for
  feed and browser-URL blocking. Inspection is in memory only; nothing is stored or sent.
- **Usage access** — for the screen-time dashboard.
- **Notifications** — for the ongoing "blocking active" notice (required for a foreground service).

### Keeping protection on
Android may stop background services to save power. Warden contains its own crashes and
restarts the filter when Home is opened, but two system settings make it survive reliably
(see **Setup → Keep protection alive**):
- **Always-on VPN → Warden on.** Do **not** enable "Block connections without VPN" —
  Warden only filters DNS, and lockdown would cut all connectivity whenever it restarts.
- **Battery optimization → No restrictions** for Warden, especially on Xiaomi, Samsung,
  Oppo/Realme and Vivo devices that kill background apps aggressively.
- **Private DNS → Off.** Strict Private DNS (DoT) bypasses the DNS filter completely, and
  "Automatic" can still use DoT on carriers that advertise it.

### Installing a sideloaded build (Android 13+)
Android blocks special access (Accessibility, VPN) for apps installed outside an app store.
Warden cannot, and will not, bypass this. Two legitimate routes:
1. **Sideload**: after installing, open **App info → ⋮ → Allow restricted settings**, then
   enable Accessibility in system settings. Play Protect can stay **on** — it does not need
   to be disabled to grant this. If Play Protect flags the unsigned/unrecognized build, use
   route 2 rather than turning protection off.
2. **Play distribution** (internal testing, closed testing, or production): installs from
   Play are not subject to the restricted-settings gate at all.

### Social Guard scope
Social Guard blocks **detected adult/NSFW screens** inside the selected social apps; the rest
of each app keeps working. To block an entire app or site, add it in **Blocklist** instead.

## Renaming

Everything keys off `com.warden.blocker`. To rebrand: change `applicationId`/`namespace` in
[`app/build.gradle.kts`](app/build.gradle.kts), the package folders, `rootProject.name` in
[`settings.gradle.kts`](settings.gradle.kts), and `app_name` in `res/values/strings.xml`.
Android Studio's *Refactor → Rename* handles the package move cleanly.

## License

[GPL-3.0](LICENSE) — Warden is free software; derivatives must stay free too.

**Before any closed or paid distribution:** GPL-3.0 obliges you to hand recipients the source of the
same build and keeps derivatives under the same license, so a proprietary release requires the
copyright holder to relicense first (replace `LICENSE`, then update this section). Third-party
notices are required either way: TensorFlow Lite Task Vision (Apache-2.0) and IBM Plex Sans Arabic
(OFL, [`app/licenses/`](app/licenses)) — check the provenance of any blocklist you add too.

The hooks in [`AdSlots.kt`](app/src/main/java/com/warden/blocker/ui/ads/AdSlots.kt) and
[`Entitlements.kt`](app/src/main/java/com/warden/blocker/billing/Entitlements.kt) are inert in this
build: no ad SDK, no billing library, and `isPremium` is permanently false, so "no ads" describes
the shipped APK rather than a roadmap promise.

## Contributing

Issues and PRs welcome. Please read `SPEC.md` first for the architecture and the privacy
constraints that any change must preserve.
