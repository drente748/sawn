package com.sawn.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sawn.app.ui.AppPickerScreen
import com.sawn.app.ui.BlocklistScreen
import com.sawn.app.ui.HomeScreen
import com.sawn.app.ui.ManagementPinGate
import com.sawn.app.ui.onboarding.OnboardingHost
import com.sawn.app.ui.SchedulesScreen
import com.sawn.app.ui.SetupScreen
import com.sawn.app.ui.StatsScreen
import com.sawn.app.ui.SawnViewModel
import com.sawn.app.ui.settings.BrowserSettingsScreen
import com.sawn.app.ui.settings.PrivacySupportSettingsScreen
import com.sawn.app.ui.settings.ProtectionSettingsScreen
import com.sawn.app.ui.theme.SawnTheme
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.vpn.VpnController
import com.sawn.app.vpn.SawnVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SawnTheme {
                val vm: SawnViewModel = viewModel()

                var pendingAfterNotification by remember { mutableStateOf<(() -> Unit)?>(null) }
                val notifPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    val action = pendingAfterNotification
                    pendingAfterNotification = null
                    action?.invoke()
                }

                var pendingAfterConsent by remember { mutableStateOf<(() -> Unit)?>(null) }
                val consentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) pendingAfterConsent?.invoke()
                    else {
                        Toast.makeText(this, R.string.vpn_permission_needed, Toast.LENGTH_SHORT).show()
                    }
                    pendingAfterConsent = null
                }

                fun withVpn(action: () -> Unit) {
                    val consent = VpnController.consentIntent(this@MainActivity)
                    if (consent == null) action()
                    else {
                        pendingAfterConsent = action
                        consentLauncher.launch(consent)
                    }
                }

                fun requestNotificationThen(action: () -> Unit) {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        pendingAfterNotification = action
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        action()
                    }
                }

                fun toggleBlocking(enabled: Boolean, onSettled: (Boolean) -> Unit = {}) {
                    if (enabled) {
                        requestNotificationThen {
                            withVpn {
                                this@MainActivity.lifecycleScope.launch {
                                    val started = VpnController.startAndAwait(this@MainActivity)
                                    if (started) {
                                        vm.setMasterEnabled(true)
                                        // سجّل اليوم فوراً: لا ننتظر إعادة فتح التطبيق كي
                                        // يتحرك عدّاد الأيام.
                                        vm.recordProtectedDayNow()
                                    } else Toast.makeText(
                                        this@MainActivity,
                                        R.string.protection_start_failed,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    onSettled(started)
                                }
                            }
                        }
                    } else {
                        this@MainActivity.lifecycleScope.launch {
                            val outcome = vm.disableProtectionNow()
                            if (outcome.disabled && !outcome.focusActive) {
                                VpnController.stop(this@MainActivity)
                            }
                            onSettled(outcome.disabled)
                        }
                    }
                }

                fun startFocus(minutes: Int, strict: Boolean) {
                    requestNotificationThen {
                        withVpn {
                            this@MainActivity.lifecycleScope.launch {
                                val started = VpnController.startAndAwait(this@MainActivity)
                                if (started) {
                                    vm.startFocus(minutes, strict)
                                } else {
                                    Toast.makeText(this@MainActivity, R.string.protection_start_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }

                fun cancelFocus() {
                    vm.cancelFocus {
                        if (!vm.isMasterEnabledNow()) VpnController.stop(this@MainActivity)
                    }
                }

                LaunchedEffect(Unit) { vm.onAppOpened() }

                SawnScaffold(
                    vm = vm,
                    onToggleBlocking = { enabled, onSettled -> toggleBlocking(enabled, onSettled) },
                    onStartFocus = ::startFocus,
                    onCancelFocus = ::cancelFocus,
                )
            }
        }
    }
}

@Composable
private fun SawnScaffold(
    vm: SawnViewModel,
    onToggleBlocking: (Boolean, (Boolean) -> Unit) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onCancelFocus: () -> Unit,
) {
    val navController = rememberNavController()
    val settingsReady by vm.settingsReady.collectAsStateWithLifecycle()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // إذن الإشعارات يُطلب مرة واحدة بعد الترحيب — لا داخل زر التشغيل فقط. بدونه لا
    // يظهر إشعار «الحماية تعمل»، وبعض الشركات تقتل الخدمة الأمامية بلا إشعار.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(settingsReady, onboardingDone) {
        if (!settingsReady || !onboardingDone) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionsHelper.hasNotifications(context)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (!settingsReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (!onboardingDone) {
        OnboardingHost(vm = vm, onDone = vm::setOnboardingDone)
        return
    }

    var managementUnlocked by remember { mutableStateOf(false) }
    val master by vm.masterEnabled.collectAsStateWithLifecycle()
    val focusEndsAt by vm.focusEndsAt.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
    val enforcementActive by vm.enforcementActive.collectAsStateWithLifecycle()
    val vpnRunning by SawnVpnService.runningFlow.collectAsStateWithLifecycle()

    LaunchedEffect(enforcementActive, vpnRunning) {
        if (!enforcementActive || vpnRunning) return@LaunchedEffect
        if (VpnController.consentIntent(context) != null) return@LaunchedEffect
        repeat(3) {
            if (SawnVpnService.running) return@LaunchedEffect
            VpnController.start(context)
            repeat(16) {
                delay(400)
                if (SawnVpnService.running) return@LaunchedEffect
            }
        }
    }

    @Composable
    fun protectedScreen(title: String, content: @Composable () -> Unit) {
        ManagementPinGate(
            vm = vm,
            title = title,
            unlocked = managementUnlocked,
            onUnlocked = { managementUnlocked = true },
            content = content,
        )
    }

    // بعد الترحيب: إن كانت الطبقة الأساسية ناقصة نفتح شاشة الفحص مباشرة بدل
    // ترك المستخدم أمام رئيسيةٍ لا تحمي شيئاً.
    val startRoute = remember(context) {
        if (PermissionsHelper.essentialsGranted(context)) "home" else "setup"
    }

    Scaffold { padding ->
        NavHost(navController, startDestination = startRoute, modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    vm,
                    onToggleBlocking,
                    onStartFocus,
                    onCancelFocus,
                    onOpenSetup = { navController.navigate("setup") },
                    onOpenBlocklist = { navController.navigate("blocklist") },
                    onOpenSchedules = { navController.navigate("schedules") },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("setup") {
                SetupScreen(
                    onBack = {
                        // قد تكون هذه أول شاشة بعد الترحيب، فلا شيء في المكدس للرجوع.
                        if (!navController.popBackStack()) {
                            navController.navigate("home") {
                                popUpTo("setup") { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable("blocklist") {
                protectedScreen(stringResource(R.string.blocklist_title)) {
                    BlocklistScreen(
                        vm,
                        onOpenAppPicker = { navController.navigate("apppicker") },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("apppicker") {
                protectedScreen(stringResource(R.string.apps_title)) {
                    AppPickerScreen(vm, onBack = { navController.popBackStack() })
                }
            }
            composable("schedules") {
                protectedScreen(stringResource(R.string.schedules_title)) {
                    SchedulesScreen(vm, onBack = { navController.popBackStack() })
                }
            }
            composable("stats") {
                StatsScreen(vm, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                protectedScreen(stringResource(R.string.settings_title)) {
                    com.sawn.app.ui.settings.SettingsScreen(
                        vm = vm,
                        onOpenSetup = { navController.navigate("setup") },
                        onBack = { navController.popBackStack() },
                        onOpenProtection = { navController.navigate("settings/protection") },
                        onOpenBrowser = { navController.navigate("settings/browser") },
                        onOpenPrivacy = { navController.navigate("settings/privacy") },
                    )
                }
            }
            composable("settings/protection") {
                protectedScreen(stringResource(R.string.settings_protection)) {
                    ProtectionSettingsScreen(
                        vm,
                        onOpenSetup = { navController.navigate("setup") },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("settings/browser") {
                protectedScreen(stringResource(R.string.settings_browser)) {
                    BrowserSettingsScreen(
                        vm,
                        onOpenSetup = { navController.navigate("setup") },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("settings/privacy") {
                protectedScreen(stringResource(R.string.settings_privacy)) {
                    PrivacySupportSettingsScreen(
                        vm,
                        onOpenSetup = { navController.navigate("setup") },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
