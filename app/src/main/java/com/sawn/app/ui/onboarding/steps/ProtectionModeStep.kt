package com.sawn.app.ui.onboarding.steps

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import com.sawn.app.ui.PillCTA
import com.sawn.app.ui.dawnBackground
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.domain.block.ProtectionMode

/**
 * «كيف يحميك صَوْن؟» — the mode choice, asked *before* the permissions.
 *
 * The user picks between two named promises, not a switch: full protection (the DNS
 * filter, which needs the connection permission) or light protection (accessibility
 * only). Saying it here, plainly, is what keeps the later permission prompt from
 * feeling like a trap — and it is the only place the difference can be explained
 * honestly, because the difference is a limitation, not a preference.
 */
@Composable
fun ProtectionModeStep(
    selected: ProtectionMode,
    onSelect: (ProtectionMode) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .dawnBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_mode_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = SawnPalette.Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.onboarding_mode_body),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.InkSoft,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeChoice(
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.protection_mode_full),
                description = stringResource(R.string.protection_mode_full_desc),
                selected = selected == ProtectionMode.FULL,
                onClick = { onSelect(ProtectionMode.FULL) },
            )
            ModeChoice(
                icon = Icons.Filled.Bolt,
                title = stringResource(R.string.protection_mode_light),
                description = stringResource(R.string.protection_mode_light_desc),
                selected = selected == ProtectionMode.LIGHT,
                onClick = { onSelect(ProtectionMode.LIGHT) },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillCTA(text = stringResource(R.string.onboarding_welcome_cta), onClick = onContinue)
            SpacerHeight(16.dp)
        }
    }
}

@Composable
private fun ModeChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) SawnPalette.PrimarySoft else SawnPalette.Card,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) SawnPalette.Primary else SawnPalette.Line,
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) SawnPalette.PrimaryDeep else SawnPalette.InkSoft,
                modifier = Modifier.size(22.dp),
            )
            SpacerWidth(12.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SawnPalette.Ink,
                )
                SpacerHeight(4.dp)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SawnPalette.InkSoft,
                )
            }
        }
    }
}

@Composable
private fun SpacerWidth(value: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.layout.Spacer(Modifier.width(value))

@Composable
private fun SpacerHeight(value: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.layout.Spacer(Modifier.height(value))
