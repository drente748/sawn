package com.warden.blocker.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.warden.blocker.system.AdminManager
import com.warden.blocker.block.LockdownPolicy
import com.warden.blocker.ui.theme.WardenPalette
import com.warden.blocker.vpn.VpnController

/**
 * أدوات الحماية والخصوصية التي احتاجتها الأقسام بعد التقسيم — PIN والحماية
 * من الإلغاء تعيش في [SettingsControls] وتُستدعى من SettingsSections.kt.
 */
object SettingsControls {
    @Composable
    fun PinCard(vm: WardenViewModel, strict: Boolean, hasPin: Boolean) {
        val lockdownActive by vm.lockdownActive.collectAsState()
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
                        OutlinedButton(onClick = { showRemovePin = true }, enabled = !strict && !lockdownActive) {
                            Text(
                                when {
                                    lockdownActive -> "القفل الزمني نشط"
                                    strict -> "أطفئ الوضع الصارم أولاً"
                                    else -> "إزالة الرقم"
                                }
                            )
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
    fun AdminProtectionCard(strict: Boolean, vm: WardenViewModel) {
        val context = LocalContext.current
        val lockdownActive by vm.lockdownActive.collectAsState()
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
                        enabled = !strict && !lockdownActive,
                    ) {
                        Text(
                            when {
                                lockdownActive -> "القفل الزمني نشط"
                                strict -> "أطفئ الوضع الصارم أولاً"
                                else -> "إطفاء الحماية"
                            }
                        )
                    }
                } else {
                    Button(onClick = { adminLauncher.launch(AdminManager.enableIntent(context)) }) { Text("تفعيل الحماية") }
                }
            }
        }
    }

    /**
     * بطاقة القفل الزمني (قسم الحماية). القفل مطلق: لا رفع قبل الانتهاء.
     * التفعيل بخطوتين: بطاقة داعمة → إذن الفلتر → شاشة Device Admin → الكتابة بعد
     * التأكد من أن الحجب يعمل فعلاً. لا تُكتب قيمة قبل نجاح السلسلة كلها.
     */
    @Composable
    fun LockdownCard(vm: WardenViewModel) {
        val context = LocalContext.current
        val lockdownActive by vm.lockdownActive.collectAsState()
        val remaining by vm.lockdownRemaining.collectAsState()
        val streak by vm.currentStreak.collectAsState()
        val sessions by vm.focusSessionsDone.collectAsState()

        var pendingDurationMs by remember { mutableStateOf<Long?>(null) }
        var showNewLockOptions by remember { mutableStateOf(false) }
        var manualAmount by remember { mutableStateOf("") }
        var manualUnit by remember { mutableStateOf(LockdownPolicy.DurationUnit.MINUTES) }
        var manualError by remember { mutableStateOf(false) }
        var activationError by remember { mutableStateOf<String?>(null) }

        fun commitStaged() {
            vm.commitLockdown { success ->
                if (success) {
                    activationError = null
                    showNewLockOptions = false
                } else {
                    activationError = "تعذّر تشغيل فلتر الحماية — لم يُكتب القفل. حاول مرة أخرى."
                }
            }
        }

        val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // موافقة admin (أو موجود سلفاً) → أكمل؛ رفض/تراجع → أهدر القرار.
            if (AdminManager.isActive(context) && vm.hasStagedLockdown()) {
                commitStaged()
            } else {
                vm.discardStagedLockdown()
                activationError = "لم تُفعّل حماية الإلغاء — لم يُكتب القفل."
            }
        }

        val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (AdminManager.isActive(context)) commitStaged()
                else adminLauncher.launch(AdminManager.enableIntent(context))
            } else {
                vm.discardStagedLockdown()
                activationError = "لم يُمنح إذن الفلتر — لم يُكتب القفل."
            }
        }

        fun continueActivation() {
            val consent = VpnController.consentIntent(context)
            when {
                consent != null -> vpnLauncher.launch(consent)
                AdminManager.isActive(context) -> commitStaged()
                else -> adminLauncher.launch(AdminManager.enableIntent(context))
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("قفل الحماية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (lockdownActive) {
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
                    if (!showNewLockOptions) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            activationError = null
                            showNewLockOptions = true
                        }) { Text("قفل جديد") }
                    }
                } else {
                    Text(
                        "اختر مدة تُقفل فيها الحماية: لا إيقاف ولا حذف طوالها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WardenPalette.TextMuted,
                    )
                }

                if (!lockdownActive || showNewLockOptions) {
                    if (lockdownActive) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "القفل الجديد يبدأ من الآن ويستبدل المدة الحالية بالكامل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WardenPalette.Amber,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    QuickDurationGrid { durationMs ->
                        activationError = null
                        pendingDurationMs = durationMs
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "أو مدة مخصصة (1 دقيقة – 12 شهراً)",
                        style = MaterialTheme.typography.bodySmall,
                        color = WardenPalette.TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualAmount,
                        onValueChange = {
                            // نقبل الأرقام العربية/الفارسية ونحوّلها فوراً إلى ASCII،
                            // لأن toLongOrNull لا يفهم ٠١٢.
                            val normalized = LockdownPolicy.normalizeDigits(it)
                            if (normalized.length <= 5 && normalized.all { c -> c in '0'..'9' }) {
                                manualAmount = normalized
                                manualError = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = manualError,
                        label = { Text("المدة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LockdownPolicy.DurationUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = manualUnit == unit,
                                onClick = { manualUnit = unit },
                                label = { Text(unitLabel(unit)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val millis = LockdownPolicy.validateManual(
                                LockdownPolicy.parseManualAmount(manualAmount) ?: 0L,
                                manualUnit,
                            )
                            if (millis == null) {
                                manualError = true
                            } else {
                                manualError = false
                                activationError = null
                                pendingDurationMs = millis
                            }
                        },
                        enabled = manualAmount.isNotBlank(),
                    ) { Text("تثبيت المدة المخصصة") }
                    if (manualError) {
                        Text(
                            "المدة يجب أن تكون بين دقيقة و12 شهراً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                activationError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        pendingDurationMs?.let { durationMs ->
            SupportiveConfirmDialog(
                vm = vm,
                streak = streak,
                sessions = sessions,
                onDismiss = { pendingDurationMs = null },
                onConfirmed = {
                    pendingDurationMs = null
                    activationError = null
                    vm.stageLockdownForDuration(durationMs)
                    continueActivation()
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

    private fun unitLabel(unit: LockdownPolicy.DurationUnit): String = when (unit) {
        LockdownPolicy.DurationUnit.MINUTES -> "دقائق"
        LockdownPolicy.DurationUnit.HOURS -> "ساعات"
        LockdownPolicy.DurationUnit.DAYS -> "أيام"
        LockdownPolicy.DurationUnit.WEEKS -> "أسابيع"
        LockdownPolicy.DurationUnit.MONTHS -> "شهور"
    }

    private data class QuickDuration(
        val label: String,
        val amount: Long,
        val unit: LockdownPolicy.DurationUnit,
    )

    private val quickDurations = listOf(
        QuickDuration("30 دقيقة", 30L, LockdownPolicy.DurationUnit.MINUTES),
        QuickDuration("ساعة", 1L, LockdownPolicy.DurationUnit.HOURS),
        QuickDuration("6 ساعات", 6L, LockdownPolicy.DurationUnit.HOURS),
        QuickDuration("يوم", 1L, LockdownPolicy.DurationUnit.DAYS),
        QuickDuration("3 أيام", 3L, LockdownPolicy.DurationUnit.DAYS),
        QuickDuration("أسبوع", 1L, LockdownPolicy.DurationUnit.WEEKS),
        QuickDuration("أسبوعان", 2L, LockdownPolicy.DurationUnit.WEEKS),
        QuickDuration("شهر", 1L, LockdownPolicy.DurationUnit.MONTHS),
        QuickDuration("3 شهور", 3L, LockdownPolicy.DurationUnit.MONTHS),
        QuickDuration("6 شهور", 6L, LockdownPolicy.DurationUnit.MONTHS),
    )

    /** أزرار المدة في شبكة عمودين. */
    @Composable
    private fun QuickDurationGrid(onPick: (Long) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickDurations.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        OutlinedButton(
                            onClick = { onPick(item.amount * unitMillis(item.unit)) },
                            modifier = Modifier.weight(1f),
                        ) { Text(item.label) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    fun formatRemaining(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val days = totalSec / 86_400
        val hours = totalSec % 86_400 / 3_600
        val minutes = totalSec % 3_600 / 60
        val seconds = totalSec % 60
        return if (days > 0) "متبقٍ ${days}ي ${hours}س" else "متبقٍ ${hours}س ${minutes}د ${seconds}ث"
    }
}
