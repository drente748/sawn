# Three Blocker Fixes — Spec

**Date:** 2026-10-28
**Status:** Approved for planning
**Scope:** Three correctness fixes only. No ads, premium, localization, redesign, or unrelated audit work.

## Problem 1 — VPN blocklist goes stale under schedules/focus

`WardenVpnService.watchBlocklistChanges()` observes only `repo.items`, `masterEnabled`,
`alwaysOn`, and `blockDoh`. It does not observe `repo.schedules` or `focusEndsAt`, and nothing
refreshes the filter when wall-clock time crosses a schedule boundary. Result: a schedule or
focus session can start or end while the VPN keeps the old domain set, so blocked sites stay
reachable (or stay blocked) until blocking is toggled.

### Acceptance criteria
- Editing/adding/removing a schedule updates the running filter immediately.
- Starting or cancelling a focus session updates the running filter immediately.
- Crossing a schedule start/end boundary updates the running filter within 30 seconds.
- Turning the master switch off (with no focus session running) clears the filter set
  (all domains allowed) immediately.
- The VPN never needs to be toggled for any of the above.

## Problem 2 — Timed access expires but the app stays open

A mindful-pause grant is enforced only when the accessibility service receives a new
`TYPE_WINDOW_STATE_CHANGED` for a different package. While the same app stays foreground,
nothing re-evaluates the decision when the grant expires. Result: a "1 min" grant silently
becomes permanent until the user leaves and returns.

### Acceptance criteria
- When a grant expires while its app is still foreground, `InterceptActivity` is shown again.
- Re-granting after that second intercept schedules the next expiry correctly.
- Leaving the app cancels the pending expiry recheck (no intercept for a background app).
- Expired grants do not allow access in the pure decision table.

## Problem 3 — Strict mode is only a stored flag

`strictMode` is read by Settings and the device-admin button only. It does not lock the Home
master switch, the Quick Settings tile, or focus cancellation. `MainActivity.cancelFocus()`
also stops the VPN before the async refusal check completes. Result: strict mode can be
bypassed in one tap/hold, and a strict focus session can still tear down the tunnel.

### Acceptance criteria
- While strict mode is on and protection is on, the Home switch cannot turn protection off.
- The Quick Settings tile cannot turn protection off; it opens the app instead.
- A focus session started while strict mode is on is stored as strict and cannot be ended
  early. Cancelling an active strict focus session must not stop the VPN.
- Strict mode can only be enabled when a PIN exists; the PIN cannot be removed while strict
  mode is on.
- Device-admin uninstall protection still cannot be disabled in-app while strict mode is on.
- The pure strict policy is unit-tested; UI/service wiring is verified by build + manual steps.

## Out of scope
- Reworking `SettingsStore.trustedNow()` write frequency.
- Fixing async DNS reply writes or unbounded forwarding sockets.
- Restarting the VPN when the packet loop dies.
- Release signing, ads, billing, onboarding, localization.
- Any commit of the user's existing uncommitted work.

## Verification constraint
No Android device is connected. JVM unit tests and a release build can be verified locally;
VPN, accessibility, Quick Settings, and uninstall behavior must be checked manually using
`DEVICE_TESTING.md`.
