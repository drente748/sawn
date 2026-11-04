package com.warden.blocker.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.BuildConfig
import com.warden.blocker.R
import com.warden.blocker.block.web.BrowserConfig
import com.warden.blocker.ui.SettingsControls
import com.warden.blocker.ui.SettingsSectionScreen
import com.warden.blocker.ui.WardenViewModel
import com.warden.blocker.ui.rememberResumeTick
import com.warden.blocker.util.PermissionsHelper
import kotlinx.coroutines.launch

/**
 * أقسام الإعدادات الثلاثة: الحماية / المتصفح / الخصوصية والدعم.
 * بوابة الرقم السري واحدة يملكها MainActivity عبر ManagementPinGate، فلا تكرر هذه
 * الشاشات طلب الرقم.
 */
private const val HELPLINE_URL = "https://findahelpline.com/ar"

@Composable
fun ProtectionSettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pornGuard by vm.pornGuard.collectAsStateWithLifecycle()
    val safeSearchForce by vm.safeSearchForce.collectAsStateWithLifecycle()
    val blockDoh by vm.blockDoh.collectAsStateWithLifecycle()
    val blockAds by vm.blockAds.collectAsStateWithLifecycle()
    val alwaysOn by vm.alwaysOn.collectAsStateWithLifecycle()
    val strict by vm.strictMode.collectAsStateWithLifecycle()
    val pinConfigured by vm.pinConfigured.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
    val hasPin = pinConfigured == true

    var showPinForStrict by remember { mutableStateOf(false) }
    var strictFailed by remember { mutableStateOf(false) }

    SettingsSectionScreen(stringResource(R.string.settings_protection), onBack) {
        if (lockdownActive) {
            InfoCard(
                title = stringResource(R.string.lockdown_active_title),
                body = stringResource(R.string.lockdown_active_body),
                tinted = true,
            )
        }

        SwitchRow(
            title = stringResource(R.string.porn_guard_title),
            subtitle = stringResource(R.string.porn_guard_sub),
            checked = pornGuard,
            enabled = !lockdownActive,
            onChange = vm::setPornGuard,
        )
        SwitchRow(
            title = stringResource(R.string.safe_search_title),
            subtitle = stringResource(R.string.safe_search_sub),
            checked = safeSearchForce,
            enabled = !lockdownActive,
            onChange = vm::setSafeSearchForce,
        )
        SwitchRow(
            title = stringResource(R.string.always_on_title),
            subtitle = stringResource(if (alwaysOn) R.string.always_on_on else R.string.always_on_off),
            checked = alwaysOn,
            enabled = !lockdownActive,
            onChange = vm::setAlwaysOn,
        )
        SwitchRow(
            title = stringResource(R.string.doh_title),
            subtitle = stringResource(R.string.doh_sub),
            checked = blockDoh,
            enabled = !lockdownActive,
            onChange = vm::setBlockDoh,
        )
        SwitchRow(
            title = stringResource(R.string.ads_title),
            subtitle = stringResource(R.string.ads_sub),
            checked = blockAds,
            enabled = !lockdownActive,
            onChange = vm::setBlockAds,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.strict_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.strict_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = strict,
                        // أثناء القفل الزمني يُمنع إضعاف الوضع الصارم.
                        enabled = !lockdownActive || strict,
                        onCheckedChange = { on ->
                            strictFailed = false
                            when {
                                on && !hasPin -> showPinForStrict = true
                                else -> scope.launch { strictFailed = !vm.setStrictMode(on) }
                            }
                        },
                    )
                }
                if (strictFailed) {
                    Text(
                        stringResource(R.string.strict_enable_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        SettingsControls.LockdownCard(vm = vm)
        AdultListsCard(vm)
    }

    // الضرب رقماً سرياً ثم تفعيل الوضع الصارم خطوة واحدة — بلا تحويل صامت يفشل سرّاً.
    if (showPinForStrict) {
        SettingsControls.PinSetup(vm = vm, enableStrictAfter = true, onDismiss = { showPinForStrict = false })
    }
}

@Composable
private fun AdultListsCard(vm: WardenViewModel) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.lists_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.lists_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    // ردّ التحديث يصل خارج التراكيب، فيُقرأ النص من السياق لا من stringResource.
                    vm.refreshAdultLists { result ->
                        busy = false
                        failed = result.error != null
                        message = when {
                            result.error != null -> context.getString(R.string.lists_update_failed)
                            result.skippedSameContent -> context.getString(R.string.lists_up_to_date)
                            else -> context.getString(R.string.lists_updated, result.added)
                        }
                    }
                },
            ) {
                Text(stringResource(if (busy) R.string.lists_updating else R.string.lists_update_action))
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun BrowserSettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val accessibilityOn = remember(resumeTick) { PermissionsHelper.isAccessibilityEnabled(context) }

    SettingsSectionScreen(stringResource(R.string.settings_browser), onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Accessibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.a11y_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.a11y_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (accessibilityOn) R.string.a11y_on else R.string.a11y_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accessibilityOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (!accessibilityOn) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.grant_now))
                    }
                }
            }
        }

        InfoCard(
            title = stringResource(R.string.browsers_title),
            body = stringResource(R.string.browsers_count, BrowserConfig.ALL.size),
        )
        InfoCard(
            title = stringResource(R.string.doh_hint_title),
            body = stringResource(R.string.doh_hint_body),
        )
    }
}

@Composable
fun PrivacySupportSettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
    val strict by vm.strictMode.collectAsStateWithLifecycle()
    val pinConfigured by vm.pinConfigured.collectAsStateWithLifecycle()
    val blockNotifications by vm.blockNotifications.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
    val resumeTick = rememberResumeTick()
    val notificationsGranted = remember(resumeTick) { PermissionsHelper.hasNotifications(context) }
    val hasPin = pinConfigured == true

    SettingsSectionScreen(stringResource(R.string.settings_privacy), onBack) {
        SettingsControls.PinCard(vm = vm, strict = strict, hasPin = hasPin)
        SettingsControls.AdminProtectionCard(strict = strict, vm = vm)

        SwitchRow(
            title = stringResource(R.string.notifications_title),
            subtitle = stringResource(R.string.notifications_sub),
            checked = blockNotifications,
            enabled = !lockdownActive,
            onChange = vm::setBlockNotifications,
        )
        if (blockNotifications && !notificationsGranted) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.notifications_grant)) }
        }

        InfoCard(title = stringResource(R.string.privacy_title), body = stringResource(R.string.privacy_body))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.help_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HELPLINE_URL)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.help_action)) }
            }
        }

        SwitchRow(
            title = stringResource(R.string.reduce_motion_title),
            subtitle = stringResource(R.string.reduce_motion_sub),
            checked = reduceMotion,
            onChange = vm::setReduceMotion,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, tinted: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = if (tinted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
    }
}
