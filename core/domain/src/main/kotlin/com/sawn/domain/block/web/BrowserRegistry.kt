package com.sawn.domain.block.web

/**
 * The browsers Sawn knows how to read, and how to recognize them.
 *
 * The whole [BrowserConfig] table (116 browsers) is reachable through [byPackage];
 * the seven browsers below are the ones Sawn advertises and tests against, so a
 * regression in any of them is visible instead of hidden in a long tail.
 */
object BrowserRegistry {

    /** Chrome — the URL widget is "url_bar", and "origin" shows the eTLD+1 only. */
    val CHROME = BrowserSpec(
        packageName = "com.android.chrome",
        displayName = "Chrome",
        urlBarIds = listOf("url_bar", "origin", "url_bar_container", "search_box"),
    )

    /** Firefox — the address box moved between "ADDRESSBAR_URL_BOX" and the Mozilla one. */
    val FIREFOX = BrowserSpec(
        packageName = "org.mozilla.firefox",
        displayName = "Firefox",
        urlBarIds = listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"),
    )

    /** Samsung Internet — its own "location_bar_edit_text". */
    val SAMSUNG = BrowserSpec(
        packageName = "com.sec.android.app.sbrowser",
        displayName = "Samsung Internet",
        urlBarIds = listOf("location_bar_edit_text", "url_bar"),
    )

    /** Edge (the Android build is still "emmx"). */
    val EDGE = BrowserSpec(
        packageName = "com.microsoft.emmx",
        displayName = "Edge",
        urlBarIds = listOf("url_bar"),
    )

    /** Brave — Chromium-based, same widget as Chrome. */
    val BRAVE = BrowserSpec(
        packageName = "com.brave.browser",
        displayName = "Brave",
        urlBarIds = listOf("url_bar", "origin"),
    )

    /** DuckDuckGo — its own "omnibarTextInput". */
    val DUCKDUCKGO = BrowserSpec(
        packageName = "com.duckduckgo.mobile.android",
        displayName = "DuckDuckGo",
        urlBarIds = listOf("omnibarTextInput", "url_bar"),
    )

    /** Opera — "url_field" on the modern app, "addressbarEdit" on the touch build. */
    val OPERA = BrowserSpec(
        packageName = "com.opera.browser",
        displayName = "Opera",
        urlBarIds = listOf("url_field", "addressbarEdit"),
    )

    /** The browsers Sawn advertises. A regression in one of these is a visible bug. */
    val CORE: List<BrowserSpec> = listOf(CHROME, FIREFOX, SAMSUNG, EDGE, BRAVE, DUCKDUCKGO, OPERA)

    private val byPackage: Map<String, BrowserSpec> = buildMap {
        CORE.forEach { put(it.packageName, it) }
        // The long tail: every browser in the config table, addressed by the same
        // suffix-matching rules. Its display name is the package, which is enough to
        // log and to show the user which app was recognized.
        BrowserConfig.ALL.values.forEach { config ->
            putIfAbsent(config.packageName, BrowserSpec.from(config, config.packageName))
        }
    }

    /** The spec for [packageName], or null when the package is not a known browser. */
    fun byPackage(packageName: String): BrowserSpec? = byPackage[packageName]

    /** True when [packageName] is a browser Sawn knows. */
    fun isKnownBrowser(packageName: String): Boolean = byPackage.containsKey(packageName)

    /** How many browsers are known — shown in the settings screen. */
    val size: Int get() = byPackage.size
}
