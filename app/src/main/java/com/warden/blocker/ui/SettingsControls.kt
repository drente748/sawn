package com.warden.blocker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.warden.blocker.system.AdminManager
import com.warden.blocker.ui.theme.WardenPalette

/**
 * أدوات الحماية والخصوصية التي احتاجتها الأقسام بعد التقسيم — PIN والحماية
 * من الإلغاء تعيش في [SettingsControls] وتُستدعى من SettingsSections.kt.
 */
object SettingsControls {
    @Composable
    fun PinCard(vm: WardenViewModel, strict: Boolean, hasPin: Boolean) {
        var showSetPin by remember { mutableStateOf(false) }
        var showRemovePin by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("قفل الرقم السري", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (hasPin) "يُطلب الرقم السري لكل تعديل إعدادات أو إنهاء للحماية."
                    else "احمِ الإعدادات وإنهاء الحماية برقم من 4 أرقام.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WardenPalette.TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSetPin = true }) { Text(if (hasPin) "تغيير الرقم" else "وضع رقماً") }
                    if (hasPin) {
                        OutlinedButton(onClick = { showRemovePin = true }, enabled = !strict) {
                            Text(if (strict) "أطفئ الوضع الصارم أولاً" else "إزالة الرقم")
                        }
                    }
                }
            }
        }
        if (showSetPin) {
            SetPinDialog(onDone = { vm.setPin(it, enableStrict = false) }, onDismiss = { showSetPin = false })
        }
        if (showRemovePin) {
            VerifyPinDialog(
                "أدخل الرقم لإزالته",
                vm::verifyPin,
                onSuccess = { vm.clearPin(); showRemovePin = false },
                onDismiss = { showRemovePin = false },
            )
        }
    }

    @Composable
    fun AdminProtectionCard(strict: Boolean) {
        val context = LocalContext.current
        var adminActive by remember { mutableStateOf(AdminManager.isActive(context)) }
        val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            adminActive = AdminManager.isActive(context)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("حماية من الإلغاء", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (adminActive) "مفعّلة — لا يمكن إلغاء Warden قبل تعطيل هذه الحماية."
                    else "مطفأة — يمكن إلغاء Warden في أي وقت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WardenPalette.TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                if (adminActive) {
                    OutlinedButton(
                        onClick = { if (!strict) { AdminManager.disable(context); adminActive = AdminManager.isActive(context) } },
                        enabled = !strict,
                    ) { Text(if (strict) "أطفئ الوضع الصارم أولاً" else "إطفاء الحماية") }
                } else {
                    Button(onClick = { adminLauncher.launch(AdminManager.enableIntent(context)) }) { Text("تفعيل الحماية") }
                }
            }
        }
    }
}
