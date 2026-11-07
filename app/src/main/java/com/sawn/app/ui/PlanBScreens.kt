package com.sawn.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.domain.feature.ChallengePolicy

@Composable
fun ChallengeScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val days by vm.challengeDays.collectAsStateWithLifecycle(initialValue = 0)
    val stage = ChallengePolicy.stageFor(days)
    val completed = ChallengePolicy.isComplete(days)
    val remaining = ChallengePolicy.daysRemaining(days)

    PlanBScreenShell(title = stringResource(R.string.feature_challenge_title), onBack = onBack) {
        Text(
            text = if (completed) stringResource(R.string.feature_challenge_victory)
            else stringResource(R.string.feature_challenge_progress, days, ChallengePolicy.TOTAL_DAYS),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.feature_challenge_remaining, remaining), style = MaterialTheme.typography.bodyLarge)
        stage?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.feature_challenge_stage, it.milestone), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.feature_challenge_how), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.feature_challenge_how_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.startChallenge() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_challenge_start))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.recordChallengeDay() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_challenge_record))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.restartChallenge() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_challenge_restart))
        }
    }
}

@Composable
fun NightGuardScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val enabled by vm.nightGuardEnabled.collectAsStateWithLifecycle(initialValue = false)
    val startMin by vm.nightGuardStartMin.collectAsStateWithLifecycle(initialValue = 1320)
    val endMin by vm.nightGuardEndMin.collectAsStateWithLifecycle(initialValue = 360)
    var startH by remember { mutableStateOf((startMin / 60).toString()) }
    var startM by remember { mutableStateOf((startMin % 60).toString()) }
    var endH by remember { mutableStateOf((endMin / 60).toString()) }
    var endM by remember { mutableStateOf((endMin % 60).toString()) }

    PlanBScreenShell(title = stringResource(R.string.feature_night_guard_title), onBack = onBack) {
        Text(stringResource(R.string.feature_night_guard_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            label = stringResource(R.string.feature_night_guard_enable),
            checked = enabled,
            onCheckedChange = { on ->
                val s = (startH.toIntOrNull() ?: 22) * 60 + (startM.toIntOrNull() ?: 0)
                val e = (endH.toIntOrNull() ?: 6) * 60 + (endM.toIntOrNull() ?: 0)
                vm.setNightGuard(on, s, e)
            },
        )
        OutlinedTextField(
            value = startH, onValueChange = { startH = it },
            label = { Text(stringResource(R.string.feature_night_guard_start_h)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = endH, onValueChange = { endH = it },
            label = { Text(stringResource(R.string.feature_night_guard_end_h)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.feature_night_guard_note), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.requestNightGuardUnlock() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_night_guard_unlock))
        }
    }
}

@Composable
fun DeepFocusScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val enabled by vm.deepFocusEnabled.collectAsStateWithLifecycle(initialValue = false)
    val whitelist by vm.deepFocusWhitelist.collectAsStateWithLifecycle(initialValue = emptySet())
    var unlockIn by remember { mutableStateOf(0) }

    PlanBScreenShell(title = stringResource(R.string.feature_deep_focus_title), onBack = onBack) {
        Text(stringResource(R.string.feature_deep_focus_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            label = stringResource(R.string.feature_deep_focus_enable),
            checked = enabled,
            onCheckedChange = { vm.setDeepFocusEnabled(it) },
        )
        Text(
            stringResource(R.string.feature_deep_focus_whitelist, whitelist.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.feature_deep_focus_note), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.requestDeepFocusUnlock() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_deep_focus_unlock))
        }
    }
}

@Composable
fun AccountabilityScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val name by vm.accountabilityName.collectAsStateWithLifecycle(initialValue = null)
    var nameText by remember { mutableStateOf("") }
    var contactText by remember { mutableStateOf("") }

    PlanBScreenShell(title = stringResource(R.string.feature_accountability_title), onBack = onBack) {
        Text(stringResource(R.string.feature_accountability_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nameText, onValueChange = { nameText = it },
            label = { Text(stringResource(R.string.feature_accountability_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = contactText, onValueChange = { contactText = it },
            label = { Text(stringResource(R.string.feature_accountability_contact)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.setAccountability(nameText.ifBlank { null }, contactText.ifBlank { null }) }) {
            Text(stringResource(R.string.save))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.shareWeeklyReport(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_accountability_share))
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    PlanBScreenShell(title = stringResource(R.string.feature_backup_title), onBack = onBack) {
        Text(stringResource(R.string.feature_backup_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = passphrase, onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.feature_backup_passphrase)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                status = if (passphrase.length >= 8) {
                    vm.exportBackup(context, passphrase)
                    "exported"
                } else "short"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_backup_export))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                status = if (passphrase.length >= 8) {
                    vm.importBackup(context, passphrase)
                    "imported"
                } else "short"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_backup_import))
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.feature_backup_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TriggersScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val topTag by vm.topTriggerTag.collectAsStateWithLifecycle(initialValue = null)
    val peakHour by vm.peakHour.collectAsStateWithLifecycle(initialValue = null)

    PlanBScreenShell(title = stringResource(R.string.feature_triggers_title), onBack = onBack) {
        Text(stringResource(R.string.feature_triggers_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.feature_triggers_top, topTag ?: "—"),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.feature_triggers_peak, peakHour?.toString() ?: "—"),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        com.sawn.domain.feature.TriggerTag.entries.forEach { tag ->
            OutlinedButton(onClick = { vm.logTrigger(tag) }, modifier = Modifier.fillMaxWidth()) {
                Text(tag.name)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun ParentModeScreen(onBack: () -> Unit = {}) {
    val vm: SawnViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val enabled by vm.parentModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    var pin by remember { mutableStateOf("") }

    PlanBScreenShell(title = stringResource(R.string.feature_parent_title), onBack = onBack) {
        Text(stringResource(R.string.feature_parent_sub), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            label = stringResource(R.string.feature_parent_enable),
            checked = enabled,
            onCheckedChange = { vm.setParentModeEnabled(it) },
        )
        OutlinedTextField(
            value = pin, onValueChange = { pin = it },
            label = { Text(stringResource(R.string.feature_parent_pin)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.setParentPin(pin) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pin_set_title))
        }
        Spacer(Modifier.height(12.dp))
        val ctx = LocalContext.current
        OutlinedButton(onClick = { vm.shareWeeklyReport(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_accountability_share))
        }
    }
}

@Composable
private fun PlanBScreenShell(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Text(label, Modifier.padding(16.dp))
        Spacer(Modifier.height(4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.padding(16.dp))
    }
}
