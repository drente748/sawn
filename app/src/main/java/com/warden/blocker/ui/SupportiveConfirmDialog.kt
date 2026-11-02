package com.warden.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warden.blocker.ui.theme.WardenPalette
import kotlinx.coroutines.delay

/**
 * البطاقة النفسية الداعمة: تظهر قبل تأكيد القفل (ومحاولات الإيقاف حيث يعني ذلك).
 * أسلوب داعم لا لائم — أرقام حقيقية فقط (سلسلة الأيام + جلسات التركيز)، ثم تأمّل
 * 10 ثوانٍ قبل تفعيل زر التأكيد.
 */
@Composable
fun SupportiveConfirmDialog(
    title: String = "قرار يحترم طريقك",
    bodyIntro: String = "هذا القرار لك وحده. قبل أن تُقفل الحماية:",
    confirmLabel: String = "تأكيد القفل",
    cancelLabel: String = "التراجع",
    vm: WardenViewModel,
    streak: Int = 0,
    sessions: Int = 0,
    endsAtMs: Long = 0L, // القفل: الأزرار تحسبها قبل الاستدعاء
    onExceptionalConfirmLabel: String = "",
    onDismiss: () -> Unit = {},
    onConfirmed: (Long) -> Unit,
) {
    // سبب القفل: الأزرار تمرر endsAtMs أما الإيقاف فلا يلزم قيمة.
    var reflectSeconds by remember { mutableLongStateOf(10L) }
    LaunchedEffect(Unit) {
        while (reflectSeconds > 0) {
            delay(1000)
            reflectSeconds--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bodyIntro, style = MaterialTheme.typography.bodyMedium)
                if (streak > 0 || sessions > 0) {
                    Text(
                        buildString {
                            append("واقعك حتى الآن: ")
                            if (streak > 0) append("سلسلة $streak يوم")
                            if (streak > 0 && sessions > 0) append(" و ")
                            if (sessions > 0) append("$sessions جلسة تركيز")
                            append(".")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = WardenPalette.GreenDark,
                    )
                }
                Text(
                    "كل يوم خارج الإباحية يعيد للنوم صفاءً ويعيد للتركيز عمقه — قيمتك هي نوع الحياة التي تختارها.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (reflectSeconds > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "خذ لحظة تأمّل… (${reflectSeconds}ث)",
                        style = MaterialTheme.typography.labelMedium,
                        color = WardenPalette.TextMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reflectSeconds <= 0,
                onClick = { onConfirmed(endsAtMs) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بقي محمياً") }
        },
    )
}
