package com.sawn.domain.update

/**
 * The decision of whether a downloaded APK may be handed to Android's installer.
 *
 * Pure Kotlin on purpose: the rule that decides whether a file is trustworthy must be
 * unit-testable without a device, a network, or a real APK.
 *
 * The threat: the update channel is the weakest link. A SHA-256 in a manifest proves the
 * file matches *the manifest*; it does not prove the file was signed by *us*. If the
 * manifest is replaced — or served from a compromised page — an attacker-supplied APK
 * with our package name would pass the checksum. Android's installer would then refuse
 * it (the signature does not match the installed app), but the user is one tap away
 * from uninstalling the real app first — and then the forged APK installs cleanly.
 *
 * So the signature is checked **before** the installer is ever opened, not by it.
 */
object ApkSignaturePolicy {

    /** Why a candidate APK was refused. */
    enum class Verdict {
        /** The candidate is signed by a key in our lineage. */
        ACCEPTED,

        /** The APK carries no signature at all. */
        UNSIGNED,

        /** The APK is signed, but by a key that is not ours. */
        FOREIGN_KEY,

        /** We could not read the signature (no archive info, malformed APK). */
        UNREADABLE,
    }

    /**
     * Compares the candidate APK's signing certificates against our own lineage.
     *
     * @param candidateFingerprints SHA-256 fingerprints (lowercase hex) of the
     * certificates the candidate APK is signed with — the **whole** history, not just the
     * current signer, because a key rotation means an older install carries an older key.
     * @param ourFingerprints the fingerprints of the installed app's signing lineage.
     *
     * The candidate is accepted when it shares **any** fingerprint with our lineage: that
     * is exactly the rule Android's own installer applies, and it is what makes a
     * key-rotation upgrade work instead of bricking the updater.
     */
    fun verify(
        candidateFingerprints: Set<String>,
        ourFingerprints: Set<String>,
    ): Verdict {
        // No signature on the file at all: it could never have been installed over the
        // real app, so it must not be offered.
        if (candidateFingerprints.isEmpty()) return Verdict.UNSIGNED
        // We could not read our own signature: we cannot prove anything, so we refuse.
        if (ourFingerprints.isEmpty()) return Verdict.UNREADABLE
        val shared = candidateFingerprints.intersect(ourFingerprints)
        return if (shared.isNotEmpty()) Verdict.ACCEPTED else Verdict.FOREIGN_KEY
    }

    /**
     * The SHA-256 fingerprint of a signing certificate, lowercase hex.
     *
     * Only the fingerprint is ever computed or logged — the certificate itself is public
     * data, but there is no reason to write it anywhere.
     */
    fun fingerprint(certificate: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(certificate)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
