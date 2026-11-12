# Real-device testing checklist

Most of Sawn is verified on an emulator (see commit history), but a few things can only be
confirmed on a physical phone with real apps and a real Quick Settings panel. Work through
these once on your device.

## Setup
1. `./gradlew installDebug` with the phone connected (see [setup.md](setup.md)).
2. Grant, in-app: **Accessibility** (Settings → Accessibility → Sawn), **Usage access**,
   **Notifications**, and turn blocking **On** once (accept the VPN consent dialog).

## Must-verify on hardware

### 1. ~~In-app feed detection~~ — removed from the product
The per-feed blocking (Reels, Shorts, TikTok feed…) and the on-screen content inspection
that went with it are no longer part of Sawn. The service no longer reads screen text
and takes no screenshots, so there is nothing to tune here: the app blocks whole apps
and whole sites, by name. The section number is kept so the rest of the plan keeps its
references.

### 2. Quick Settings tile
- Pull down the shade → edit (pencil) → drag the **Sawn** tile into the active set.
- Tap it. ✅ Blocking toggles on/off (tile shows active/inactive).
- With a PIN set, tapping to turn **off** should open the app for the PIN.
- With strict mode on and protection on, tapping **off** should open the app and must not
  disable protection — the tile cannot bypass strict mode.
  *(Not verifiable on a headless emulator — `cmd statusbar click-tile` doesn't invoke it.)*

### 3. DNS-over-HTTPS reality check
- Block a site (e.g. `example.com`), turn blocking on, open it in **Chrome**.
- ✅ It should fail to load with Chrome's normal DNS.
- In **Settings → Block DNS-over-HTTPS**, enable the option, restart blocking, and retry with
  Chrome "Secure DNS" set to a known provider such as Cloudflare or Google.
- ✅ The known DoH provider should be blocked too. ⚠️ A custom/hardcoded resolver IP can still
  bypass DNS filtering; note what you observe.

### 4. Device-admin uninstall protection
- Settings → turn on **Uninstall protection**.
- Try to uninstall Sawn from the launcher / system settings.
- ✅ Android should refuse until you turn protection off first.

### 5. Strict mode + PIN
- Set a PIN, enable strict mode, turn protection on.
- ✅ Home shows "Strict lock on"; the master switch cannot be turned off.
- ✅ The tile opens Sawn instead of turning protection off.
- Start a focus session with strict unchecked. ✅ It is stored strict and there is no early exit.
- ✅ Blocklist, Schedules, app picker, and feed blocking all ask for the PIN when one exists,
  and no management controls flash before the lock screen appears.
- ✅ Turning strict mode off requires unlocking Settings with the PIN.
- ✅ Remove PIN is disabled while strict mode is on.
- ✅ In-app "Turn off protection" (device admin) is disabled while strict mode is on.
- ✅ From Android system Settings, deactivating device admin still works (the emergency exit).

### 6. Reboot persistence
- With blocking on, reboot the phone.
- ✅ The DNS filter should re-arm automatically (BootReceiver).

### 7. Schedule / focus filter refresh
- Block `example.com`. In **Settings → Always on**, turn **Always on OFF** (otherwise blocking
  is always active and a schedule boundary is invisible). Turn protection ON and keep the VPN
  running, then create a schedule that starts one minute from now and covers that site.
- ✅ At the start time, the site must fail to load within 30 seconds without toggling Sawn.
- Start and cancel a focus session. ✅ The same domain must become blocked/unblocked immediately.

### 8. Timed access expiry
- Open a PAUSE app, wait out the pause, choose "1 min", and stay in the app.
- ✅ After ~1 minute, the intercept screen must appear again over the app.
- Choose "1 min" again. ✅ A second expiry must fire after that minute.
- While a grant is active, go Home and wait past expiry. ✅ No intercept should appear in the
  background.

### 9. Strict-mode bypass attempts
- With strict mode on, try the Home switch and the Quick Settings tile. ✅ Both must refuse
  without stopping the VPN.
- Start a focus session with "Strict" unchecked while strict mode is on, then try to end it
  early. ✅ It is stored strict and has no early exit.
- Open Settings with the PIN and try to remove the PIN. ✅ The control is disabled.
- Open Settings and try to disable uninstall protection. ✅ The control is disabled until
  strict mode is turned off.

### 10. PIN lockout versus clock changes
- Set a PIN, then enter a wrong PIN five times. ✅ A lockout is shown.
- While locked, move the device clock forward by an hour. ✅ The same-boot lockout must remain
  until its real elapsed duration ends; it must not be shortened by changing wall time.
- Reboot once the lockout has fully expired and confirm the correct PIN unlocks normally.

### 11. Ad/tracker blocking and list refresh
- With **Block ads & trackers** on, open a site/app that normally loads third-party ads.
  ✅ Common ad requests should fail; first-party sponsored cards may still appear (expected).
- Disable the setting and toggle blocking off/on once. ✅ The ad hosts must stop being blocked.
- Turn off Wi-Fi/data and tap **Refresh blocklists**. ✅ Sawn must say the update failed and
  keep the previous extra-domain count; it must not claim the lists are current.

### 12. Browser fallback repeat and tunnel failure
- In a browser, visit an adult host that dodges DNS, leave the browser, then return to the
  same host. ✅ The intercept must appear again rather than only on the first visit.
- With blocking active, revoke Sawn's VPN from Android system settings. ✅ Sawn's health
  indicator must report protection off and a re-arm action must start a fresh tunnel.
- Switch Wi-Fi ↔ mobile data while blocking. ✅ DNS filtering must continue after the network
  change; note any failure and the Android version/device.

### 13. ~~Social media protection~~ — removed from the product
The in-app social content detector is gone with the rest of the in-app blocking, so
there is nothing to verify here. The section number is kept so the rest of the plan keeps
its references.

### 14. One-tap protection and the recents swipe (new in 1.2.0)
- On the home screen tap «الحماية» once. ✅ It switches on within a couple of seconds — no
  holding — the switch stays where you left it, and the ongoing notification appears.
- Tap it again. ✅ It switches off and the tunnel goes with it (no orphaned notification).
- Turn protection on, swipe Sawn out of Recents twice, wait five minutes, then open a blocked
  site. ✅ Still blocked: the health worker restarts the filter if Android tore it down, and the
  home card only reads green while the tunnel is actually running.
- Settings → Apps → Sawn → Force stop, then wait. ⚠️ Expected to stay stopped — Android does
  not let an app restart itself after a force stop. The health notification must say the filter
  stopped, and opening Sawn must restore it.

### 15. Back handling on the intercept screen (target SDK 36)
- Open a blocked app, then press Back / use the swipe-back gesture. ✅ You land on the launcher,
  never inside the blocked app. With predictive back the screen animates out instead of
  disappearing flatly.
- On «تمهّل» (the mindful pause), use Back. ✅ Recorded as a defeat: the next attempt waits
  longer — check the escalation counter.
- On the PIN screen, Back must never skip verification.

### 16. ~~On-device image screening~~ — removed from the product
The on-device NSFW image screening is gone, together with every screenshot the app took.
There is nothing to verify here; the section number is kept so the rest of the plan
keeps its references.

### 17. Dark mode and Arabic layout
- Toggle the system theme in Sawn and on an intercept screen. ✅ Both palettes stay readable,
  the day-letter row fits the narrowest phone, and nothing overlaps in right-to-left.

### 18. Home-screen widget (new in 2.2.0)
- Add the 2×2 widget to the home screen. ✅ The large number is the protected-day streak — **not
  the app name** (before 2.2.0 the streak field showed «صَوْن» permanently), and the line under it
  reads protected / off.
- Tap the **افتح** button. ✅ The app opens. (Before 2.2.0 the button had no listener, so the tap
  did nothing at all.) Tapping the widget body should open it too.
- Toggle protection off and on. ✅ The widget must follow within a few seconds. It used to keep
  showing the old state until the launcher happened to ask for an update.
- Rotate to a fresh streak (or clear app data): ✅ the widget shows the app name instead of a
  misleading `0 days`.

### 19. In-app update channel (new in 2.2.0)
- Open **Settings → Privacy & support → Check for updates**. ✅ It must reach the hosted
  manifest over HTTPS. If it says the hosting is not configured, this APK was built **without**
  `SAWN_UPDATE_MANIFEST_URL` — see `docs/update-hosting.md`; it cannot be fixed by the user.
- With an older version installed, ✅ the dialog shows the new version name and the **real release
  notes** (pulled from `docs/CHANGELOG.md`), not a generic sentence.
- Tap download. ✅ The APK is downloaded, its SHA-256 is re-checked locally, and only then does
  Android's installer open. The screen must say the update is verified and awaiting approval —
  **not** “you already have the latest version” (before 2.2.0 that message appeared while the
  update was still pending).
- Cancel the system install. ✅ The app must not report the update as installed.
- Serve an APK that is **not** signed with the release key (a re-signed copy with the same
  package name and a higher `versionCode`). ✅ The app must refuse it before the installer
  opens and say **«فشل التحقق من توقيع التحديث — تم إلغاء التثبيت»**. The Android installer
  must not appear at all — the check happens in Sawn, not in the installer.

### 20. Automatic updates (new in 2.2.0)
- **Settings → Privacy & support → Sawn updates** must show three switches, **all on by default**:
  *Automatic updates*, *Download automatically*, and *Install as soon as it is ready*.
- Turn **Automatic updates** off, then force-stop the app and wait for the periodic worker
  (or trigger it from `adb shell am broadcast`/WorkManager). ✅ No network request to the
  manifest, and no notification. Check with `adb shell dumpsys netstats` or the app's own log.
- Turn it back on and publish a test manifest with a higher `versionCode`. ✅ Within the
  worker interval (or on the next app start) a notification appears: **«التحديث جاهز»**.
- Tap the notification. ✅ Android's installer opens **directly on the already-downloaded,
  already-verified APK** — no second download, no second progress bar.
- The install screen must show the app name and require a tap on **Install**. ✅ That tap is
  mandatory: Android offers no way to install silently, and Sawn must not pretend otherwise.
- Install it. ✅ The app must come back with the new `versionCode`, and the pending-update
  card must disappear.
- Open **Settings → Privacy & support** before installing. ✅ The card must already show
  **«الإصدار X جاهز للتثبيت»** with an *Install now* button, plus the real release notes.

## Report back
Note whether a blocked app was intercepted, whether DoH bypassed the block in your
browser, and anything that didn't behave — those become the next fixes. If a site slipped
through, note the app it was opened in and the address you saw, so the engine's reach
can be judged.
