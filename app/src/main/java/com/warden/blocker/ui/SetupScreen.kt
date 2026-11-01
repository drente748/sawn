package com.warden.blocker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.warden.blocker.util.PermissionsHelper

@Composable
fun SetupScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    // Re-check statuses whenever we come back to the foreground (e.g. from system settings).
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_EXPRESSION") refresh // read so recomposition re-evaluates the checks below

    val accessibility = PermissionsHelper.isAccessibilityEnabled(context)
    val usage = PermissionsHelper.hasUsageAccess(context)
    val notifications = PermissionsHelper.hasNotifications(context)
    val alwaysOnVpn = PermissionsHelper.isAlwaysOnVpnConfigured(context)
    val batteryFree = PermissionsHelper.isBatteryOptimizationIgnored(context)
    val privateDnsStrict =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && PermissionsHelper.isPrivateDnsStrict(context)
    val showRestrictedSettings =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !accessibility && PermissionsHelper.isSideloaded(context)

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        BackHeader("Setup", onBack)
        Spacer(Modifier.height(4.dp))
        Text("Grant these so Warden can do its job. Only accessibility is required.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        SetupRow(
            title = "App blocking (Accessibility)",
            subtitle = "Required — detects blocked apps and in-app feeds.",
            granted = accessibility,
            actionLabel = "Open",
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        if (showRestrictedSettings) {
            SetupRow(
                title = "Allow restricted settings",
                subtitle = "Android 13+ blocks special access for sideloaded apps. In App info, " +
                    "tap ⋮ → Allow restricted settings, then enable Accessibility.",
                granted = false,
                actionLabel = "App info",
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }

        SetupRow(
            title = "Usage access",
            subtitle = "For screen-time and daily time limits.",
            granted = usage,
            actionLabel = "Open",
        ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        SetupRow(
            title = "Notifications",
            subtitle = "Shows the ongoing 'blocking active' notice.",
            granted = notifications,
            actionLabel = "Open",
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }

        SectionLabel("Keep protection alive")
        SetupRow(
            title = "Always-on VPN",
            subtitle = "Lets Android restart Warden's filter by itself after reboots and crashes. " +
                "Turn on Always-on for Warden — but leave 'Block connections without VPN' off; " +
                "Warden only filters DNS.",
            granted = alwaysOnVpn,
            actionLabel = "Open",
        ) {
            runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
        }

        SetupRow(
            title = "Battery optimization",
            subtitle = "Choose Warden → No restrictions so your phone can't kill the filter in the background.",
            granted = batteryFree,
            actionLabel = "Open",
        ) {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SetupRow(
                title = "Private DNS",
                subtitle = if (privateDnsStrict) {
                    "Strict Private DNS (DoT) bypasses Warden's DNS filter completely. Set it to Off."
                } else {
                    "Off is safest — Automatic may still use DoT on some carriers and skip the filter."
                },
                granted = !privateDnsStrict,
                actionLabel = "Open",
            ) {
                runCatching {
                    context.startActivity(Intent("android.settings.PRIVATE_DNS_SETTINGS"))
                }.onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun SetupRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (!granted) OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
