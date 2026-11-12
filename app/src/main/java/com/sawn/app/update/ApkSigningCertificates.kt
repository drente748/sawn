package com.sawn.app.update

import android.content.pm.PackageInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.sawn.domain.update.ApkSignaturePolicy

/**
 * The signing certificates of a package on Android 9 (API 28) and later.
 *
 * `android.content.pm.SigningInfo` does not exist below API 28, so it is confined to
 * this class: a class that names a missing API in a method *signature* cannot be
 * linked on Android 8, and the legacy path in [UpdateInstaller] must keep working
 * there. The class is only ever touched on a system that has the API.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal object ApkSigningCertificates {

    /**
     * The SHA-256 fingerprints of every certificate the package is signed with.
     *
     * `signingCertificateHistory` is the **whole lineage** — the current signer plus
     * every key it superseded — which is what lets a key rotation keep working
     * instead of bricking the updater for everybody still on the old key.
     */
    fun fingerprints(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        val certificates = signingInfo.signingCertificateHistory ?: return emptySet()
        return certificates
            .mapNotNull { it?.toByteArray() }
            .mapTo(mutableSetOf()) { ApkSignaturePolicy.fingerprint(it) }
    }
}
