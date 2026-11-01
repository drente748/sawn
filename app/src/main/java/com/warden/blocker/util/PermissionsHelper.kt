package com.warden.blocker.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.warden.blocker.accessibility.AppBlockAccessibilityService
import com.warden.blocker.usage.UsageStatsHelper

/** Central checks for the special-access permissions Warden relies on. */
object PermissionsHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val id = "${context.packageName}/${AppBlockAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(id, ignoreCase = true) }
    }

    fun hasUsageAccess(context: Context): Boolean = UsageStatsHelper.hasPermission(context)

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Accessibility is the one truly essential grant (app blocking + in-app feeds). */
    fun essentialsGranted(context: Context): Boolean = isAccessibilityEnabled(context)

    /** Not "granted" by us — the user picks Warden → No restrictions in system settings. */
    fun isBatteryOptimizationIgnored(context: Context): Boolean =
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

    /** Always-on VPN is configured for Warden, so the OS re-arms the filter after reboots. */
    fun isAlwaysOnVpnConfigured(context: Context): Boolean = runCatching {
        Settings.Secure.getString(context.contentResolver, "always_on_vpn_app") == context.packageName
    }.getOrDefault(false)

    /**
     * Strict Private DNS sends queries over DoT straight to the configured resolver, so the
     * Warden tunnel never sees them — the DNS filter is silently bypassed (API 28+). Even
     * "Automatic" may use DoT on networks/carriers that advertise it.
     */
    fun isPrivateDnsStrict(context: Context): Boolean = runCatching {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Settings.Global.getString(context.contentResolver, "private_dns_mode") == "hostname"
    }.getOrDefault(false)

    /**
     * Non-Play installs on Android 13+ need a one-time "Allow restricted settings" from the
     * App Info menu before Accessibility can be switched on.
     */
    fun isSideloaded(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer != "com.android.vending"
    }.getOrDefault(false)
}
