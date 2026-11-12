package com.sawn.app.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.sawn.domain.update.ApkSignaturePolicy
import java.io.File

/**
 * Reading the signing certificates — of a downloaded archive, and of the installed app.
 *
 * There is one reader because there are two callers: the updater compares a downloaded
 * APK with our own signing lineage, and the About card shows the fingerprint the user
 * can compare with ours. They must never read the certificates differently — a second
 * copy of the API-level dance is a second chance to break the check.
 *
 * `android.content.pm.SigningInfo` does not exist below API 28, so the reader that
 * knows about the lineage lives in its own class: a class that names a missing API in a
 * method *signature* cannot be linked on Android 8, and the legacy path must keep
 * working there.
 */
internal object SigningCertificates {

    /**
     * The SHA-256 fingerprints of every certificate the archive is signed with.
     *
     * Empty when the archive carries no signature, or cannot be read at all — both
     * cases refuse the file.
     */
    fun archiveFingerprints(packageManager: PackageManager, apkFile: File): Set<String> {
        val info = archiveInfo(packageManager, apkFile) ?: return emptySet()
        return fingerprints(info)
    }

    /** The SHA-256 fingerprints of every certificate the installed package carries. */
    fun installedFingerprints(packageManager: PackageManager, packageName: String): Set<String> {
        val info = installedInfo(packageManager, packageName) ?: return emptySet()
        return fingerprints(info)
    }

    /**
     * The bytes of the certificate this build is signed with.
     *
     * The *current* signer — the newest of the lineage, because the history is ordered
     * from oldest to newest. This is the fingerprint the user compares with ours, so it
     * has to be the key that signed this build, not the one a key rotation replaced.
     */
    fun installedCurrentCertificate(
        packageManager: PackageManager,
        packageName: String,
    ): ByteArray? {
        val info = installedInfo(packageManager, packageName) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ModernSigningCertificates.currentCertificate(info)
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
    }

    /** The fingerprints of a package's signing certificates — the whole lineage. */
    private fun fingerprints(info: PackageInfo): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ModernSigningCertificates.certificates(info)
        } else {
            legacyCertificates(info)
        }
        return certificates.mapTo(mutableSetOf()) { ApkSignaturePolicy.fingerprint(it) }
    }

    /** The certificates of Android 8 and 9's predecessor API: the current signer only. */
    @Suppress("DEPRECATION")
    private fun legacyCertificates(info: PackageInfo): List<ByteArray> =
        info.signatures?.mapNotNull { it?.toByteArray() } ?: emptyList()

    private fun archiveInfo(packageManager: PackageManager, apkFile: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags().toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags())
        }

    private fun installedInfo(packageManager: PackageManager, packageName: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags().toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags())
        }

    /**
     * The flags that make the package manager return the signing certificates.
     *
     * `GET_SIGNING_CERTIFICATES` — the flag that also carries the lineage — arrived in
     * API 28. Below it the legacy flag is the only way to read the certificate at all.
     */
    @Suppress("DEPRECATION")
    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
}

/**
 * The API 28+ reader — the one that knows about the lineage.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal object ModernSigningCertificates {

    /**
     * Every certificate the package is signed with.
     *
     * `signingCertificateHistory` is the **whole lineage** — the current signer plus
     * every key it superseded — which is what lets a key rotation keep working instead
     * of bricking the updater for everybody still on the old key.
     */
    fun certificates(info: PackageInfo): List<ByteArray> =
        info.signingInfo
            ?.signingCertificateHistory
            ?.mapNotNull { it?.toByteArray() }
            ?: emptyList()

    /** The certificate this build is signed with — the newest of the lineage. */
    fun currentCertificate(info: PackageInfo): ByteArray? =
        certificates(info).lastOrNull()
}
