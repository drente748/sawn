# Porn Guard — Design

**Date:** 2026-10-28
**Status:** Implemented (v0.4.0)
**Scope:** Adult-content blocking at commercial grade — DNS sinkhole, browser URL watch,
SafeSearch enforcement, refreshable open-source lists.

## Why

Warden's commercial positioning is protection from adult content and ads, not general app
blocking. This wave makes that promise real: adult sites are blocked in every browser and
app on the device, search engines are forced into their restricted modes, and the paths
that normally dodge DNS filtering (DoH, raw navigation) get a second, accessibility-based
net.

## Layers

1. **DNS sinkhole (VPN)** — `AdultHosts` (curated offline list, ~120 top domains incl.
   regional/Arabic + cam + hentai + onlyfans-style sites) merges into the existing tunnel
   filter whenever `pornGuard` is on and blocking is active. Runtime-fetched domains
   (`adultExtraDomains`) join the same set.
2. **SafeSearch enforcement** — `SafeSearchHosts` maps search/video hosts to their
   official restricted DNS anchors (Google `forcesafesearch.google.com`, YouTube
   `restrict.youtube.com`, Bing `strict.bing.com`, DuckDuckGo `safe.duckduckgo.com`).
   When the VPN sees a mapped host it resolves the safe anchor instead and returns that
   answer, so the search engine itself enforces restricted results even if the user
   never logged in. (Wiring lands with the SNI rewrite task; the map is shipped with
   tests now.)
3. **Browser URL monitor** — `BrowserUrlMonitor` (accessibility-based) scans browser
   windows (Chrome, Firefox, Samsung Internet, Opera, Edge, Brave, Kiwi, UC, DuckDuckGo,
   Yandex…) for an address-bar/visible URL whose host matches the adult set and launches
   `KIND_ADULT` intercept over the browser. Covers DoH-using browsers and cached-DNS
   edge cases that escape the tunnel. Text is inspected in memory only — never stored.
4. **List refresh** — `AdultListFetcher` pulls hosts-format files from PornAway and
   StevenBlack's porn extension (both actively maintained), merges them (capped at
   100k), de-duplicates by content hash, and throttles to once/day per cache.

## Settings

- `PORN_GUARD` — default **on**; the app's core promise for its target users.
- `SAFE_SEARCH_FORCE` — default on.
- `ADULT_EXTRA_DOMAINS` — runtime-merged set from fetched sources.
- Settings screen: "Porn Guard" card with the master switch, SafeSearch switch, list
  refresh button, and a count of extra domains.

## Intercept UX

`InterceptActivity` gained `KIND_ADULT` — the shared `CenteredHoldToLeave` screen with
adult-specific copy ("Blocked — adult content"), requiring the deliberate ~1.2s hold to
leave. No grant, no "continue for a minute": the page stays blocked.

## Honest limits

- Hardcoded-IP adult sites (rare) escape DNS and are caught only by the URL monitor.
- Encrypted-DNS browsers that already warmed a DNS cache may load a site for one page
  load before the monitor intercepts.
- The monitor scans visible text, so a site opened inside an app-embedded webview with
  no URL text visible is not covered until DNS catches it.
- SafeSearch rewrite via DNS covers A-queries only; IPv6 (AAAA) safe-host answers are a
  follow-up.

## Testing

- `DomainMatcherTest`: normalization (scheme/www/userinfo/port/path stripping, spaces
  rejected), suffix matching (subdomains yes, suffix-pretending no, null/blank no), and
  list hygiene (every entry lowercase, trimmed, has a dot, no leading dot, no spaces).
- SafeSearch host mapping covered in the same test class.
- Full `testDebugUnitTest` suite passes; `assembleRelease` produces the signed APK.
