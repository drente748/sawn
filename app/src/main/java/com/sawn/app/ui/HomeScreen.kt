package com.sawn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.app.vpn.SawnVpnService
import com.sawn.domain.feature.JournalSnapshot
import com.sawn.domain.feature.ShieldPreset
import com.sawn.domain.feature.ShieldPresetState
import com.sawn.domain.feature.ProtectionCheckup
import com.sawn.domain.feature.CheckupState
import com.sawn.app.util.PermissionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «لوحة الفجر» — الرئيسية بصَوْن: كل الميزات مباشرة في صفحة واحدة، بلا تبويبات.
 * البنية: تحية ← درع/الحالة ← تنبيهات ← الحماية المباشرة ← سلاسل/تقدم ← أدواتي ← إدارة.
 */
@Composable
fun HomeScreen(
    vm: SawnViewModel,
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
    val vpnRunning by SawnVpnService.runningFlow.collectAsStateWithLifecycle()
    val journal by vm.blockJournal.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val profileName by vm.profileName.collectAsStateWithLifecycle()

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
    val heroState = when {
        !accessibility -> HeroState.NEEDS_SETUP
        lockdownActive -> HeroState.LOCKDOWN
        filterUnavailable -> HeroState.DOWN
        actuallyProtected -> HeroState.PROTECTED
        guardRequested -> HeroState.PARTIAL
        else -> HeroState.DOWN
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
            .dawnBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DawnTopBar(
            name = profileName,
            streak = streak,
            onOpenSettings = onOpenSettings,
        )

        // ===== الدرع =====
        DawnCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HeroShield(
                    state = heroState,
                    progress = checkup.score / 100f,
                    modifier = Modifier.size(96.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            when (heroState) {
                                HeroState.NEEDS_SETUP -> R.string.setup_required
                                HeroState.PARTIAL -> R.string.protection_waiting
                                HeroState.LOCKDOWN -> R.string.time_lock_active
                                HeroState.DOWN -> R.string.protection_off
                                HeroState.PROTECTED -> R.string.home_hero_protected
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = SawnPalette.Ink,
                    )
                    Text(
                        stringResource(
                            when (heroState) {
                                HeroState.NEEDS_SETUP -> R.string.grant_access_to_start
                                HeroState.PARTIAL -> R.string.protection_scheduled
                                HeroState.LOCKDOWN -> R.string.home_lockdown_note
                                HeroState.DOWN -> R.string.turn_on_protection
                                HeroState.PROTECTED -> R.string.home_layers_complete
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = SawnPalette.InkSoft,
                    )
                    if (lockdownActive) {
                        Text(
                            stringResource(R.string.lockdown_until, SettingsControls.formatRemaining(lockdownRemaining)),
                            style = MaterialTheme.typography.labelMedium,
                            color = SawnPalette.InkSoft,
                        )
                    }
                }
            }
            // حالة التشغيل + زر الإجراء
            Box(Modifier.fillMaxWidth().height(8.dp))
            if (heroState == HeroState.PROTECTED) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(SawnPalette.Success),
                        )
                        Text(
                            stringResource(R.string.home_hero_running),
                            style = MaterialTheme.typography.labelLarge,
                            color = SawnPalette.Success,
                        )
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        desiredGuard = false
                        guardBusy = true
                        if (hasPin) showVerify = true else onToggleBlocking(false) { guardBusy = false }
                    }) {
                        Text(stringResource(R.string.home_pause_protection), color = SawnPalette.InkSoft)
                    }
                }
            } else {
                PillCTA(
                    text = stringResource(R.string.home_enable_protection),
                    onClick = {
                        desiredGuard = true
                        guardBusy = true
                        onToggleBlocking(true) { guardBusy = false }
                    },
                    enabled = !guardBusy,
                )
            }
        }

        // ===== تنبيهات سياقية =====
        when {
            !accessibility -> AlertRow(stringResource(R.string.complete_setup), urgent = true, onClick = onOpenSetup)
            filterUnavailable -> AlertRow(stringResource(R.string.filter_stopped), urgent = true, onClick = onOpenSetup)
        }

        // ===== الحماية المباشرة =====
        SectionHeader(
            stringResource(R.string.home_section_layers),
            stringResource(R.string.home_section_layers_hint),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProtectionToggleCard(
                    icon = Icons.Filled.VisibilityOff,
                    title = stringResource(R.string.quick_porn),
                    subtitle = stringResource(R.string.quick_porn_sub),
                    checked = pornGuard,
                    enabled = !lockdownActive,
                    onCheckedChange = vm::setPornGuard,
                    modifier = Modifier.weight(1f),
                )
                ProtectionToggleCard(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.quick_safe_search),
                    subtitle = stringResource(R.string.quick_safe_search_sub),
                    checked = safeSearch,
                    enabled = !lockdownActive,
                    onCheckedChange = vm::setSafeSearchForce,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProtectionToggleCard(
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.quick_ads),
                    subtitle = stringResource(R.string.quick_ads_sub),
                    checked = blockAds,
                    enabled = !lockdownActive,
                    onCheckedChange = vm::setBlockAds,
                    modifier = Modifier.weight(1f),
                )
                ProtectionToggleCard(
                    icon = Icons.Filled.Dns,
                    title = stringResource(R.string.quick_doh),
                    subtitle = stringResource(R.string.quick_doh_sub),
                    checked = blockDoh,
                    enabled = !lockdownActive,
                    onCheckedChange = vm::setBlockDoh,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProtectionToggleCard(
                    icon = Icons.Filled.NotificationsOff,
                    title = stringResource(R.string.quick_notifications),
                    subtitle = stringResource(R.string.quick_notifications_sub),
                    checked = blockNotifications,
                    enabled = !lockdownActive,
                    onCheckedChange = vm::setBlockNotifications,
                    modifier = Modifier.weight(1f),
                )
                ProtectionToggleCard(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.quick_strict),
                    subtitle = stringResource(R.string.quick_strict_sub),
                    checked = strictMode,
                    enabled = !lockdownActive || strictMode,
                    onCheckedChange = { on ->
                        strictFailed = false
                        when {
                            on && !hasPin -> showPinForStrict = true
                            else -> scope.launch { strictFailed = !vm.setStrictMode(on) }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (strictFailed) {
                Text(
                    stringResource(R.string.strict_enable_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Always-on VPN
            DawnCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = SawnPalette.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_always_on_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = SawnPalette.Ink,
                        )
                        Text(
                            stringResource(
                                if (alwaysOn) R.string.home_always_on_on else R.string.home_always_on_off,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = SawnPalette.InkSoft,
                        )
                    }
                    Switch(
                        checked = alwaysOn,
                        onCheckedChange = vm::setAlwaysOn,
                        enabled = !lockdownActive,
                    )
                }
            }
            PresetChips(
                selected = ShieldPreset.entries.indexOfFirst { it.matches(presetState) }.coerceAtLeast(0),
                options = listOf(
                    stringResource(R.string.preset_light),
                    stringResource(R.string.preset_balanced),
                    stringResource(R.string.preset_maximum),
                ),
                onSelect = { selected ->
                    ShieldPreset.entries.getOrNull(selected)?.let { vm.applyPreset(it); appliedPreset = selected }
                },
            )
            appliedPreset?.let {
                Text(
                    stringResource(
                        R.string.preset_applied,
                        stringResource(
                            listOf(R.string.preset_light, R.string.preset_balanced, R.string.preset_maximum)[it],
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SawnPalette.Success,
                )
            }
        }

        // ===== التقدم =====
        SectionHeader(
            stringResource(R.string.home_section_progress),
            stringResource(R.string.home_section_progress_hint),
        )
        ProgressStrip(
            streak = streak,
            longest = longest,
            sessions = sessions,
            onClick = onOpenStats,
        )
        if (streakAtRisk) {
            Text(
                stringResource(R.string.home_streak_at_risk),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.GoldDeep,
            )
        }

        JournalCard(journal)

        FocusInline(
            focusEndsAt = focusEndsAt,
            strict = focusStrict,
            onStart = onStartFocus,
            onCancel = onCancelFocus,
        )

        // ===== أدواتي =====
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
                detail = stringResource(R.string.focus_sessions) + " · " + SawnNumbers.format(sessions.toLong()),
                onClick = onOpenStats,
            )
            ManageTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.home_manage_checkup),
                detail = stringResource(R.string.home_manage_checkup_detail, checkup.score),
                onClick = onOpenSetup,
            )
        }

        Text(
            stringResource(R.string.home_privacy_footer),
            style = MaterialTheme.typography.bodySmall,
            color = SawnPalette.Muted,
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

@Composable
private fun DawnTopBar(name: String?, streak: Int, onOpenSettings: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SawnPalette.PrimarySoft)
                .border(1.dp, SawnPalette.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = SawnPalette.Primary,
                modifier = Modifier.size(24.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = SawnPalette.Ink,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (name.isNullOrBlank()) {
                    stringResource(R.string.onboarding_greet, stringResource(R.string.app_name))
                } else {
                    stringResource(R.string.onboarding_greet, name)
                },
                style = MaterialTheme.typography.labelMedium,
                color = SawnPalette.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (streak > 0) {
            Surface(
                shape = CircleShape,
                color = SawnPalette.GoldContainer,
                modifier = Modifier.border(1.dp, SawnPalette.Gold, CircleShape),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = SawnPalette.GoldDeep,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        SawnNumbers.format(streak.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SawnPalette.OnGoldContainer,
                    )
                }
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, stringResource(R.string.settings_title), tint = SawnPalette.Ink)
        }
    }
}

@Composable
private fun AlertRow(text: String, urgent: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (urgent) SawnPalette.ErrorSoft else SawnPalette.PrimarySoft,
        modifier = Modifier.fillMaxWidth().border(1.dp, if (urgent) SawnPalette.Error else SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = SawnPalette.Ink, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SawnPalette.InkSoft,
            )
        }
    }
}

@Composable
private fun ManageTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = SawnPalette.Card,
        modifier = modifier.border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(SawnPalette.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = SawnPalette.Primary, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = SawnPalette.Ink)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = SawnPalette.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun JournalCard(snapshot: JournalSnapshot) {
    DawnCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Insights,
                contentDescription = null,
                tint = SawnPalette.Primary,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.home_journal_title),
                style = MaterialTheme.typography.titleMedium,
                color = SawnPalette.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.home_journal_week),
                style = MaterialTheme.typography.labelSmall,
                color = SawnPalette.InkSoft,
            )
        }
        Text(
            if (snapshot.today > 0) {
                stringResource(R.string.home_journal_today, snapshot.today)
            } else {
                stringResource(R.string.home_journal_quiet)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = SawnPalette.Ink,
        )
        ProtectionWeekChart(snapshot)
        Text(
            stringResource(R.string.home_journal_total, snapshot.total),
            style = MaterialTheme.typography.bodySmall,
            color = SawnPalette.InkSoft,
        )
    }
}

@Composable
private fun SosCard(onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = SawnPalette.PrimarySoft,
        modifier = Modifier.fillMaxWidth().border(1.dp, SawnPalette.Line, MaterialTheme.shapes.large),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(SawnPalette.Card),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SelfImprovement,
                    contentDescription = null,
                    tint = SawnPalette.Primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.sos_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = SawnPalette.Ink,
                )
                Text(
                    stringResource(R.string.sos_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = SawnPalette.InkSoft,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.sos_open),
                tint = SawnPalette.PrimaryDeep,
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

    DawnCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = SawnPalette.Primary)
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Text(
                if (active) stringResource(R.string.focus_active) else stringResource(R.string.home_focus_quick),
                style = MaterialTheme.typography.titleMedium,
                color = SawnPalette.Ink,
                modifier = Modifier.weight(1f),
            )
            if (active && strict) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = SawnPalette.ErrorSoft,
                ) {
                    Text(
                        stringResource(R.string.strict),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = SawnPalette.OnErrorSoft,
                    )
                }
            }
        }

        if (active) {
            Text(
                formatCountdown(focusEndsAt - now),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = SawnPalette.Primary,
            )
            Text(
                if (strict) stringResource(R.string.strict_focus_description) else stringResource(R.string.focus_description),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
            if (!strict) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.end_session)) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 90).forEach { minutes ->
                    OutlinedButton(onClick = { onStart(minutes, strictChoice) }) {
                        Text(stringResource(R.string.home_focus_minutes, minutes))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = strictChoice, onCheckedChange = { strictChoice = it })
                Text(stringResource(R.string.strict_mode), color = SawnPalette.Ink)
            }
            if (strictChoice) {
                Text(
                    stringResource(R.string.home_focus_strict_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SawnPalette.Error,
                )
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
