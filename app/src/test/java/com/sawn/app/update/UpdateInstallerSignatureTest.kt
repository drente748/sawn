package com.sawn.app.update

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * The signature gate of the updater.
 *
 * The update channel is the weakest link: a SHA-256 in the manifest proves the file
 * matches *the manifest*, not that it was signed by *us*. An APK carrying our package
 * name and a higher version code would pass the checksum and reach the system
 * installer — one uninstall away from taking the app's place. So the signature is
 * compared against our own signing lineage before the installer is ever opened, and a
 * file that does not match is refused.
 *
 * These tests run on API 26 (the only Robolectric system image available offline), so
 * they exercise the legacy certificate path. The key-rotation rule — the lineage
 * comparison — is covered where it lives, in `ApkSignaturePolicyTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class UpdateInstallerSignatureTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val shadowPackageManager = Shadows.shadowOf(context.packageManager)

    /** Stands for our own signing key. */
    private val ourKey = byteArrayOf(1, 2, 3, 4, 5)

    /** Stands for somebody else's key — an attacker who repackaged the app. */
    private val foreignKey = byteArrayOf(9, 9, 9, 9, 9)

    @Test
    fun anApkSignedWithOurKeyIsAccepted() {
        installOurApp(ourKey)
        installArchive(ourKey)
        assertTrue(UpdateInstaller.verifyApkSignature(context, apkFile()))
    }

    @Test
    fun anApkSignedWithAForeignKeyIsRefused() {
        installOurApp(ourKey)
        installArchive(foreignKey)
        assertFalse(UpdateInstaller.verifyApkSignature(context, apkFile()))
    }

    @Test
    fun anUnsignedApkIsRefused() {
        installOurApp(ourKey)
        installArchive(null)
        assertFalse(UpdateInstaller.verifyApkSignature(context, apkFile()))
    }

    /**
     * The anchor is the *installed* app. If our own certificate cannot be read, nothing
     * can be proven about the candidate — so the candidate is refused rather than
     * waved through.
     */
    @Test
    fun anApkIsRefusedWhenOurOwnCertificateCannotBeRead() {
        installOurApp(null)
        installArchive(ourKey)
        assertFalse(UpdateInstaller.verifyApkSignature(context, apkFile()))
    }

    /** A file the package manager cannot read at all is refused as well. */
    @Test
    fun anUnreadableArchiveIsRefused() {
        installOurApp(ourKey)
        shadowPackageManager.setPackageArchiveInfo(apkFile().absolutePath, PackageInfo())
        assertFalse(UpdateInstaller.verifyApkSignature(context, apkFile()))
    }

    /** The installed package, as the system knows it. */
    private fun installOurApp(key: ByteArray?) {
        shadowPackageManager.addPackage(
            PackageInfo().apply {
                packageName = context.packageName
                applicationInfo = ApplicationInfo().apply { packageName = context.packageName }
                signatures = key?.let { arrayOf(Signature(it)) }
            },
        )
    }

    /** The downloaded archive, as the package manager reports it. */
    private fun installArchive(key: ByteArray?) {
        shadowPackageManager.setPackageArchiveInfo(
            apkFile().absolutePath,
            PackageInfo().apply {
                packageName = context.packageName
                signatures = key?.let { arrayOf(Signature(it)) }
            },
        )
    }

    private fun apkFile(): File =
        File(context.cacheDir, "updates/Sawn-99.apk").apply { parentFile?.mkdirs() }
}
