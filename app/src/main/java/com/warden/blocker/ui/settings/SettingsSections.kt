package com.warden.blocker.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warden.blocker.util.PermissionsHelper
import com.warden.blocker.block.web.BrowserConfig
import com.warden.blocker.ui.BackHeader
import com.warden.blocker.ui.WardenViewModel
import com.warden.blocker.ui.BackHeader
import com.warden.blocker.ui.theme.WardenPalette

/**
 * أقسام الإعدادات الثلاثة (مواصفة 2026-11-01): الحماية / المتصفح / الخصوصية والدعم.
 * كل شاشة WRAPPER فوق [SettingsScreen] بوضع router=false، فتستفيد من بوابة الـ PIN
 * التي تبقى خصوصية إدارة الإعدادات.
 */

@Composable
fun ProtectionSettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    SettingsScreenHost(vm, onOpenSetup, onBack) {
        val pornGuard by vm.pornGuard.collectAsState()
        val safeSearchForce by vm.safeSearchForce.collectAsState()
        val blockDoh by vm.blockDoh.collectAsStateWithLifecycle()
        val blockAds by vm.blockAds.collectAsStateWithLifecycle()
        val alwaysOn by vm.alwaysOn.collectAsStateWithLifecycle()
        val strict by vm.strictMode.collectAsStateWithLifecycle()
        val pinConfigured by vm.pinConfigured.collectAsStateWithLifecycle()
        val hasPin = pinConfigured == true

        SwitchRow(
            title = "فلتر حجب الإباحية",
            subtitle = "يحجب المواقع الإباحية في كل المتصفحات والتطبيقات، عبر الفلتر المحلي ومصادر جماعية.",
            checked = pornGuard,
            onChange = { vm.setPornGuard(it) },
        )
        SwitchRow(
            title = "فرض SafeSearch (DNS)",
            subtitle = "يقفل نتائج البحث والصور والفيديو الحساسة على Google وBing وYouTube وDuckDuckGo من خوادمها نفسها.",
            checked = safeSearchForce,
            onChange = { vm.setSafeSearchForce(it) },
        )
        SwitchRow(
            title = "الوضع الدائم",
            subtitle = if (alwaysOn) "الحجب يعمل دوماً عند تفعيله." else "الحجب يعمل فقط خلال الجداول الزمنية.",
            checked = alwaysOn,
            onChange = { vm.setAlwaysOn(it) },
        )
        SwitchRow(
            title = "حجب DNS-over-HTTPS",
            subtitle = "يحجب أيضاً محلّلات DNS المشفّرة كي لا تتخطى المتصفحات فلترة المواقع. يسري عند بدء الحجب التالي.",
            checked = blockDoh,
            onChange = { vm.setBlockDoh(it) },
        )
        SwitchRow(
            title = "حجب الإعلانات والمتتبعات",
            subtitle = "يحجب مضيفي الإعلانات والتحليلات الشائعة. الإعلانات التي يقدّمها الموقع نفسه قد تظهر.",
            checked = blockAds,
            onChange = { vm.setBlockAds(it) },
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("الوضع الصارم", style = MaterialTheme.typography.titleMedium)
                Text("يقفل مفتاح الحماية الرئيسي طوال عملها — يتطلب رقماً سرياً.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Switch(checked = strict, onCheckedChange = { on ->
                    if (on && !hasPin) {
                        // Enable-strict-after-PIN: مثل السلوك الأصلي في 0.x.
                        vm.setStrictMode(false)
                    } else {
                        vm.setStrictMode(on)
                    }
                })
            }
        }
        com.warden.blocker.ui.SettingsControls.LockdownCard(vm = vm)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("قوائم الحجب الجماعية", style = MaterialTheme.typography.titleMedium)
                Text(
                    "تُسحب قوائم PornAway وStevenBlack وتُدمج في الفلتر — حدّثها يدوياً بانتظام.",
                    style = MaterialTheme.typography.bodySmall,
                )
                var refreshMsg by remember { mutableStateOf<String?>(null) }
                var refreshFailed by remember { mutableStateOf(false) }
                TextButton(onClick = {
                    vm.refreshAdultLists { result ->
                        refreshFailed = result.error != null
                        refreshMsg = when {
                            result.error != null -> "تعذّر التحديث — بقيت القوائم السابقة."
                            result.skippedSameContent -> "القوائم محدثة بالفعل."
                            else -> "تم التحديث — أضيف ${result.added} نطاقاً جديداً."
                        }
                    }
                }) { Text("تحديث القوائم الآن") }
                refreshMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (refreshFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun BrowserSettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScreenHost(vm, onOpenSetup, onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Accessibility, contentDescription = null, tint = WardenPalette.Green)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("إمكانية الوصول", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "تراقب شريط عنوان المتصفح لتصيد المواقع التي تتخطى الفلتر — لا تخزّن أي نص.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val granted = PermissionsHelper.isAccessibilityEnabled(context)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (granted) "الحالة: مفعّل ✓" else "الحالة: غير مفعّل",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (granted) WardenPalette.GreenDark else MaterialTheme.colorScheme.error,
                )
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenSetup) { Text("تفعيل الآن") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("المتصفحات المدعومة", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${BrowserConfig.ALL.size} متصفحاً مدعوماً — يعمل تلقائياً بدون ضبط إضافي.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("تلميحات التلافي (DoH)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "إذا استخدم متصفحك DNS-مشفّراً (DoH) فسيقاوم الفلتر — فعّل «حجب DNS-over-HTTPS» من قسم الحماية ليُحجب المشفّر أيضاً.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun PrivacySupportSettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion by vm.reduceMotion.collectAsState()
    val strict by vm.strictMode.collectAsState()
    val pinConfigured by vm.pinConfigured.collectAsState()
    val blockNotifications by vm.blockNotifications.collectAsState()
    val hasPin = pinConfigured == true
    SettingsScreenHost(vm, onOpenSetup, onBack) {
        com.warden.blocker.ui.SettingsControls.PinCard(vm = vm, strict = strict, hasPin = hasPin)
        com.warden.blocker.ui.SettingsControls.AdminProtectionCard(strict = strict)
        SwitchRow(
            title = "حجب الإشعارات",
            subtitle = "يكتم إشعارات التطبيقات المحجوبة طوال عمل الحجب.",
            checked = blockNotifications,
            onChange = { vm.setBlockNotifications(it) },
        )
        if (blockNotifications) {
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text("منح إذن الإشعارات")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = WardenPalette.Green)
                    Spacer(Modifier.width(10.dp))
                    Text("خصوصيتك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "لا نجمع أي بيانات — لا سيرفر، لا حساب، لا تسجيل. كل شيء يعمل على جهازك، وكل القوائم تُسحب من مصادر مفتوحة.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = WardenPalette.Amber)
                    Spacer(Modifier.width(10.dp))
                    Text("أنا أحتاج مساعدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "نقطة يعاينها المستخدم لطلب مساعدة فورية — نفتح صفحة أزمات مفتوحة في متصفحك.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://findahelpline.com/ar")))
                    } catch (_: Exception) {
                    }
                }) { Text("فتح صفحة المساعدة") }
            }
        }

        SwitchRow(
            title = "تقليل الحركة",
            subtitle = "يوقف نبض الدرع في الصفحة الرئيسية — مناسب لمن يفضل واجهة ثابتة.",
            checked = reduceMotion,
            onChange = { vm.setReduceMotion(it) },
        )

        HorizontalDivider()
        Text(
            "Warden 1.0.0 (12)",
            style = MaterialTheme.typography.bodySmall,
            color = WardenPalette.TextMuted,
        )
    }
}

/** Wrap over SettingsScreen المعاد هيكلته — router=false. التمرير والحاشية يوفرهما SettingsScreen نفسه. */
@Composable
private fun SettingsScreenHost(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit, content: @Composable () -> Unit) {
    com.warden.blocker.ui.SettingsScreen(
        vm = vm,
        onOpenSetup = onOpenSetup,
        onBack = onBack,
        router = false,
        content = { content() },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WardenPalette.TextMuted)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
