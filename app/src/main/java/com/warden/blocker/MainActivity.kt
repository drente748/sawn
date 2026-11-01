package com.warden.blocker

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.warden.blocker.ui.AppPickerScreen
import com.warden.blocker.ui.BlocklistScreen
import com.warden.blocker.ui.FeaturesScreen
import com.warden.blocker.ui.HomeScreen
import com.warden.blocker.ui.ManagementPinGate
import com.warden.blocker.ui.OnboardingScreen
import com.warden.blocker.ui.SchedulesScreen
import com.warden.blocker.ui.SettingsScreen
import com.warden.blocker.ui.SetupScreen
import com.warden.blocker.ui.StatsScreen
import com.warden.blocker.ui.WardenViewModel
import com.warden.blocker.ui.theme.WardenTheme
import com.warden.blocker.vpn.VpnController
import com.warden.blocker.vpn.WardenVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WardenTheme {
                val vm: WardenViewModel = viewModel()

                val notifPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result ignored; the app still works without it */ }
                LaunchedEffect(Unit) {
                    vm.onAppOpened()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var pendingAfterConsent by remember { mutableStateOf<(() -> Unit)?>(null) }
                val consentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) pendingAfterConsent?.invoke()
                    pendingAfterConsent = null
                }

                fun withVpn(action: () -> Unit) {
                    val consent = VpnController.consentIntent(this@MainActivity)
                    if (consent == null) action() else { pendingAfterConsent = action; consentLauncher.launch(consent) }
                }

                fun toggleBlocking(enabled: Boolean) {
                    if (enabled) {
                        withVpn { VpnController.start(this@MainActivity); vm.setMasterEnabled(true) }
                    } else {
                        // Strict/focus locks are authoritative here, not the possibly-stale UI.
                        this@MainActivity.lifecycleScope.launch {
                            val outcome = vm.disableProtectionNow()
                            if (!outcome.disabled) return@launch
                            // A live focus session keeps forcing blocking on; leave its tunnel up.
                            if (!outcome.focusActive) VpnController.stop(this@MainActivity)
                        }
                    }
                }

                fun startFocus(minutes: Int, strict: Boolean) {
                    withVpn {
                        this@MainActivity.lifecycleScope.launch {
                            // Persist only after the tunnel is actually up, so denying consent
                            // cannot leave a strict session with no filter behind it.
                            if (VpnController.startAndAwait(this@MainActivity)) {
                                vm.startFocus(minutes, strict)
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Warden's filter didn't start — try again",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }

                fun cancelFocus() {
                    // Only tear the tunnel down after the cancellation actually succeeded.
                    vm.cancelFocus {
                        if (!vm.isMasterEnabledNow()) VpnController.stop(this@MainActivity)
                    }
                }

                WardenScaffold(vm, ::toggleBlocking, ::startFocus, ::cancelFocus)
            }
        }
    }
}

@Composable
private fun WardenScaffold(
    vm: WardenViewModel,
    onToggleBlocking: (Boolean) -> Unit,
    onStartFocus: (Int, Boolean) -> Unit,
    onCancelFocus: () -> Unit,
) {
    val navController = rememberNavController()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    if (!onboardingDone) {
        OnboardingScreen(
            onDone = {
                vm.setOnboardingDone()
            },
        )
        return
    }

    // Self-healing guard, active on every screen: if blocking should be live but the tunnel
    // dropped (OEM battery killer, service crash), restart it. Consent is only re-requested
    // by the visible "Protection paused" card — never from this silent path.
    val context = LocalContext.current
    val master by vm.masterEnabled.collectAsStateWithLifecycle()
    val focusEndsAt by vm.focusEndsAt.collectAsStateWithLifecycle()
    val vpnRunning by WardenVpnService.runningFlow.collectAsStateWithLifecycle()
    val shouldBlock = master || focusEndsAt > System.currentTimeMillis()
    LaunchedEffect(shouldBlock, vpnRunning, focusEndsAt) {
        if (!shouldBlock || vpnRunning) return@LaunchedEffect
        if (VpnController.consentIntent(context) != null) return@LaunchedEffect
        repeat(3) {
            if (WardenVpnService.running) return@LaunchedEffect
            runCatching { VpnController.start(context) }
            repeat(15) {
                delay(400)
                if (WardenVpnService.running) return@LaunchedEffect
            }
        }
    }

    Scaffold { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    vm, onToggleBlocking, onStartFocus, onCancelFocus,
                    onOpenSetup = { navController.navigate("setup") },
                    onOpenBlocklist = { navController.navigate("blocklist") },
                    onOpenFeatures = { navController.navigate("features") },
                    onOpenSchedules = { navController.navigate("schedules") },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("setup") {
                SetupScreen(onBack = { navController.popBackStack() })
            }
            composable("blocklist") {
                ManagementPinGate(vm, "Blocklist") {
                    BlocklistScreen(
                        vm,
                        onOpenAppPicker = { navController.navigate("apppicker") },
                        onOpenFeatures = { navController.navigate("features") },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("apppicker") {
                ManagementPinGate(vm, "App blocking") {
                    AppPickerScreen(vm, onBack = { navController.popBackStack() })
                }
            }
            composable("features") {
                ManagementPinGate(vm, "Feed blocking") {
                    FeaturesScreen(vm, onBack = { navController.popBackStack() })
                }
            }
            composable("schedules") {
                ManagementPinGate(vm, "Schedules") {
                    SchedulesScreen(vm, onBack = { navController.popBackStack() })
                }
            }
            composable("stats") {
                StatsScreen(vm, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    vm,
                    onOpenSetup = { navController.navigate("setup") },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
