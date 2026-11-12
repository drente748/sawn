package com.sawn.domain.block.web

/**
 * A browser Sawn can read an address bar from.
 *
 * [urlBarIds] holds **resource-name suffixes** ("url_bar", "origin",
 * "ADDRESSBAR_URL_BOX"), not fully-qualified ids. A fully-qualified id is
 * "<package>:id/<name>", so the same suffix works across browsers and across
 * OEM rebuilds that rename the package but keep the widget — matching on
 * `viewId.substringAfterLast('/')` is what makes one list cover many browsers.
 */
data class BrowserSpec(
    val packageName: String,
    val displayName: String,
    val urlBarIds: List<String>,
) {
    /** The suffixes as a set, for O(1) lookups while walking the node tree. */
    val urlBarIdSet: Set<String> = urlBarIds.toSet()

    companion object {
        /**
         * Builds a spec from the [BrowserConfig] table (116 browsers), deriving the
         * suffixes from its fully-qualified ids.
         */
        fun from(config: BrowserConfig, displayName: String): BrowserSpec = BrowserSpec(
            packageName = config.packageName,
            displayName = displayName,
            urlBarIds = config.urlViewIds,
        )
    }
}
