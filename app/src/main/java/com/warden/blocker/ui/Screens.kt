package com.warden.blocker.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft

import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.foundation.Canvas

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.block.AdultHosts
import com.warden.blocker.data.BlockType
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.data.InterceptMode
import com.warden.blocker.data.Schedule
import com.warden.blocker.system.AdminManager
import com.warden.blocker.ui.ads.AdSlot
import com.warden.blocker.ui.ads.AdSlotBanner
import com.warden.blocker.ui.status.CoverageLevel
import com.warden.blocker.ui.status.CoverageState
import com.warden.blocker.ui.status.coverageLevel
import com.warden.blocker.ui.theme.WardenPalette
import com.warden.blocker.usage.AppsHelper
import com.warden.blocker.usage.UsageStatsHelper
import com.warden.blocker.util.PermissionsHelper
import com.warden.blocker.vpn.WardenVpnService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * الشاشة الرئيسية — تصميم «نافذة الصباح» (v2):
 * بطل واحد كبير (حلقة الحماية الحيّة)، تحته مفتاح الحماية، ثم إجراءات هادئة،
 * ثم أزرار الإدارة، ثم المردود. أبيض هادئ + أخضر أمان، بلا تدرجات ولا
 * بطاقات متلونة. عربي RTL.
 */
@Composable
fun HomeScreen(
    vm: WardenViewModel,
    onToggleBlocking: (Boolean) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onCancelFocus: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenBlocklist: () -> Unit = {},
    onOpenSchedules: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val needsSetup = !PermissionsHelper.essentialsGranted(context)
    val enabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val bestStreak by vm.longestStreak.collectAsStateWithLifecycle()
    val sessions by vm.focusSessionsDone.collectAsStateWithLifecycle()
    val focusEndsAt by vm.focusEndsAt.collectAsStateWithLifecycle()
    val focusStrict by vm.focusStrict.collectAsStateWithLifecycle()
    val strictLocked by vm.strictLocked.collectAsStateWithLifecycle()
    val settingsReady by vm.settingsReady.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val pornGuard by vm.pornGuard.collectAsStateWithLifecycle()
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
    val lockdownRemaining by vm.lockdownRemaining.collectAsStateWithLifecycle()
    val safeSearchForce by vm.safeSearchForce.collectAsStateWithLifecycle()
    val vpnRunning by WardenVpnService.runningFlow.collectAsStateWithLifecycle()
    val coverage = coverageLevel(
        CoverageState(
            vpn = vpnRunning,
            accessibility = !needsSetup,
            safeSearchDns = safeSearchForce && pornGuard,
        ),
    )
    val activeCount = items.count { it.enabled }
    var showVerify by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focusEndsAt) {
        while (focusEndsAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val focusActive = focusEndsAt > now
    val blockingNow = enabled || focusActive || lockdownActive
    val lockedNow = !settingsReady || strictLocked || (focusActive && focusStrict)

    Column(
        Modifier
            .fillMaxSize()
            .background(WardenPalette.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── الترويسة: الاسم + الإعدادات ───────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Warden",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = WardenPalette.Zaytoun,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(WardenPalette.Halek),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "الإعدادات", tint = WardenPalette.Safeen, modifier = Modifier.size(21.dp))
            }
        }

        // ── البطل: حلقة الحماية الحيّة ───────────────────────────────────
        GuardianRing(
            blockingNow = blockingNow,
            needsSetup = needsSetup,
            coverage = coverage,
            lockdownActive = lockdownActive,
            lockdownRemaining = lockdownRemaining,
            reduceMotion = reduceMotion,
            streak = streak,
        )

        // ── تحذير واحد فقط عند الحاجة ────────────────────────────────────
        when {
            needsSetup -> QuietActionRow(
                text = "أكمل الإعداد لتفعيل الحماية الكاملة",
                onClick = onOpenSetup,
                urgent = false,
            )
            settingsReady && blockingNow && !vpnRunning -> QuietActionRow(
                text = "الفلتر توقف — اضغط لإعادة تشغيله",
                onClick = { onToggleBlocking(true) },
                urgent = true,
            )
        }

        // ── مفتاح الحماية الرئيسي ────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = WardenPalette.Card,
            border = BorderStroke(1.dp, WardenPalette.Fasal),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "الحماية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WardenPalette.Zaytoun,
                    )
                    Text(
                        if (blockingNow) "مفعّلة الآن — كل شيء تحت الحرس." else "مطفأة. اضغط للتشغيل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WardenPalette.Ghaim,
                    )
                }
                HoldToGuardSwitch(
                    enabled = enabled,
                    hasPin = hasPin,
                    locked = lockedNow,
                    lockLabel = if (focusActive && focusStrict) "جلسة صارمة جارية" else if (focusActive) "مقفل خلال الجلسة" else "قفل صارم مفعّل",
                    lockdownActive = lockdownActive,
                    onToggle = onToggleBlocking,
                    onPinRequired = { showVerify = true },
                )
            }
        }

        // ── جلسة التركيز ────────────────────────────────────────────────
        FocusCard(focusEndsAt = focusEndsAt, strict = focusStrict, onStart = onStartFocus, onCancel = onCancelFocus)

        // ── الإدارة: شبكة 2×2 هادئة ─────────────────────────────────────
        Text(
            "الإدارة",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = WardenPalette.Ghaim,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuietTile(Modifier.weight(1f), Icons.Filled.Block, "المواقع والتطبيقات",
                if (activeCount > 0) "$activeCount مُحجوب" else "لا شيء بعد", onOpenBlocklist)
            QuietTile(Modifier.weight(1f), Icons.Filled.Schedule, "الجداول", "تشغيل تلقائي", onOpenSchedules)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuietTile(Modifier.weight(1f), Icons.Filled.Insights, "الاستخدام", "يومك بنظرة", onOpenStats)
            QuietTile(Modifier.weight(1f), Icons.Filled.Settings, "الإعدادات", "الأقسام الكاملة", onOpenSettings)
        }

        // ── المردود: صف مضغوط ──────────────────────────────────────────
        Text(
            "مردودك",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = WardenPalette.Ghaim,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill(Modifier.weight(1f), "$streak", if (streak == 1) "يوم متصل" else "أيام متصلة")
            StatPill(Modifier.weight(1f), "$bestStreak", "الأطول")
            StatPill(Modifier.weight(1f), "$sessions", "جلسة تركيز")
        }

        AdSlotBanner(slot = AdSlot.HOME_BANNER)
        Spacer(Modifier.height(8.dp))
    }

    if (showVerify) {
        VerifyPinDialog(
            title = "أدخل الرقم لإيقاف الحماية",
            verify = vm::verifyPin,
            onSuccess = { showVerify = false; onToggleBlocking(false) },
            onDismiss = { showVerify = false },
        )
    }
}

/** البطل: حلقة حماية دائرية كبيرة، حالة الحماية في مركزها، تتنفس بهدوء. */
@Composable
private fun GuardianRing(
    blockingNow: Boolean,
    needsSetup: Boolean,
    coverage: CoverageLevel,
    lockdownActive: Boolean,
    lockdownRemaining: Long,
    reduceMotion: Boolean,
    streak: Int,
) {
    val infinite = rememberInfiniteTransition(label = "ringBreath")
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringAlpha",
    )
    val breath = if (reduceMotion || !blockingNow) 1f else ringAlpha

    val ringColor = when {
        needsSetup -> WardenPalette.Ramil
        blockingNow && coverage == CoverageLevel.Full -> WardenPalette.Safeen
        blockingNow -> WardenPalette.Safeen
        else -> WardenPalette.Fasal
    }
    val centerMark = when {
        needsSetup -> "…"
        blockingNow -> "✓"
        else -> "○"
    }
    val headline = when {
        needsSetup -> "لنكمل الإعداد"
        lockdownActive -> "مقفل زمنياً"
        blockingNow -> "أنت محميّ"
        else -> "الحماية مطفأة"
    }
    val subline = when {
        needsSetup -> "امنح إذن إمكانية الوصول لتبدأ الحماية"
        lockdownActive -> "الحماية والحذف ممنوعان — ${SettingsControls.formatRemaining(lockdownRemaining)}"
        blockingNow && coverage == CoverageLevel.Full -> "كل طبقات الحماية تعمل الآن"
        blockingNow -> "طبقة أو أكثر تحتاج انتباهك من الإعدادات"
        else -> "اضغط مفتاح الحماية أدناه لتفعيلها"
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = WardenPalette.Halek,
        border = BorderStroke(1.dp, WardenPalette.Fasal),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                Canvas(Modifier.fillMaxSize().scale(breath)) {
                    drawCircle(color = ringColor, style = Stroke(width = 7.dp.toPx()))
                    drawCircle(color = WardenPalette.Card, radius = size.minDimension / 2f - 14.dp.toPx())
                }
                Text(
                    centerMark,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ringColor,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = WardenPalette.Zaytoun,
                )
                Text(
                    subline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WardenPalette.Ghaim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (blockingNow && streak > 0) {
                    Text(
                        if (streak == 1) "يوم واحد متصل بلا رجوع" else "$streak أيام متصلة بلا رجوع",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = WardenPalette.Safeen,
                    )
                }
            }
        }
    }
}

/** صف إجراء هادئ واحد — بديل بطاقات التحذير الملوّنة السابقة. */
@Composable
private fun QuietActionRow(text: String, onClick: () -> Unit, urgent: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (urgent) WardenPalette.RamilSoft else WardenPalette.Halek)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (urgent) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (urgent) WardenPalette.Ramil else WardenPalette.Safeen,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = WardenPalette.Zaytoun,
        )
        Spacer(Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = WardenPalette.Ghaim, modifier = Modifier.size(18.dp))
    }
}

/** بلاطة إدارة هادئة: أيقونة واحدة ملوّنة، الباقي نص عربي نظيف. */
@Composable
private fun QuietTile(modifier: Modifier, icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = WardenPalette.Card,
        border = BorderStroke(1.dp, WardenPalette.Fasal),
        modifier = modifier.clickable { onClick() },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = WardenPalette.Safeen, modifier = Modifier.size(22.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = WardenPalette.Zaytoun,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = WardenPalette.Ghaim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** رقاقة إحصاء صغيرة — الرقم فقط، هادئ بلا أيقونة. */
@Composable
private fun StatPill(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(WardenPalette.Halek)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = WardenPalette.Zaytoun,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = WardenPalette.Ghaim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** Shared back header for every detail screen. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WardenPalette.BlueSoft),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = WardenPalette.Blue,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = WardenPalette.Ink,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WardenPalette.Muted)
            }
        }
    }
}

/** Gentle press feedback: 0.96 scale while touched, springs back on release. */
@Composable
private fun Modifier.scaleOnPress(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 420f),
        label = "pressScale",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .scale(scale)
}

private const val GUARD_HOLD_MS = 900

/**
 * The master switch as a hold-to-act pill: turning the guard OFF (the impulsive direction)
 * requires a deliberate 0.9 s hold; turning it ON stays a simple tap. Built on
 * detectTapGestures' onPress lifecycle (down → up-or-CANCEL), which needs no raw pointer
 * plumbing and cannot misfire.
 */
@Composable
private fun HoldToGuardSwitch(
    enabled: Boolean,
    hasPin: Boolean,
    locked: Boolean,
    lockLabel: String,
    lockdownActive: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onPinRequired: () -> Unit,
) {
    if (lockdownActive) {
        Text(
            "🔒 مقفل بالقفل الزمني",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WardenPalette.GreenDark,
        )
        return
    }
    if (locked) {
        Text(
            lockLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WardenPalette.Blue,
        )
        return
    }
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(holding) {
        if (!holding) { progress = 0f; return@LaunchedEffect }
        val start = System.currentTimeMillis()
        while (holding) {
            progress = ((System.currentTimeMillis() - start) / GUARD_HOLD_MS.toFloat()).coerceIn(0f, 1f)
            if (progress >= 1f) { holding = false; progress = 0f; onToggle(false); return@LaunchedEffect }
            delay(40)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (enabled) "Hold to turn off" else "Tap to protect",
            style = MaterialTheme.typography.bodySmall,
            color = WardenPalette.Muted,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(74.dp).height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(listOf(WardenPalette.HeroStart, WardenPalette.HeroMid))
                    } else {
                        Brush.horizontalGradient(listOf(WardenPalette.Blue, WardenPalette.BlueDeep))
                    },
                )
                .pointerInput(enabled, hasPin) {
                    if (!enabled) {
                        // Turning ON is always safe: a simple tap (down→up without cancel).
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            waitForUpOrCancellation()?.consume() ?: return@awaitEachGesture
                            onToggle(true)
                        }
                    } else {
                        // Turning OFF: a press that survives ≥ GUARD_HOLD_MS. Explicit
                        // awaitEachGesture loop — the primitive-free, error-proof form.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            if (hasPin) {
                                onPinRequired()
                                return@awaitEachGesture
                            }
                            val start = System.currentTimeMillis()
                            var released: PointerInputChange? = waitForUpOrCancellation()
                            val elapsed = System.currentTimeMillis() - start
                            if (released != null && elapsed >= GUARD_HOLD_MS) {
                                onToggle(false)
                            }
                        }
                    }
                },
        ) {
            Icon(
                if (enabled) Icons.Filled.PowerSettingsNew else Icons.Filled.PlayArrow,
                contentDescription = if (enabled) "Hold to turn off" else "Turn protection on",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun BlocklistScreen(
    vm: WardenViewModel,
    onOpenAppPicker: () -> Unit,
    onBack: () -> Unit = {},
) {
    val items by vm.items.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<BlockedItem?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader("Blocklist", onBack, "Sites and apps that are off-limits.")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Website, e.g. instagram.com") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.addWebsite(input); input = "" }, modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenAppPicker, modifier = Modifier.fillMaxWidth()) { Text("+ Add apps to block") }
        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            Text("Nothing blocked yet — add a website above or tap “Add apps”.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { editing = item }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.type == BlockType.APP) {
                            AppIcon(item.value, modifier = Modifier.size(36.dp).padding(end = 12.dp))
                        } else {
                            Text("🌐", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.titleMedium)
                            Text(itemSummary(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { vm.toggleItem(item) })
                        IconButton(onClick = { vm.removeItem(item) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }

    editing?.let { current ->
        ItemEditorSheet(
            item = current,
            onSave = { vm.saveItem(it) },
            onDismiss = { editing = null },
        )
    }
}

private fun itemSummary(item: BlockedItem): String {
    if (item.type == BlockType.WEBSITE) return "Website · hard block"
    return when (item.interceptMode) {
        InterceptMode.BLOCK -> "App · hard block"
        InterceptMode.PAUSE -> buildString {
            append("App · pause ${item.pauseSeconds}s")
            if (item.openLimitPerDay > 0) append(" · ${item.openLimitPerDay}/day")
            if (item.dailyLimitMinutes > 0) append(" · ${item.dailyLimitMinutes}m limit")
        }
    }
}

@Composable
fun SchedulesScreen(vm: WardenViewModel, onBack: () -> Unit = {}) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader("Schedules", onBack)
        Spacer(Modifier.height(8.dp))
        Text("With 'always-on' off, the blocklist is only enforced during these windows.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ New schedule") }
        Spacer(Modifier.height(16.dp))
        if (schedules.isEmpty()) {
            Text("No schedules yet. Add one, then turn off “Always on” in Settings to use them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth().clickable { editing = s }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text("${fmt(s.startMinute)}–${fmt(s.endMinute)}  •  ${daysLabel(s.daysMask)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = s.enabled, onCheckedChange = { vm.addSchedule(s.copy(enabled = it)) })
                        IconButton(onClick = { vm.removeSchedule(s) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                }
            }
        }
    }

    if (creating) {
        ScheduleEditorDialog(initial = null, onSave = { vm.addSchedule(it) }, onDismiss = { creating = false })
    }
    editing?.let { current ->
        ScheduleEditorDialog(initial = current, onSave = { vm.addSchedule(it) }, onDismiss = { editing = null })
    }
}

private data class UsageRow(val pkg: String, val label: String, val millis: Long)

@Composable
fun StatsScreen(vm: WardenViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val hasPermission = remember { UsageStatsHelper.hasPermission(context) }
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()
    val sessions by vm.focusSessionsDone.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader("Your wins", onBack)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Streak", "$streak d", Modifier.weight(1f))
            StatCard("Best", "$longest d", Modifier.weight(1f))
            StatCard("Sessions", "$sessions", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text("Screen time today", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (!hasPermission) {
            Text("Grant usage access to see screen-time.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                Text("Grant usage access")
            }
        } else {
            val rows by produceState<List<UsageRow>?>(initialValue = null) {
                value = withContext(Dispatchers.Default) {
                    UsageStatsHelper.todayUsage(context).take(25)
                        .map { UsageRow(it.packageName, AppsHelper.labelFor(context, it.packageName), it.totalMillis) }
                }
            }
            val list = rows
            if (list != null && list.isEmpty()) {
                Text("No screen time recorded yet today.", style = MaterialTheme.typography.bodyMedium)
            } else if (list != null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(list, key = { it.pkg }) { u ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(u.pkg, modifier = Modifier.size(32.dp).padding(end = 12.dp))
                            Text(u.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(fmtDuration(u.millis), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                    item { AdSlotBanner(slot = AdSlot.STATS_NATIVE, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    }
}

@Composable
fun ManagementPinGate(
    vm: WardenViewModel,
    screenName: String,
    content: @Composable () -> Unit,
) {
    val settingsReady by vm.settingsReady.collectAsStateWithLifecycle()
    val pinConfigured by vm.pinConfigured.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    if (!settingsReady || pinConfigured == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (pinConfigured == true && !unlocked) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$screenName is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showUnlock = true }) { Text("Unlock with PIN") }
        }
        if (showUnlock) {
            VerifyPinDialog(
                title = "Enter PIN",
                verify = vm::verifyPin,
                onSuccess = { unlocked = true; showUnlock = false },
                onDismiss = { showUnlock = false },
            )
        }
        return
    }
    content()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit = {},
                   onOpenProtection: () -> Unit = {}, onOpenBrowser: () -> Unit = {}, onOpenPrivacy: () -> Unit = {},
                   router: Boolean = true,
                   content: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val pinConfigured by vm.pinConfigured.collectAsStateWithLifecycle()
    val settingsReady by vm.settingsReady.collectAsStateWithLifecycle()

    var unlocked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    // Until DataStore delivers its first snapshot we do not know whether a PIN/strict lock
    // exists, so render no controls at all rather than an unlocked-looking screen.
    if (!settingsReady || pinConfigured == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val hasPin = pinConfigured == true

    // PIN gate: settings are locked until the PIN is entered.
    if (hasPin && !unlocked) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Settings are locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showUnlock = true }) { Text("Unlock with PIN") }
        }
        if (showUnlock) {
            VerifyPinDialog("Enter PIN", vm::verifyPin, onSuccess = { unlocked = true; showUnlock = false }, onDismiss = { showUnlock = false })
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader("Settings", onBack, "الأقسام خلف الحماية.")

        if (router) {
            // ── قائمة الأقسام ──────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Filled.Shield,
                title = "الحماية",
                subtitle = "فلتر الحجب، فرض SafeSearch، القوائم المخصصة",
                onClick = onOpenProtection,
            )
            SettingsSectionCard(
                icon = Icons.Filled.Search,
                title = "المتصفح",
                subtitle = "إذن إمكانية الوصول، المتصفحات المدعومة، تلميحات التلافي",
                onClick = onOpenBrowser,
            )
            SettingsSectionCard(
                icon = Icons.Filled.Info,
                title = "الخصوصية والدعم",
                subtitle = "لا نجمع بيانات، طلب مساعدة، الحركة، اللمسات الأخيرة",
                onClick = onOpenPrivacy,
            )
            HorizontalDivider()
            OutlinedButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                Text("الإعداد والأذونات")
            }
        } else {
            content()
        }
    }

    // (إدارة PIN انتقلت إلى قسم «الخصوصية والدعم» — SettingsControls.PinCard.)
}

/** بطاقة قسم في قائمة الإعدادات الرئيسية. */
@Composable
private fun SettingsSectionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = WardenPalette.Card),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(WardenPalette.GreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = WardenPalette.GreenDark, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = WardenPalette.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WardenPalette.TextMuted)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = WardenPalette.TextMuted)
        }
    }
}

/** Focus sessions, now a bright mint card with real timer styling. */
@Composable
private fun FocusCard(focusEndsAt: Long, strict: Boolean, onStart: (Int, Boolean) -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focusEndsAt) {
        while (focusEndsAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val active = focusEndsAt > now

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WardenPalette.GreenSoft),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = WardenPalette.Green, modifier = Modifier.size(22.dp))
                Text(
                    if (active) "Focus session" else "Start a focus session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WardenPalette.Ink,
                )
                if (active && strict) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "STRICT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = WardenPalette.Green,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (active) {
                Text(
                    formatCountdown(focusEndsAt - now),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = WardenPalette.Green,
                )
                Text(
                    if (strict) "Strict — this can't be ended early. Deep work, locked in." else "Blocking stays on until the timer ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WardenPalette.Muted,
                )
                if (!strict) OutlinedButton(onClick = onCancel) { Text("End session") }
            } else {
                Text(
                    "Force blocking on for a set time — great for deep work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WardenPalette.Muted,
                )
                var strictChoice by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = strictChoice, onCheckedChange = { strictChoice = it })
                    Text("Strict (no early exit)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50).forEach { m ->
                        Button(onClick = { onStart(m, strictChoice) }) { Text("$m min") }
                    }
                }
            }
        }
    }
}

private fun formatCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = WardenPalette.BlueSoft),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = WardenPalette.Blue,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = WardenPalette.Muted)
        }
    }
}

// --- small formatting helpers ---
private fun fmt(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun daysLabel(mask: Int): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return names.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(" ")
}

private fun fmtDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
