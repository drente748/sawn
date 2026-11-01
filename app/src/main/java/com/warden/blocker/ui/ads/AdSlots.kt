package com.warden.blocker.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warden.blocker.wardenContainer

/**
 * Reserved placeholders for the future ad layer. No ad SDK is integrated yet — these
 * slots render nothing today and become the only ad surfaces when ads arrive, so the
 * layout never shifts and the rule "no ads on block/pause screens" is structurally
 * guaranteed (these slots simply do not exist on those screens).
 */
enum class AdSlot { HOME_BANNER, STATS_NATIVE }

object AdPolicy {
    /** The only two allowed surfaces, by design doc. */
    val ALLOWED_SLOTS = setOf(AdSlot.HOME_BANNER, AdSlot.STATS_NATIVE)
}

/**
 * Renders nothing in the free-tier-only build. When the ad SDK lands, this composable
 * inflates the real ad for [slot] unless the user disabled ads or is premium.
 */
@Composable
fun AdSlotBanner(
    slot: AdSlot,
    modifier: Modifier = Modifier,
) {
    if (slot !in AdPolicy.ALLOWED_SLOTS) return
    val premium by LocalContext.current.wardenContainer.entitlements.isPremium
        .collectAsStateWithLifecycle(initialValue = false)
    if (premium) return
    // Reserved live-space: renders nothing today (zero height).
}
