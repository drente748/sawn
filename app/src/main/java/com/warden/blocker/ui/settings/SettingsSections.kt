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
        val pornGuard by vm.safeSearchForce.collectAsState()

        SwitchRow(
            title = "فلتر حجب الإباحية",
            subtitle = "يحجب المواقع الإباحية في كل المتصفحات والتطبيقات، عبر الفلتر المحلي ومصادر جماعية محدثة يومياً.",
            checked = pornGuard,
            onChange = { vm.setPornGuard(it) },
        )
        SwitchRow(
            title = "فرض SafeSearch (DNS)",
            subtitle = "يقفل نتائج البحث والصور والفيديو الحساسة على Google وBing وYouTube وDuckDuckGo من خوادمها نفسها.",
            checked = vm.safeSearchForce.collectAsState().value,
            onChange = { vm.setSafeSearchForce(it) },
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("قوائم الحجب الجماعية", style = MaterialTheme.typography.titleMedium)
                Text(
                    "تُسحب قوائمPornAway وStevenBLack يومياً وتُدمج في الفلتر.",
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
    SettingsScreenHost(vm, onOpenSetup, onBack) {
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

/** Wrap over SettingsScreen المعاد هيكلته — router=false. */
@Composable
private fun SettingsScreenHost(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit, content: @Composable () -> Unit) {
    com.warden.blocker.ui.SettingsScreen(
        vm = vm,
        onOpenSetup = onOpenSetup,
        onBack = onBack,
        router = false,
        content = {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        },
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
