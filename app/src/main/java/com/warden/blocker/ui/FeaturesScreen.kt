package com.warden.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.feature.FeatureCatalog

@Composable
fun FeaturesScreen(vm: WardenViewModel, onBack: () -> Unit = {}) {
    val enabled by vm.enabledFeatureKeys.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader("Block in-app feeds", onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            "Block just the endless-scroll parts of apps you still want to use — Reels, Shorts, " +
                "the TikTok feed — while the rest keeps working. Needs the accessibility " +
                "permission and blocking switched on.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lockdownActive) {
            Spacer(Modifier.height(8.dp))
            Text(
                "A time lock is active — feed blocking cannot be turned off until it ends.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FeatureCatalog.ALL, key = { it.key }) { feature ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(feature.label, style = MaterialTheme.typography.titleMedium)
                            Text(feature.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = feature.key in enabled,
                            enabled = !lockdownActive,
                            onCheckedChange = { vm.setFeatureEnabled(feature.key, it) },
                        )
                    }
                }
            }
        }
    }
}
