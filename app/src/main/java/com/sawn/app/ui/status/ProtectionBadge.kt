package com.sawn.app.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sawn.app.R
import com.sawn.domain.block.ProtectionLevel

/**
 * The badge that tells the user, at a glance, what is actually protecting them.
 *
 * It is deliberately explicit: "browser-only" is not the same promise as "full", and a
 * user who believes they are fully protected when they are not is the exact failure this
 * badge exists to prevent.
 */
@Composable
fun ProtectionBadge(level: ProtectionLevel, modifier: Modifier = Modifier) {
    val (label, background, foreground) = when (level) {
        ProtectionLevel.Full -> Triple(
            stringResource(R.string.protection_badge_full),
            Color(0xFF1B6B45),
            Color.White,
        )
        ProtectionLevel.BrowserOnly -> Triple(
            stringResource(R.string.protection_badge_browser),
            Color(0xFF8A6D1F),
            Color.White,
        )
        ProtectionLevel.Paused -> Triple(
            stringResource(R.string.protection_badge_paused),
            Color(0xFF6B6B6B),
            Color.White,
        )
        ProtectionLevel.None -> Triple(
            stringResource(R.string.protection_badge_none),
            Color(0xFF8C2F2F),
            Color.White,
        )
    }
    Text(
        text = label,
        color = foreground,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
