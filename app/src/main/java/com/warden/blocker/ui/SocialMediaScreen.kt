package com.warden.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.feature.SocialPlatform
import com.warden.blocker.util.PermissionsHelper

@Composable
fun SocialMediaScreen(vm: WardenViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val guardEnabled by vm.socialGuard.collectAsStateWithLifecycle()
    val enabledPlatforms by vm.enabledSocialPlatforms.collectAsStateWithLifecycle()
    val masterEnabled by vm.masterEnabled.collectAsStateWithLifecycle()
    var accessibilityGranted by remember { mutableStateOf(PermissionsHelper.isAccessibilityEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        accessibilityGranted = PermissionsHelper.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }

    var showFilterTips by remember { mutableStateOf(false) }
    val selectedCount = SocialPlatform.entries.count { it.key in enabledPlatforms }
    val allSelected = selectedCount == SocialPlatform.entries.size
    val enforcing = guardEnabled && selectedCount > 0 && masterEnabled && accessibilityGranted

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader("Social media protection", onBack)
            Spacer(Modifier.height(6.dp))
            Text(
                "Block detected adult and NSFW screens inside social apps while keeping the rest of each app available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Keep the app, skip the adult screens", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Social Guard leaves the rest of each app usable. To block Twitter, Reddit or any app entirely, add it in Blocklist instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text("Social Guard", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (enforcing) "Active for $selectedCount ${if (selectedCount == 1) "app" else "apps"}"
                            else "$selectedCount of ${SocialPlatform.entries.size} apps selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enforcing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = guardEnabled, onCheckedChange = vm::setSocialGuard)
                }
            }
        }

        if (!accessibilityGranted || !masterEnabled) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (!accessibilityGranted) "Accessibility is needed to detect adult screens inside other apps."
                            else "Turn on Warden's main protection to enforce these choices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (!accessibilityGranted) {
                            TextButton(onClick = onOpenSetup) { Text("Open setup") }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Protected apps", style = MaterialTheme.typography.titleLarge)
                    Text("Choose each app independently", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.setAllSocialPlatforms(!allSelected) }) {
                    Text(if (allSelected) "Clear all" else "Select all")
                }
            }
        }

        items(SocialPlatform.entries, key = { it.key }) { platform ->
            SocialPlatformCard(
                platform = platform,
                enabled = platform.key in enabledPlatforms,
                onToggle = { vm.setSocialPlatformEnabled(platform.key, it) },
            )
        }

        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Strengthen the apps' own filters",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showFilterTips = !showFilterTips }) {
                            Text(if (showFilterTips) "Hide" else "View")
                        }
                    }
                    if (showFilterTips) {
                        Text(
                            "These built-in settings add server-side filtering. Warden cannot change them for you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SocialPlatform.entries.forEach { platform ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(platform.label, style = MaterialTheme.typography.titleSmall)
                                Text(platform.builtInFilterTip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Private, on-device detection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Warden checks visible warnings, community names and search fields. It never takes screenshots or uploads what you see.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Photos and videos without an NSFW label or identifying text may not be detected. Coverage can vary by app version and language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialPlatformCard(
    platform: SocialPlatform,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val accent = when (platform) {
        SocialPlatform.REDDIT -> Color(0xFFFF4500)
        SocialPlatform.FACEBOOK -> Color(0xFF1877F2)
        SocialPlatform.INSTAGRAM -> Color(0xFFC13584)
        SocialPlatform.X -> Color(0xFF657786)
        SocialPlatform.TIKTOK -> Color(0xFF008C95)
        SocialPlatform.YOUTUBE -> Color(0xFFFF0000)
    }
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f), modifier = Modifier.size(44.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        platform.monogram,
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(platform.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    platform.coverage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
