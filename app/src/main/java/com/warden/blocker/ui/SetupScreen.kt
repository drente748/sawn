package com.warden.blocker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.warden.blocker.ui.theme.WardenPalette

/**
 * شاشة الإعداد بمعيار Android 13+ المنسجم: تسلسل مألوف، أزرار مباشرة، لحظة
 * «الإعدادات المقيدة» لبرامج التثبيت الخارجية، وطلب إشعارات runtime رسمي.
 * الحالة تُعاد قراءتها عند كل ON_RESUME — تعود من إعدادات النظام فتتحدث فوراً.
 */
@Composable
fun SetupScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_EXPRESSION") refresh

    val accessibility = PermissionsHelper.isAccessibilityEnabled(context)
    val usage = PermissionsHelper.hasUsageAccess(context)
    val notifications = PermissionsHelper.hasNotifications(context)
    val alwaysOnVpn = PermissionsHelper.isAlwaysOnVpnConfigured(context)
    val batteryFree = PermissionsHelper.isBatteryOptimizationIgnored(context)
    val privateDnsStrict = PermissionsHelper.isPrivateDnsStrict(context)

    // Android 13+ يمنع الأذونات الخاصة للنسخ المثبتة خارج المتاجر حتى يسمح المستخدم
    // بالإعدادات المقيدة من «معلومات التطبيق». التسلسل يبدأ بإتاحة المصدر.
    val restrictedNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !accessibility && PermissionsHelper.isSideloaded(context)

    // إشعارات 13+ تُطلب بنظام runtime الرسمي مباشرة (لا إعدادات).
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> refresh++ }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        BackHeader("الإعداد", onBack, "منح هذه الأذونات يجعل Warden يعمل كاملاً.")
        Spacer(Modifier.height(4.dp))
        Text(
            "التسلسل باقتراح معين: المطلوب أولاً، ثم ما يحفز الاستمرارية. الانتقال أحياناً لشاشة نظام ثم العودة — الحالة تتحدث تلقائياً.",
            style = MaterialTheme.typography.bodyMedium,
            color = WardenPalette.TextMuted,
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("أساسي")
        SetupRow(
            title = "حجب التطبيقات (إمكانية الوصول)",
            subtitle = "مطلوب — يرصد التطبيقات والمحتوى المحجوب داخل التطبيقات.",
            granted = accessibility,
            actionLabel = "فتح",
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        if (restrictedNeeded) {
            // بطاقة الإعدادات المقيدة: تصعد قبل الآخرين لأنها شرط إمكانية الوصول.
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = WardenPalette.Amber,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("السماح بالإعدادات المقيدة", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "أندرويد 13+ يكتم إمكانية الوصول من التطبيقات المثبتة من الخارج — خطوة واحدة تُلغي ذلك:",
                                style = MaterialTheme.typography.bodySmall,
                                color = WardenPalette.TextMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "1. افتح «معلومات التطبيق» من الزر أدناه" +
                            "\n2. اضغط قائمة (⋮) أعلى الشاشة" +
                            "\n3. اختر «السماح بالإعدادات المقيدة»",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }) { Text("فتح معلومات التطبيق") }
                }
            }
        }

        SectionLabel("إشعارات")
        SetupRow(
            title = "الإشعارات",
            subtitle = "تعرض إشعار «الحماية فعّالة» المستمر.",
            granted = notifications,
            actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "منح" else "فتح",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }

        SectionLabel("استمرارية الحماية")
        SetupRow(
            title = "الوصول لبيانات الاستخدام",
            subtitle = "لوقت الشاشة والحدود اليومية.",
            granted = usage,
            actionLabel = "فتح",
        ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        SetupRow(
            title = "إمكانية الوصول المهمة (VPN دائم)",
            subtitle = "أندرويد يُشغّل الفلتر تلقائياً بعد إعادة التشغيل. فعّل «دائماً» لـ Warden — وضِع «حجب الاتصالات بدون VPN» مطفأً.",
            granted = alwaysOnVpn,
            actionLabel = "فتح",
        ) { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }

        SetupRow(
            title = "تحسين البطارية",
            subtitle = "اختر Warden ← «بلا قيود» كي لا يقتل الهاتف الفلتر في الخلفية.",
            granted = batteryFree,
            actionLabel = "فتح",
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
                    "Private DNS الصارم يتخطى فلتر Warden تماماً. اضبطه على «إيقاف»."
                } else {
                    "«إيقاف» أأمن — «تلقائي» قد يستخدم DoT لدى بعض المشغّلين ويقفز فوق الفلتر."
                },
                granted = !privateDnsStrict,
                actionLabel = "فتح",
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
        color = WardenPalette.GreenDark,
        fontWeight = FontWeight.SemiBold,
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
                contentDescription = if (granted) "منح" else "غير منح",
                tint = if (granted) WardenPalette.Green else WardenPalette.TextMuted,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WardenPalette.TextMuted)
            }
            if (!granted) OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
