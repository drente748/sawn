package com.sawn.domain.block.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The browsers Sawn advertises.
 *
 * A regression in any of these is a visible bug — the user picked that browser and
 * expects it to be covered — so they are pinned here by name.
 */
class BrowserRegistryTest : StringSpec({

    "the core browsers are all known" {
        listOf(
            BrowserRegistry.CHROME,
            BrowserRegistry.FIREFOX,
            BrowserRegistry.SAMSUNG,
            BrowserRegistry.EDGE,
            BrowserRegistry.BRAVE,
            BrowserRegistry.DUCKDUCKGO,
            BrowserRegistry.OPERA,
        ).forEach { spec ->
            BrowserRegistry.byPackage(spec.packageName) shouldBe spec
        }
    }

    "an unknown package is not a browser" {
        BrowserRegistry.byPackage("com.example.notabrowser") shouldBe null
    }

    "the long tail of the config table is reachable too" {
        // The registry must not be limited to the seven advertised browsers: the config
        // table knows 116.
        BrowserRegistry.size shouldBe BrowserConfig.ALL.size
    }

    "the core browsers name the widgets they use" {
        BrowserRegistry.CHROME.urlBarIdSet shouldBe setOf("url_bar", "origin", "url_bar_container", "search_box")
        BrowserRegistry.FIREFOX.urlBarIdSet shouldBe setOf(
            "ADDRESSBAR_URL_BOX",
            "mozac_browser_toolbar_url_view",
            "url_bar_title",
        )
        BrowserRegistry.SAMSUNG.urlBarIdSet shouldBe setOf("location_bar_edit_text", "url_bar")
        BrowserRegistry.EDGE.urlBarIdSet shouldBe setOf("url_bar")
        BrowserRegistry.BRAVE.urlBarIdSet shouldBe setOf("url_bar", "origin")
        BrowserRegistry.DUCKDUCKGO.urlBarIdSet shouldBe setOf("omnibarTextInput", "url_bar")
        BrowserRegistry.OPERA.urlBarIdSet shouldBe setOf("url_field", "addressbarEdit")
    }
})
