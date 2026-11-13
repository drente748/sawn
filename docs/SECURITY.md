# Security & Privacy

Sawn holds four of the most powerful — and most-abused — permissions on Android
(VpnService, AccessibilityService, DeviceAdmin, NotificationListener). That combination is
exactly the profile malware uses, so Sawn is built to *prove* it is benign and local-only.
This document is the threat model and the measures in place.

## Core principle: local-first, least privilege

- **No Sawn backend or telemetry.** No accounts, analytics, ads, or tracking SDKs. The VPN
  is local; it does not proxy or forward browsing traffic to Sawn or anyone else.
- The only outbound requests are explicit, user-triggered HTTPS downloads of public adult-
  domain lists from their GitHub sources. They send no account, device, or browsing data.
  There is no automatic background list download.
- Every permission's use is justified below.

## Threat model

| Adversary | Goal | Mitigation |
|-----------|------|------------|
| Impulsive self (the user) | Turn blocking off in a weak moment | PIN gate, locks that ask for the PIN, device-admin uninstall lock, focus sessions |
| Thief / shoulder-surfer | Read the PIN, disable protection | PBKDF2 (310k) + salt, monotonic lockout, FLAG_SECURE, tapjacking guards |
| Malicious app on device | Trick user into disabling; capture PIN via overlay | `filterTouchesWhenObscured` + `setHideOverlayWindows` on intercept/PIN |
| Attacker with adb / backup | Extract PIN hash or blocklist | `allowBackup=false`; PIN stored only as salted hash |
| Network attacker | MITM list downloads | HTTPS with platform certificate validation; no secrets sent; cleartext forbidden |
| Hostile packets in the tunnel | Crash/exploit the packet loop | Defensive bounds-checked parsing; loop failure tears down health state safely |

## What each permission does — and doesn't

- **VpnService** — a *local* tunnel that filters DNS on-device. Blocked domains are answered
  with a dead-end; **allowed lookups are forwarded to the device's own DNS resolver**
  (captured from the active network). A public fallback resolver is used only when Android
  exposes no usable system resolver. Sawn never reads, stores, or transmits your browsing.
  Queried domains are **never logged**.
- **AccessibilityService** — reads the foreground package name, and nothing else of the
  screen: no view-ids, no labels, no screenshots.
  In the **normal protection mode** it reads the **address bar of a known browser**
  (Chrome, Firefox, Samsung Internet, Edge, Brave, DuckDuckGo, Opera and a long tail of
  others) to block the sites the user listed. The reading is in-memory only, it is never
  stored or logged, the walk is capped at 80 nodes, and it never enters a WebView — so it
  cannot read page content.
  Critical apps (launcher, settings, dialer, phone, Sawn itself) are never intercepted, so
  you can't be locked out of your own device.
- **DeviceAdmin** — declares **no enforcement policies**; active-admin status alone provides
  uninstall protection. Disable it from Sawn Settings (behind your PIN) or from Android's
  system Settings, which remains the emergency exit. Sawn never traps you.
- **NotificationListener** — opt-in; reads only the posting package name to cancel
  notifications from blocked apps. Notification **content is never read, stored, or sent**.

## Hardening measures (code)

- **PIN**: `PBKDF2WithHmacSHA256`, 310,000 iterations, per-PIN `SecureRandom` salt; stored as
  hash only. Compounding lockout after 5 failures (30s → 1h) uses elapsed-realtime within a
  boot, so advancing the wall clock does not shorten it. Constant-time comparison. Settings,
  blocklist, app management, and disable paths are PIN-gated.
- **Screen protection**: `FLAG_SECURE` on the PIN pad (release builds) blocks
  screenshots/recording; `filterTouchesWhenObscured` + `setHideOverlayWindows` are applied
  to the *dialog window itself* and the host activity (the dialog's context is a
  ContextThemeWrapper, so the old activity-cast silently no-op'd inside dialogs).
- **Network**: `network_security_config.xml` with `cleartextTrafficPermitted=false` and
  `usesCleartextTraffic=false`. List refresh uses HTTPS only, rejects non-2xx responses,
  validates every hostname, limits response size, and keeps the previous list on failure.
- **Backups**: `android:allowBackup="false"` — the PIN hash and blocklist can't be pulled via
  `adb backup` or cloud backup.
- **Exported surface**: only the launcher activity is exported. The intercept activity is
  `exported=false`; the VPN/accessibility/tile/notification/device-admin services are
  system-bound and protected by their respective `BIND_*` permissions.
- **Release builds**: R8 shrinking and log stripping are on by default from v2.2.0
  (`-PSAWN_MINIFY_RELEASE=false` only for a diagnostic unminified build). The manifest
  keep rules cover every manifest-referenced component; if a release build fails at
  runtime with a missing class, add a targeted keep rule rather than disabling minify.
  The uncaught crash handler writes one bounded, private trace (`last-crash.txt`) locally;
  review and erase it before sharing diagnostic files.
- **Packet handling**: `DnsPacket` validates received IP/UDP lengths, the DNS question and
  each hostname label before reading and drops malformed packets. DNS forwarding is
  concurrency-bounded and shared tunnel writes are synchronized. If the packet loop stops,
  Sawn clears its health flag and stops the service instead of appearing protected while
  filtering has stopped.

## Known limitations (honest)

- **The normal protection mode has real gaps.** Without the DNS filter, the browser engine can
  only act on what a browser *shows in its address bar*. That means:
  - A page opened **inside another app** (a WebView — many apps open links that way) is
    **not** blocked. The engine never enters a WebView, on purpose: it cannot block there
    anyway, and reading page content would mean reading far more than the address bar.
  - **SafeSearch** and **DoH blocking** are DNS features. They do not work in the normal
    mode; they need the advanced mode.
  - A browser that does not expose its address bar to accessibility services is not covered.
    The list of known browsers is long (see `BrowserRegistry`), but a browser that ships
    tomorrow is not in it until it is added.
  - The advanced mode has **none** of these gaps — that is what it is for. The home-screen
    badge says which mode is actually running, so a user is never misled about it.
- **The browser engine is a race the user can lose.** It reacts to a window event and
  reads the bar a moment later. A page that loads and redirects within that window may not
  be caught. The advanced mode (DNS) does not have this gap: it acts before any page is drawn.
- **Accessibility is a powerful permission.** In the normal mode it is the *only* engine, so
  the app's protection depends on a permission that can also be abused by malware. This is
  the trade the normal mode makes, and the reason the advanced mode is the default for
  upgraded installs.
- **The DNS filter stays the user's choice, even under a lock.** A time lock and the panic
  lock keep the *app-level* blocking on — the app you locked stays blocked — but they do not
  force the DNS layer on, and switching it off is allowed (with a warning). The panic lock
  is the one exception: it switches the advanced mode on when the VPN permission is already
  granted (it never prompts for it), and the user stays free to switch it off afterwards. So the honest promise of a lock is *"this app stays blocked"*, not *"every site on
  the device stays blocked"*.
- **DNS-over-HTTPS/TLS** can bypass the DNS sinkhole. Sawn optionally sinkholes known DoH
  resolver hostnames (Settings → Block DNS-over-HTTPS) to close the common case, but clients
  with hardcoded resolver IPs can still slip through.
- **IPv6 DNS**: a synthetic IPv6 resolver route is captured to stop the OS from bypassing the
  filter, but only IPv4 DNS packets are terminated inside the tunnel. IPv6-only resolvers may
  therefore fail rather than resolve.
- **Clock changes**: `SettingsStore.trustedNow()` keeps a monotonic high-water mark, so
  rolling the clock back cannot make a focus session or a lock end sooner. A rollback can
  make a focus session last longer (the VPN keeps blocking) until the wall clock catches up;
  moving the clock forward can end sessions early. PIN lockout additionally uses elapsed
  realtime during the current boot; after a reboot it retains a wall-clock remainder because
  Android exposes no secure monotonic clock across boots.
- **Ad/tracker filtering** is a bundled, curated host list. It cannot remove sponsored content
  served by the same first-party domain as the page, and there is no automatic remote ad-list
  subscription.
- **No screen reading.** Sawn reads the foreground package name, and the browser's address
  bar in the normal mode. Nothing else on the screen is read — no labels, no screenshots.
- **Adult list refresh** is manual and trusts the maintained upstream sources over HTTPS. It
  validates formats and sizes but does not yet verify a signed manifest or prevent rollback.
- **Update channel**: the manifest is served from GitHub Pages and the APK from GitHub Releases.
  SHA-256 detects a corrupted or substituted file. Sawn then verifies the APK **signature
  itself, before the system installer is ever opened**: the downloaded file's signing
  certificates are compared against the signing lineage of the *installed* copy of the app
  (the whole history, so a planned key rotation keeps working). An unsigned file, or one
  signed by another key, is refused with "فشل التحقق من توقيع التحديث — تم إلغاء التثبيت" and
  never reaches the installer. The anchor is the installed app's own certificate rather than a
  hash compiled into the app, because a hash inside the APK is exactly what an attacker
  repackages. What SHA-256 is *not* is an independent signature: if both hosting surfaces
  were compromised at once, a manifest and APK that agree with each other would still pass.
  The release key therefore stays out of GitHub and in Actions secrets only. The check is
  manual and the whole channel is inert until `SAWN_UPDATE_MANIFEST_URL` is baked into a
  build; there is no automatic check on app start.
- **On some devices a force-stopped accessibility service cannot be revived by the app.** On
  Pixel devices, `am force-stop` clears the `accessibility_enabled` setting itself, so the
  system no longer considers the service enabled and no app — Sawn included — can bring it
  back programmatically; the user has to re-enable it in Settings → Accessibility. This is a
  limitation of Android, not a bug in the app. What Sawn can do, it does: it notices the loss
  within minutes (every 3 minutes while a time lock or the panic lock holds, every 15 minutes
  otherwise), asks the system to rebind the service a bounded number of times with
  exponential backoff, and posts an ongoing notice whose tap opens the accessibility settings
  directly.

## Play Store submission checklist

Uploading the AAB is not the whole job — these declarations are checked against the binary, and a
mismatch between them and this file is the usual cause of a policy rejection.

| What must be declared | What this build actually does |
|---|---|
| **AccessibilityService use** (`isAccessibilityTool = false`, plus a purpose statement and a prominent in-app disclosure) | Reads the foreground package name to enforce the user's own block rules, and the address bar of a known browser in the normal mode. It takes no screenshot and reads no on-screen text. It issues BACK/HOME inside a blocked app to leave the blocked page. That is the highest-risk item under the rule against autonomously initiating actions in other apps, so state it plainly instead of implying the service is a disability aid. |
| **`specialUse` foreground service** | Local DNS filter only; the `<property>` subtype string in the manifest is the first thing a reviewer reads. |
| **Data safety form** | Nothing collected, nothing shared, no advertising or analytics SDK. Nothing is read from the screen at all — say so in the privacy policy too. |
| **Sensitive permissions / access** | `PACKAGE_USAGE_STATS` (screen time), notification access (suppressing notifications of blocked apps), device admin (uninstall lock), VPN. Each needs a one-line user-facing justification that matches this document. |
| **Privacy policy URL** | Must state on-device processing, no backend, and that adult-list downloads are user-triggered. |
| **Backup and transfer** | Handled in code: `allowBackup="false"` **and** `dataExtractionRules` closing both cloud backup and device-to-device transfer, so a PIN hash cannot travel to a new phone. |
| **API level** | Handled: `targetSdk 36`, the Play requirement for updated apps after 31 Aug 2026. |

Back the release keystore up off-machine before the first upload. Losing it means losing the ability
to ship updates to the same listing forever.

## Reporting a vulnerability

Please open a **private** security advisory on the GitHub repository (Security → Report a
vulnerability) rather than a public issue. We aim to acknowledge within a few days.
