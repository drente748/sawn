package com.sawn.domain.block

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The boundary between "a subdomain" and "a different domain".
 *
 * This is the rule the whole blocklist rests on: "example.com" must cover
 * "www.example.com" and "a.b.example.com", and must never cover "notexample.com" or
 * "example.com.evil.net" — those are different sites that happen to contain the
 * blocked name as a substring.
 */
class DomainMatcherBoundaryTest : StringSpec({

    "an exact match is blocked" {
        DomainMatcher.matches("example.com", setOf("example.com")) shouldBe true
    }

    "a subdomain is blocked" {
        DomainMatcher.matches("www.example.com", setOf("example.com")) shouldBe true
        DomainMatcher.matches("a.b.example.com", setOf("example.com")) shouldBe true
        DomainMatcher.matches("deep.sub.example.com", setOf("example.com")) shouldBe true
    }

    "a longer label is not a subdomain" {
        DomainMatcher.matches("notexample.com", setOf("example.com")) shouldBe false
        DomainMatcher.matches("myexample.com", setOf("example.com")) shouldBe false
    }

    "a blocked domain does not cover a domain that merely ends with it" {
        DomainMatcher.matches("example.com.evil.net", setOf("example.com")) shouldBe false
    }

    "the trailing dot and the case do not matter" {
        DomainMatcher.matches("EXAMPLE.COM.", setOf("example.com")) shouldBe true
    }

    "an empty host is never blocked" {
        DomainMatcher.matches("", setOf("example.com")) shouldBe false
        DomainMatcher.matches(null, setOf("example.com")) shouldBe false
    }

    "an empty blocklist blocks nothing" {
        DomainMatcher.matches("example.com", emptySet()) shouldBe false
    }
})
