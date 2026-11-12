package com.sawn.domain.block.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The address bar shows whatever the browser feels like showing. These tests pin the
 * shapes that must all collapse to the same bare host.
 */
class HostNormalizerTest : StringSpec({

    "a full URL collapses to its host" {
        HostNormalizer.normalizeHost("https://www.Example.com/path?q=1") shouldBe "example.com"
        HostNormalizer.normalizeHost("http://example.com/") shouldBe "example.com"
    }

    "a breadcrumb is cut at the separator" {
        HostNormalizer.normalizeHost("example.com › Some Page") shouldBe "example.com"
        HostNormalizer.normalizeHost("example.com／page") shouldBe "example.com"
    }

    "plain text is taken as the host" {
        HostNormalizer.normalizeHost("example.com") shouldBe "example.com"
        HostNormalizer.normalizeHost("  example.com  ") shouldBe "example.com"
    }

    "upper case is folded" {
        HostNormalizer.normalizeHost("WWW.EXAMPLE.COM") shouldBe "example.com"
        HostNormalizer.normalizeHost("HTTPS://WWW.Example.Com/Path") shouldBe "example.com"
    }

    "a search query is not a host" {
        HostNormalizer.normalizeHost("how to block a site") shouldBe null
        HostNormalizer.normalizeHost("") shouldBe null
        HostNormalizer.normalizeHost(null) shouldBe null
        HostNormalizer.normalizeHost("   ") shouldBe null
    }

    "the port and the userinfo are dropped" {
        HostNormalizer.normalizeHost("https://user:pass@example.com:8080/x") shouldBe "example.com"
    }

    "a trailing dot is dropped" {
        HostNormalizer.normalizeHost("example.com.") shouldBe "example.com"
    }

    "a subdomain is kept as it is" {
        HostNormalizer.normalizeHost("www.example.com") shouldBe "example.com"
        HostNormalizer.normalizeHost("a.b.example.com") shouldBe "a.b.example.com"
    }
})
