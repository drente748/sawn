# Security & Privacy

Warden holds four of the most powerful — and most-abused — permissions on Android
(VpnService, AccessibilityService, DeviceAdmin, NotificationListener). That combination is
exactly the profile malware uses, so Warden is built to *prove* it is benign and local-only.
This document is the threat model and the measures in place.

## Core principle: local-first, least privilege

- **No Warden backend or telemetry.** No accounts, analytics, ads, or tracking SDKs. The VPN
  is local; it does not proxy or forward browsing traffic to Warden or anyone else.
- The only outbound requests are explicit, user-triggered HTTPS downloads of public adult-
  domain lists from their GitHub sources. They send no account, device, or browsing data.
  There is no automatic background list download.
- Every permission's use is visible in this open-source code and justified below.

## Threat model

| Adversary | Goal | Mitigation |
|-----------|------|------------|
| Impulsive self (the user) | Turn blocking off in a weak moment | PIN gate, strict mode, device-admin uninstall lock, focus sessions |
| Thief / shoulder-surfer | Read the PIN, disable protection | PBKDF2 (310k) + salt, monotonic lockout, FLAG_SECURE, tapjacking guards |
| Malicious app on device | Trick user into disabling; capture PIN via overlay | `filterTouchesWhenObscured` + `setHideOverlayWindows` on intercept/PIN |
| Attacker with adb / backup | Extract PIN hash or blocklist | `allowBackup=false`; PIN stored only as salted hash |
| Network attacker | MITM list downloads | HTTPS with platform certificate validation; no secrets sent; cleartext forbidden |
| Hostile packets in the tunnel | Crash/exploit the packet loop | Defensive bounds-checked parsing; loop failure tears down health state safely |

## What each permission does — and doesn't

- **VpnService** — a *local* tunnel that filters DNS on-device. Blocked domains are answered
  with a dead-end; **allowed lookups are forwarded to the device's own DNS resolver**
  (captured from the active network). A public fallback resolver is used only when Android
  exposes no usable system resolver. Warden never reads, stores, or transmits your browsing.
  Queried domains are **never logged**.
- **AccessibilityService** — reads the foreground package name (to block apps) and, for
  in-app feed and social-media protection, scans on-screen view-ids/labels of the current app.
  Social detection additionally checks whether a search field contains an explicit term. It
  **never logs, stores, or transmits screen content or queries**, makes an in-memory
  block/allow decision, and discards the text. No screenshots are taken.
  Critical apps (launcher, settings, dialer, phone, Warden itself) are never intercepted, so
  you can't be locked out of your own device.
- **DeviceAdmin** — declares **no enforcement policies**; active-admin status alone provides
  uninstall protection. Disable it from Warden Settings (behind your PIN; strict mode blocks
  the in-app path until strict mode is off) or from Android's system Settings, which remains
  the emergency exit. Warden never traps you.
- **NotificationListener** — opt-in; reads only the posting package name to cancel
  notifications from blocked apps. Notification **content is never read, stored, or sent**.

## Hardening measures (code)

- **PIN**: `PBKDF2WithHmacSHA256`, 310,000 iterations, per-PIN `SecureRandom` salt; stored as
  hash only. Compounding lockout after 5 failures (30s → 1h) uses elapsed-realtime within a
  boot, so advancing the wall clock does not shorten it. Constant-time comparison. Settings,
  blocklist, schedules, app/feed management, and disable paths are PIN-gated.
- **Screen protection**: `FLAG_SECURE` on the PIN pad (release builds) blocks
  screenshots/recording; `filterTouchesWhenObscured` + `setHideOverlayWindows` on the PIN and
  the intercept screen defeat tapjacking overlays.
- **Network**: `network_security_config.xml` with `cleartextTrafficPermitted=false` and
  `usesCleartextTraffic=false`. List refresh uses HTTPS only, rejects non-2xx responses,
  validates every hostname, limits response size, and keeps the previous list on failure.
- **Backups**: `android:allowBackup="false"` — the PIN hash and blocklist can't be pulled via
  `adb backup` or cloud backup.
- **Exported surface**: only the launcher activity is exported. The intercept activity is
  `exported=false`; the VPN/accessibility/tile/notification/device-admin services are
  system-bound and protected by their respective `BIND_*` permissions.
- **Release builds**: R8 shrinks + obfuscates and **strips all `android.util.Log`** calls, so
  no diagnostic logging can leak in production.
- **Packet handling**: `DnsPacket` bounds-checks every offset before reading and drops
  malformed packets. DNS forwarding is concurrency-bounded and shared tunnel writes are
  synchronized. If the packet loop stops, Warden clears its health flag and stops the
  service instead of appearing protected while filtering has stopped.

## Known limitations (honest)

- **DNS-over-HTTPS/TLS** can bypass the DNS sinkhole. Warden optionally sinkholes known DoH
  resolver hostnames (Settings → Block DNS-over-HTTPS) to close the common case, but clients
  with hardcoded resolver IPs can still slip through.
- **IPv6 DNS**: a synthetic IPv6 resolver route is captured to stop the OS from bypassing the
  filter, but only IPv4 DNS packets are terminated inside the tunnel. IPv6-only resolvers may
  therefore fail rather than resolve.
- **Clock changes**: `SettingsStore.trustedNow()` keeps a monotonic high-water mark, so
  rolling the clock back cannot make schedules or focus sessions end sooner. A rollback can
  make a focus session last longer (the VPN keeps blocking) until the wall clock catches up;
  moving the clock forward can end sessions early. PIN lockout additionally uses elapsed
  realtime during the current boot; after a reboot it retains a wall-clock remainder because
  Android exposes no secure monotonic clock across boots.
- **Ad/tracker filtering** is a bundled, curated host list. It cannot remove sponsored content
  served by the same first-party domain as the page, and there is no automatic remote ad-list
  subscription.
- **Social-media protection** is text-and-label based, not image classification. It cannot see
  unlabelled photos/videos, and app updates may rename the view ids and warning text it relies
  on. It covers native apps only; social sites opened in a browser are outside this layer.
- **Adult list refresh** is manual and trusts the maintained upstream sources over HTTPS. It
  validates formats and sizes but does not yet verify a signed manifest or prevent rollback.
- **Strict mode** locks Warden's own disable paths (switch, tile, early focus exit, in-app
  admin disable), not Android's system Settings. Deactivating device admin or force-stopping
  the app from the OS remains possible — the deliberate emergency exit.
- The PIN is not yet bound to the hardware Keystore/StrongBox — planned as defense-in-depth
  against offline extraction on rooted devices (the salted 310k-PBKDF2 hash + no-backup is the
  current protection).

## Reporting a vulnerability

Please open a **private** security advisory on the GitHub repository (Security → Report a
vulnerability) rather than a public issue. We aim to acknowledge within a few days.
