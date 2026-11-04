package com.sawn.app.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tier seam for the future paid plan. Ship code against this interface, never against a
 * billing SDK — when subscriptions arrive, only the implementation swaps out.
 *
 * The current implementation is the free tier: `isPremium` is always false. The two future
 * ad surfaces (Home banner, Stats native) and any premium feature check this first, so
 * flipping to real billing state removes ads and unlocks features with zero other changes.
 */
interface Entitlements {
    val isPremium: Flow<Boolean>
    val tierName: String
}

/** Free tier — the only tier until billing is integrated. */
class FreeEntitlements : Entitlements {
    override val isPremium: Flow<Boolean> = MutableStateFlow(false)
    override val tierName: String = "Free"
}
