package com.warden.blocker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.R
import com.warden.blocker.feature.CheckupState
import com.warden.blocker.feature.JournalSnapshot
import com.warden.blocker.feature.ProtectionCheckup
import com.warden.blocker.feature.ShieldPreset
import com.warden.blocker.feature.ShieldPresetState
import com.warden.blocker.util.PermissionsHelper
import com.warden.blocker.vpn.WardenVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * الشاشة الرئيسية v4 — لوحة قيادة واحدة:
 *
 * الدرع ثم مفتاح الحماية، وتحتَه **كل** مفاتيح الحماية ظاهرة مباشرة (بلا تبويبات)،
 * ثم أنماط الحماية، ثم حصيلة اليوم، ثم جلسة التركيز، ثم أدوات (درع الرغبة والفحص
 * والإدارة). المستخدم يرى حال حمايته ويلفّ على كل ميزاتها من شاشة واحدة.
 */
@Composable
fun HomeScreen(
    vm: WardenViewModel,
    onToggleBlocking: (Boolean, (Boolean) -> Unit) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onCancelFocus: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenBlocklist: () -> Unit = {},
    onOpenSchedules: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resumeTick = rememberResumeTick()

    val accessibility = remember(resumeTick) { PermissionsHelper.isAccessibilityEnabled(context) }
    val usageAccess = remember(resumeTick) { PermissionsHelper.hasUsageAccess(context) }
    val notificationsAllowed = remember(resumeTick) { PermissionsHelper.hasNotifications(context) }

    val enabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()
    val streakAtRisk by vm.streakAtRisk.collectAsStateWithLifecycle()
    val sessions by vm.focusSessionsDone.collectAsStateWithLifecycle()
    val focusEndsAt by vm.focusEndsAt.collectAsStateWithLifecycle()
    val focusStrict by vm.focusStrict.collectAsStateWithLifecycle()
    val strictLocked by vm.strictLocked.collectAsStateWithLifecycle()
    val strictMode by vm.strictMode.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
    val lockdownRemaining by vm.lockdownRemaining.collectAsStateWithLifecycle()
    val pornGuard by vm.pornGuard.collectAsStateWithLifecycle()
    val safeSearch by vm.safeSearchForce.collectAsStateWithLifecycle()
    val blockAds by vm.blockAds.collectAsStateWithLifecycle()
    val blockDoh by vm.blockDoh.collectAsStateWithLifecycle()
    val blockNotifications by vm.blockNotifications.collectAsStateWithLifecycle()
    val alwaysOn by vm.alwaysOn.collectAsStateWithLifecycle()
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
    val enforcementActive by vm.enforcementActive.collectAsStateWithLifecycle()
    val vpnRunning by WardenVpnService.runningFlow.collectAsStateWithLifecycle()
    val journal by vm.blockJournal.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()

    var showVerify by remember { mutableStateOf(false) }
    var showPinForStrict by remember { mutableStateOf(false) }
    var strictFailed by remember { mutableStateOf(false) }
    var appliedPreset by remember { mutableStateOf<Int?>(null) }
    var showSos by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var guardBusy by remember { mutableStateOf(false) }
    var desiredGuard by remember { mutableStateOf(enabled) }

    LaunchedEffect(focusEndsAt) {
        while (focusEndsAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(guardBusy, enabled, vpnRunning) {
        if (!guardBusy) return@LaunchedEffect
        if ((desiredGuard && enabled && vpnRunning) || (!desiredGuard && !enabled)) guardBusy = false
    }
    LaunchedEffect(guardBusy) {
        if (!guardBusy) return@LaunchedEffect
        delay(7_000)
        guardBusy = false
    }

    val focusActive = focusEndsAt > now
    val guardRequested = enabled || focusActive || lockdownActive
    val actuallyProtected = enforcementActive && vpnRunning
    val filterUnavailable = enforcementActive && !vpnRunning
    val lockedNow = strictLocked || (focusActive && focusStrict)
    val lockLabel = when {
        lockdownActive -> stringResource(R.string.time_lock_active)
        focusActive && focusStrict -> stringResource(R.string.strict_focus_session)
        focusActive -> stringResource(R.string.locked_during_session)
        else -> stringResource(R.string.strict_mode_on)
    }

    val checkup = ProtectionCheckup.evaluate(
        CheckupState(
            accessibility = accessibility,
            filter = vpnRunning,
            pornGuard = pornGuard,
            safeSearch = safeSearch,
            encryptedDns = blockDoh,
            ads = blockAds,
            notifications = notificationsAllowed,
            usage = usageAccess,
        ),
    )
    val presetState = ShieldPresetState(
        pornGuard = pornGuard,
        safeSearch = safeSearch,
        blockAds = blockAds,
        blockDoh = blockDoh,
        blockNotifications = blockNotifications,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HomeTopBar(streak = streak, onOpenSettings = onOpenSettings)

        GuardianHero(
            actuallyProtected = actuallyProtected,
            guardRequested = guardRequested,
            filterUnavailable = filterUnavailable,
            needsSetup = !accessibility,
            lockdownActive = lockdownActive,
            lockdownRemaining = lockdownRemaining,
            streak = streak,
            enabled = enabled,
            hasPin = hasPin,
            focusActive = focusActive,
            locked = lockedNow,
            lockLabel = lockLabel,
            busy = guardBusy,
            reduceMotion = reduceMotion,
            onToggle = { target ->
                desiredGuard = target
                guardBusy = true
                onToggleBlocking(target) { guardBusy = false }
            },
            onPinRequired = { showVerify = true },
        )

        when {
            !accessibility -> AlertRow(
                text = stringResource(R.string.complete_setup),
                urgent = true,
                onClick = onOpenSetup,
            )
            filterUnavailable -> AlertRow(
                text = stringResource(R.string.filter_stopped),
                urgent = true,
                onClick = onOpenSetup,
            )
        }

        SectionHeader(
            stringResource(R.string.home_section_layers),
            stringResource(R.string.home_section_layers_hint),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.VisibilityOff,
                    title = stringResource(R.string.quick_porn),
                    subtitle = stringResource(R.string.quick_porn_sub),
                    checked = pornGuard,
                    enabled = !lockdownActive,
                    onChange = vm::setPornGuard,
                )
                ToggleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.quick_safe_search),
                    subtitle = stringResource(R.string.quick_safe_search_sub),
                    checked = safeSearch,
                    enabled = !lockdownActive,
                    onChange = vm::setSafeSearchForce,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.quick_ads),
                    subtitle = stringResource(R.string.quick_ads_sub),
                    checked = blockAds,
                    enabled = !lockdownActive,
                    onChange = vm::setBlockAds,
                )
                ToggleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Dns,
                    title = stringResource(R.string.quick_doh),
                    subtitle = stringResource(R.string.quick_doh_sub),
                    checked = blockDoh,
                    enabled = !lockdownActive,
                    onChange = vm::setBlockDoh,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.NotificationsOff,
                    title = stringResource(R.string.quick_notifications),
                    subtitle = stringResource(R.string.quick_notifications_sub),
                    checked = blockNotifications,
                    enabled = !lockdownActive,
                    onChange = vm::setBlockNotifications,
                )
                ToggleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.quick_strict),
                    subtitle = stringResource(R.string.quick_strict_sub),
                    checked = strictMode,
                    enabled = !lockdownActive || strictMode,
                    onChange = { on ->
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

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_always_on_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(
                                if (alwaysOn) R.string.home_always_on_on else R.string.home_always_on_off,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = alwaysOn,
                        onCheckedChange = vm::setAlwaysOn,
                        enabled = !lockdownActive,
                        modifier = Modifier.scale(0.85f),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShieldPreset.entries.forEach { preset ->
                FilterChip(
                    selected = preset.matches(presetState),
                    enabled = !lockdownActive,
                    onClick = {
                        vm.applyPreset(preset)
                        appliedPreset = presetLabel(preset)
                    },
                    label = { Text(stringResource(presetLabel(preset))) },
                )
            }
        }
        appliedPreset?.let { labelRes ->
            Text(
                stringResource(R.string.preset_applied, stringResource(labelRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        SectionHeader(
            stringResource(R.string.home_section_progress),
            stringResource(R.string.home_section_progress_hint),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                value = streak.toString(),
                label = stringResource(R.string.stat_streak),
                modifier = Modifier.weight(1f),
                highlight = streak > 0,
            )
            MetricCard(
                value = longest.toString(),
                label = stringResource(R.string.stat_best),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = sessions.toString(),
                label = stringResource(R.string.stat_sessions),
                modifier = Modifier.weight(1f),
            )
        }
        if (streakAtRisk) {
            Text(
                stringResource(R.string.home_streak_at_risk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        JournalCard(journal)

        FocusInline(
            focusEndsAt = focusEndsAt,
            strict = focusStrict,
            onStart = onStartFocus,
            onCancel = onCancelFocus,
        )

        SectionHeader(stringResource(R.string.home_section_tools))
        SosCard(onOpen = { showSos = true })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManageTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Block,
                title = stringResource(R.string.home_manage_apps),
                detail = stringResource(R.string.home_manage_apps_detail, items.count { it.enabled }),
                onClick = onOpenBlocklist,
            )
            ManageTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.home_manage_schedules),
                detail = stringResource(R.string.home_manage_schedules_detail, schedules.count { it.enabled }),
                onClick = onOpenSchedules,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManageTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Insights,
                title = stringResource(R.string.home_manage_stats),
                detail = stringResource(R.string.focus_sessions) + " · " + sessions,
                onClick = onOpenStats,
            )
            ManageTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.home_manage_checkup),
                detail = stringResource(R.string.home_manage_checkup_detail, checkup.score),
                onClick = onOpenSetup,
            )
        }

        Text(
            stringResource(R.string.home_privacy_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }

    if (showVerify) {
        VerifyPinDialog(
            title = stringResource(R.string.pin_disable_protection),
            verify = vm::verifyPin,
            onSuccess = {
                showVerify = false
                desiredGuard = false
                guardBusy = true
                onToggleBlocking(false) { guardBusy = false }
            },
            onDismiss = { showVerify = false },
        )
    }
    if (showPinForStrict) {
        SettingsControls.PinSetup(vm = vm, enableStrictAfter = true, onDismiss = { showPinForStrict = false })
    }
    if (showSos) {
        UrgeSosDialog(onDismiss = { showSos = false })
    }
}

private fun presetLabel(preset: ShieldPreset): Int = when (preset) {
    ShieldPreset.LIGHT -> R.string.preset_light
    ShieldPreset.BALANCED -> R.string.preset_balanced
    ShieldPreset.MAXIMUM -> R.string.preset_maximum
}

@Composable
private fun HomeTopBar(streak: Int, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (streak > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        streak.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Settings, stringResource(R.string.settings_title))
        }
    }
}

@Composable
private fun GuardianHero(
    actuallyProtected: Boolean,
    guardRequested: Boolean,
    filterUnavailable: Boolean,
    needsSetup: Boolean,
    lockdownActive: Boolean,
    lockdownRemaining: Long,
    streak: Int,
    enabled: Boolean,
    hasPin: Boolean,
    focusActive: Boolean,
    locked: Boolean,
    lockLabel: String,
    busy: Boolean,
    reduceMotion: Boolean,
    onToggle: (Boolean) -> Unit,
    onPinRequired: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "guardian")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val breath = if (reduceMotion || !actuallyProtected) 1f else pulse

    val accent = when {
        needsSetup -> MaterialTheme.colorScheme.error
        filterUnavailable -> MaterialTheme.colorScheme.tertiary
        actuallyProtected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val centerMark = when {
        needsSetup -> Icons.Filled.ErrorOutline
        filterUnavailable -> Icons.Filled.Warning
        actuallyProtected -> Icons.Filled.Check
        guardRequested -> Icons.Filled.Schedule
        else -> Icons.Filled.Shield
    }
    val headline = when {
        needsSetup -> stringResource(R.string.setup_required)
        lockdownActive -> stringResource(R.string.time_lock_active)
        actuallyProtected -> stringResource(R.string.home_hero_protected)
        filterUnavailable -> stringResource(R.string.filter_stopped)
        guardRequested -> stringResource(R.string.protection_waiting)
        else -> stringResource(R.string.protection_off)
    }
    val subline = when {
        needsSetup -> stringResource(R.string.grant_access_to_start)
        lockdownActive -> stringResource(R.string.lockdown_until, SettingsControls.formatRemaining(lockdownRemaining))
        actuallyProtected -> stringResource(R.string.home_layers_complete)
        filterUnavailable -> stringResource(R.string.restart_filter)
        guardRequested -> stringResource(R.string.protection_scheduled)
        else -> stringResource(R.string.turn_on_protection)
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (actuallyProtected) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // شريط لونٍ رفيع أعلى البطاقة: هوية الحالة بلا خلفيةٍ تُرهق العين.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.18f)),
                        ),
                    ),
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                        Canvas(Modifier.fillMaxSize().scale(breath)) {
                            drawCircle(
                                color = accent.copy(alpha = 0.12f),
                                radius = size.minDimension / 2f,
                            )
                            drawCircle(
                                color = accent.copy(alpha = 0.35f),
                                radius = size.minDimension / 2f - 5.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                            drawCircle(
                                color = accent,
                                radius = size.minDimension / 2f - 14.dp.toPx(),
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        Icon(
                            centerMark,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(headline, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            subline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = if (streak > 0) {
                                    stringResource(R.string.home_streak_chip, streak)
                                } else {
                                    stringResource(R.string.home_streak_start)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                val canTurnOff = !locked && !lockdownActive && !focusActive
                val target = !enabled
                Button(
                    onClick = { if (enabled && hasPin) onPinRequired() else onToggle(target) },
                    enabled = !busy && (target || canTurnOff),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            if (enabled) Icons.Filled.PowerSettingsNew else Icons.Filled.Shield,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        when {
                            lockdownActive -> stringResource(R.string.locked_by_time_lock)
                            focusActive && enabled -> stringResource(R.string.locked_focus)
                            locked -> stringResource(R.string.locked_strict)
                            enabled -> stringResource(R.string.disable_protection)
                            else -> stringResource(R.string.enable_protection)
                        },
                    )
                }
                if (locked || lockdownActive || focusActive) {
                    Text(
                        lockLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertRow(text: String, urgent: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (urgent) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun ToggleCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (checked && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.height(140.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (checked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    enabled = enabled,
                    modifier = Modifier.scale(0.8f),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JournalCard(snapshot: JournalSnapshot) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.home_journal_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.home_journal_week),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (snapshot.today > 0) {
                    stringResource(R.string.home_journal_today, snapshot.today)
                } else {
                    stringResource(R.string.home_journal_quiet)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            ProtectionWeekChart(snapshot)
            Text(
                stringResource(R.string.home_journal_total, snapshot.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SosCard(onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SelfImprovement,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.sos_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.sos_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.sos_open),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FocusInline(
    focusEndsAt: Long,
    strict: Boolean,
    onStart: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var strictChoice by remember { mutableStateOf(false) }
    LaunchedEffect(focusEndsAt) {
        while (focusEndsAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val active = focusEndsAt > now

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (active) stringResource(R.string.focus_active) else stringResource(R.string.home_focus_quick),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (active && strict) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            stringResource(R.string.strict),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            if (active) {
                Text(
                    formatCountdown(focusEndsAt - now),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (strict) stringResource(R.string.strict_focus_description)
                    else stringResource(R.string.focus_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!strict) OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.end_session))
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(25, 50, 90).forEach { minutes ->
                        OutlinedButton(onClick = { onStart(minutes, strictChoice) }) {
                            Text(stringResource(R.string.home_focus_minutes, minutes))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = strictChoice, onCheckedChange = { strictChoice = it })
                    Text(stringResource(R.string.strict_mode))
                }
                if (strictChoice) {
                    Text(
                        stringResource(R.string.home_focus_strict_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
private fun ManageTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
