package com.warden.blocker.ui

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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NoCell
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class Pillar(val icon: ImageVector, val title: String, val body: String)

private val PILLARS = listOf(
    Pillar(Icons.Filled.Web, "Block websites everywhere", "A local DNS filter blocks sites in every browser and app — no root, nothing proxied off your phone."),
    Pillar(Icons.Filled.NoCell, "Block apps & endless feeds", "Whole apps, or just Reels and Shorts inside them, with a mindful pause before you give in."),
    Pillar(Icons.Filled.Schedule, "Schedules, focus & strict mode", "Windows that wrap midnight, Pomodoro focus sessions, PIN lock and uninstall protection."),
)

/**
 * First-run welcome: the three pillars, one commercial notice (free tier, commercial
 * product, and the honest place ads will sit), then straight into setup.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShieldMark()
        Spacer(Modifier.height(12.dp))
        Text("Warden", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your attention, fenced in — by you, for you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        PILLARS.forEach { p ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(p.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Text(p.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Free to use · commercial app · a calm, reserved ad slot may appear below the home " +
                "screen — never on the block or pause screens. A paid plan (no ads, more power) comes later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Get started")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Skip")
        }
    }
}

@Composable
private fun ShieldMark() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
    }
}
