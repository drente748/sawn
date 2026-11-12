package com.sawn.domain.block

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.zip
import io.kotest.property.checkAll

/**
 * The blocklist boundary, as a property.
 *
 * "example.com" has to cover every subdomain of it and *only* them. If the boundary
 * moved to the right, the blocking would cover sites it was never asked to cover; if
 * it moved to the left, a blocked site could be reached through a subdomain and the
 * whole blocklist would be a suggestion.
 */
class DomainMatcherPropertyTest : StringSpec({

    /** A lowercase label of one to eight letters. */
    val arbLabel = Arb.string(1..8, "a-z")

    /** A domain of two or three lowercase labels — always at least one dot. */
    val arbDomain = Arb.zip(
        Arb.zip(arbLabel, arbLabel) { a, b -> "$a.$b" },
        Arb.zip(arbLabel, arbLabel) { a, b -> "$a.$b" },
    ) { short, long -> "$short.$long" }

    /**
     * INVARIANT 1 — a subdomain of a blocked domain is blocked.
     *
     * The blocklist names a site; the site is reached through "www." and a dozen other
     * names. Every one of them belongs to the same site and must be covered.
     */
    "a subdomain of a blocked domain is blocked" {
        checkAll(arbDomain) { domain ->
            DomainMatcher.matches("sub.$domain", setOf(domain)) shouldBe true
        }
    }

    /**
     * INVARIANT 2 — a domain that merely shares the suffix is not blocked.
     *
     * "notexample.com" is a different site that happens to end with the blocked name.
     * Blocking it would be a bug that blocks innocent sites; not blocking it is the
     * whole point of the label boundary.
     */
    "a domain that only shares the suffix is not blocked" {
        checkAll(arbDomain) { domain ->
            DomainMatcher.matches("not$domain", setOf(domain)) shouldBe false
        }
    }

    /** The blocked domain itself is blocked, whatever the letter case is. */
    "the blocked domain itself is blocked" {
        checkAll(arbDomain) { domain ->
            DomainMatcher.matches(domain, setOf(domain)) shouldBe true
            DomainMatcher.matches(domain.uppercase(), setOf(domain)) shouldBe true
        }
    }
})
