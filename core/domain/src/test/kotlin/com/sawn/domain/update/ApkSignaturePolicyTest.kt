package com.sawn.domain.update

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The rule that decides whether a downloaded APK may be handed to Android's installer.
 *
 * Why this matters: the SHA-256 in the manifest proves the file matches *the manifest*.
 * It does not prove the file was signed by *us*. If the manifest is replaced — or served
 * from a compromised page — an attacker's APK carrying our package name passes the
 * checksum. Android's installer would refuse it, but only until the user uninstalls the
 * real app first, and then the forged APK installs cleanly.
 *
 * So the signature is checked before the installer is ever opened.
 */
class ApkSignaturePolicyTest : StringSpec({

    "a candidate signed by our key is accepted" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = setOf(OUR_KEY),
            ourFingerprints = setOf(OUR_KEY),
        ) shouldBe ApkSignaturePolicy.Verdict.ACCEPTED
    }

    "a candidate signed by somebody else is refused" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = setOf(ATTACKER_KEY),
            ourFingerprints = setOf(OUR_KEY),
        ) shouldBe ApkSignaturePolicy.Verdict.FOREIGN_KEY
    }

    "an unsigned candidate is refused" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = emptySet(),
            ourFingerprints = setOf(OUR_KEY),
        ) shouldBe ApkSignaturePolicy.Verdict.UNSIGNED
    }

    /**
     * Key rotation: the installed copy carries the *old* key, the update carries the new
     * one. The new APK's certificate history contains the old key, so the two lineages
     * still meet and the update must go through — otherwise a rotation would brick the
     * updater for every user on the old key.
     */
    "a rotated key is accepted when the histories meet" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = setOf(OLD_KEY, NEW_KEY),
            ourFingerprints = setOf(OLD_KEY),
        ) shouldBe ApkSignaturePolicy.Verdict.ACCEPTED
    }

    /** The other direction of a rotation: we are on the new key, the file is signed with
     *  the old one. The old key is in our history, so the update is still ours. */
    "a rotated key is accepted in the other direction too" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = setOf(OLD_KEY),
            ourFingerprints = setOf(OLD_KEY, NEW_KEY),
        ) shouldBe ApkSignaturePolicy.Verdict.ACCEPTED
    }

    /** A file signed by a key that merely shares a prefix is not ours. */
    "a look-alike fingerprint is refused" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = setOf(OUR_KEY.dropLast(1) + "0"),
            ourFingerprints = setOf(OUR_KEY),
        ) shouldBe ApkSignaturePolicy.Verdict.FOREIGN_KEY
    }

    /**
     * If we cannot read our *own* signature, we cannot prove anything about the
     * candidate — so we refuse rather than wave it through.
     */
    "an unreadable lineage refuses everything" {
        ApkSignaturePolicy.verify(
            candidateFingerprints = setOf(OUR_KEY),
            ourFingerprints = emptySet(),
        ) shouldBe ApkSignaturePolicy.Verdict.UNREADABLE
    }

    "the fingerprint is the lowercase sha-256 of the certificate" {
        val certificate = byteArrayOf(1, 2, 3)
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { "%02x".format(it) }

        ApkSignaturePolicy.fingerprint(certificate) shouldBe expected
    }
})

private val OUR_KEY = "a".repeat(64)
private val OLD_KEY = "b".repeat(64)
private val NEW_KEY = "c".repeat(64)
private val ATTACKER_KEY = "d".repeat(64)
