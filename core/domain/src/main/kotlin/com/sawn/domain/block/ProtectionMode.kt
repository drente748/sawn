package com.sawn.domain.block

/**
 * The two ways Sawn can protect the user.
 *
 * The choice is made by the user in the settings screen, as two named options rather than
 * a toggle: the difference between them is not a preference but a promise about what
 * is (and is not) protected.
 *
 * - [FULL] — the DNS filter runs *and* the accessibility engines run. Every lookup the
 *   device makes is checked, so content inside other apps is covered too. It costs
 *   battery and needs the VPN consent.
 * - [LIGHT] — only the accessibility engines run. Websites are still blocked in known
 *   browsers, but content inside a WebView of another app is not seen.
 */
enum class ProtectionMode {
    FULL,
    LIGHT,
    ;

    /** Whether the DNS/VPN engine takes part in the protection. */
    val usesDnsEngine: Boolean get() = this == FULL

    /** Whether the accessibility engines take part in the protection. */
    val usesAccessibilityEngines: Boolean get() = true

    companion object {
        /** The mode a fresh install starts in, and the one an upgrade keeps. */
        val DEFAULT: ProtectionMode = FULL

        fun fromName(value: String?): ProtectionMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
