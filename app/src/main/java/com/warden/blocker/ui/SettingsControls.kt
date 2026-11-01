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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.warden.blocker.system.AdminManager
import com.warden.blocker.block.LockdownPolicy
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

    /**
     * بطاقة القفل الزمني (قسم الحماية). القفل مطلق: لا رفع قبل الانتهاء.
     * التفعيل بخطوتين: بطاقة داعمة → شاشة Device Admin → الكتابة عند العودة الناجحة.
     */
    @Composable
    fun LockdownCard(vm: WardenViewModel) {
        val context = LocalContext.current
        val lockdownUntil by vm.lockdownUntil.collectAsState()
        val now = remember { mutableStateOf(System.currentTimeMillis()) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                now.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
        val active = LockdownPolicy.isActive(lockdownUntil, now.value)
        val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // موافقة admin (أو موجود سلفاً) → اكتب القفل؛ رفض/تراجع → أهدر القرار.
            if (AdminManager.isActive(context) && vm.hasStagedLockdown()) {
                vm.commitLockdown()
            } else {
                vm.discardStagedLockdown()
            }
        }

        var pendingEndsAt by remember { mutableStateOf<Long?>(null) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("قفل الحماية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (active) {
                    val remaining = lockdownUntil - now.value
                    Text(
                        formatRemaining(remaining),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = WardenPalette.GreenDark,
                    )
                    Text(
                        "القفل مطلق: لا يمكن إيقاف الحماية أو إزالة التطبيق قبل انتهاء المدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WardenPalette.TextMuted,
                    )
                } else {
                    Text(
                        "اختر مدة تُقفل فيها الحماية: لا إيقاف ولا حذف طوالها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WardenPalette.TextMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRowQuick {
                        listOf(
                            "30 دقيقة" to (30L to LockdownPolicy.DurationUnit.MINUTES),
                            "ساعة" to (1L to LockdownPolicy.DurationUnit.HOURS),
                            "6 ساعات" to (6L to LockdownPolicy.DurationUnit.HOURS),
                            "يوم" to (1L to LockdownPolicy.DurationUnit.DAYS),
                            "3 أيام" to (3L to LockdownPolicy.DurationUnit.DAYS),
                            "أسبوع" to (1L to LockdownPolicy.DurationUnit.WEEKS),
                            "أسبوعان" to (2L to LockdownPolicy.DurationUnit.WEEKS),
                            "شهر" to (1L to LockdownPolicy.DurationUnit.MONTHS),
                            "3 شهور" to (3L to LockdownPolicy.DurationUnit.MONTHS),
                            "6 شهور" to (6L to LockdownPolicy.DurationUnit.MONTHS),
                        ).forEach { (label, pair) ->
                            OutlinedButton(onClick = {
                                pendingEndsAt = System.currentTimeMillis() + pair.first * unitMillis(pair.second)
                            }) { Text(label) }
                        }
                    }
                }
            }
        }
        pendingEndsAt?.let { endsAt ->
            SupportiveConfirmDialog(
                vm = vm,
                endsAtMs = endsAt,
                onConfirmed = { confirmed ->
                    pendingEndsAt = null
                    vm.stageLockdown(confirmed)
                    if (AdminManager.isActive(context)) {
                        vm.commitLockdown()
                    } else {
                        adminLauncher.launch(AdminManager.enableIntent(context))
                    }
                },
            )
        }
    }

    private fun unitMillis(unit: LockdownPolicy.DurationUnit): Long = when (unit) {
        LockdownPolicy.DurationUnit.MINUTES -> 60_000L
        LockdownPolicy.DurationUnit.HOURS -> 3_600_000L
        LockdownPolicy.DurationUnit.DAYS -> 86_400_000L
        LockdownPolicy.DurationUnit.WEEKS -> 7 * 86_400_000L
        LockdownPolicy.DurationUnit.MONTHS -> 30L * 86_400_000L
    }

    fun formatRemaining(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val days = totalSec / 86_400
        val hours = totalSec % 86_400 / 3_600
        val minutes = totalSec % 3_600 / 60
        val seconds = totalSec % 60
        return if (days > 0) "متبقٍ ${days}ي ${hours}س" else "متبقٍ ${hours}س ${minutes}د ${seconds}ث"
    }

    /** أزرار المدة في صفّين تلقائيين. */
    @Composable
    private fun FlowRowQuick(content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
