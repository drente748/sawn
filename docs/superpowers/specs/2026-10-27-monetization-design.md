# Warden Monetization Design — Free now (ad-ready), Premium later, Commercial policies

**Date:** 2026-10-27 · **Decision by the product owner (updated later same day):**
1. The app ships **free, no ads integrated yet** — but the two ad surfaces are **structurally
   reserved** (`AdSlot.HOME_BANNER`, `AdSlot.STATS_NATIVE`) so wiring an SDK later is a
   drop-in with zero UI shift.
2. Warden is and stays a **commercial product in its policies** — the Onboarding screen and
   strings disclose the free tier, the future ad policy, and the future paid plan.
3. The design focus right now is a **modern, calm, distraction-free UI** (fixed brand
   palette, no dynamic color) + core features only + a loadable test APK.

## Intent and why

Warden is GPL-3.0 — any distributed derivative stays GPL and source-available. The chosen
commercial path is therefore **monetize inside the free, open app**: ads now, an optional
premium tier later. This keeps the app distributable legally while producing revenue.

## Goals

1. Integrate a Google **AdMob** ads layer (banner on Home, native ad on Stats, **no
   interstitials**) without degrading the core promise: blocking must never be interrupted,
   delayed, or conditioned on ad engagement.
2. Keep the privacy posture as strong as possible: ads are the single deliberate exception
   to "nothing leaves your device", and it must be **opt-out** in Settings, clearly disclosed
   on the setup screen, and reflected in a Play Data Safety form.
3. Prepare (structure only, no billing code yet) for a later premium tier that removes ads
   and adds premium features.

## Non-goals / anti-goals

- No interstitial/rewarded ads anywhere near the Intercept/block/pause screens — showing an
  ad at the moment of user weakness is a dark pattern and would destroy retention.
- No third-party analytics or crash SDKs beyond what AdMob itself requires (Firebase
  Analytics is NOT added; crash reporting stays absent for this phase).
- No billing/subscription code in this phase — only the entitlement seam (see below).
- Do not modify or touch the AntSeed VPN in any way (machine policy).

## Constraints (from the codebase)

- `network_security_config.xml` forbids cleartext; AdMob SDK uses HTTPS → compatible, but the
  SDK's own domains must be reachable (they are, over TLS).
- Play policy: an app holding VpnService + Accessibility + Ads is scrutinized. The listing
  must state plainly that ads exist and why (mitigated by opt-out + open source).
- `SPEC.md`/`README.md` currently claim "no ads" — both documents plus SECURITY.md must be
  updated when the ad layer ships (docs are part of this work, not an afterthought).
- Device builds on a 3.9 GB RAM machine: keep the dependency tree small (ads SDK adds
  ~1–2 MB dex; Play Services ads is provided at runtime, so build memory impact is minor).

## The design

### Phase A — Ads (this implementation round)

1. **Dependency:** `com.google.android.gms:play-services-ads:<current stable>` via the
   version catalog. Initialize lazily in `WardenApp` only when the user has not disabled ads.
2. **New setting** in `SettingsStore`: `adsEnabled` (default **true**, user can turn ads off
   in Settings → "Support Warden"). Ads disabled → no ad view is even inflated.
3. **Surface placement (exactly two):**
   - **Banner** at the bottom of `HomeScreen` only (not on Blocklist/Schedules/Stats/Settings;
     not on Intercept, PIN, or Setup screens).
   - **Native ad** at the end of the "Your wins" Stats list (feels like content, never in
     the way of a progress metric).
4. **Test unit IDs** in debug builds; real unit ID wired through `BuildConfig`/local
   properties so it is never committed.
5. **Consent:** UMP (Google's consent management) before first ad request for EEA users —
   Google's requirement, handled by the SDK's consent flow.
6. **Docs updates:** README (pricing row: "free with unobtrusive ads, opt-out in Settings"),
   SPEC (principles section gains the ads caveat with rationale), SECURITY.md (what the ad
   SDK can and cannot see, and that blocklists/domains remain un-touchable by ad code —
   ads run only inside the activity, never inside the VPN or accessibility services).

### Phase B — Premium tier (structure only, no billing code)

- Introduce `Entitlements` (an interface in `block/` or `billing/` package) with a single
  method `isPremium: Flow<Boolean>`; the default implementation returns `false` always.
- The two ad surfaces check `Entitlements.isPremium` first — when the future tier arrives,
  the check flips to real billing state and ads disappear for subscribers with zero UI
  changes elsewhere.
- Settings gains a hidden-cache "Premium" row placeholder (no purchase flow).

## What is left to the builder

- Exact AdMob SDK version and the Play-consent artifact versions.
- Compose implementation details of banner/native placement (compose-admob wrappers vs
  manual `AdView` in `AndroidView`).
- Strings and translations (app is currently English-only).

## Verification

- `assembleDebug` + `testDebugUnitTest` green (21 existing tests stay green).
- Ads never appear when `adsEnabled=false` (manual device check listed in DEVICE_TESTING.md).
- APK size delta reported after integration.
