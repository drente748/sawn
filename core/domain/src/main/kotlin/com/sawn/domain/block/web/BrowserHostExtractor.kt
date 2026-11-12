package com.sawn.domain.block.web

import com.sawn.domain.feature.UiNode

/**
 * What the browser engine found in the address bar.
 *
 * [focused] is the difference between *typing* and *visiting*. A focused bar means the
 * user is still editing it — the text is half-written and must never be blocked. An
 * unfocused bar means the browser already navigated there, so the host can be blocked
 * at once.
 */
data class AddressBarReading(
    val host: String?,
    val focused: Boolean = false,
) {
    /** True when the user is typing: the reading is not trustworthy yet. */
    val isTyping: Boolean get() = focused

    /** True when a real, settled host was read. */
    val hasHost: Boolean get() = host != null
}

/**
 * Finds the address bar in an accessibility tree and reads the host from it.
 *
 * The walk is deliberately bounded ([MAX_NODES]) and skips web-content subtrees:
 *
 *  - A browser page lives in a WebView. Descending into it would read the *page's* text
 *    (article titles, links) instead of the address bar, and a page can hold thousands of
 *    nodes. Light mode therefore never sees inside a WebView — which is exactly the
 *    documented limitation of that mode.
 *  - The walk stops after [MAX_NODES] nodes, so a heavy page can never freeze the
 *    accessibility service.
 *
 * Nodes are matched by the **last segment** of their view id ("com.android.chrome:id/url_bar"
 * -> "url_bar"), so one list of ids covers every browser that uses the same widget name,
 * and an OEM rebuild that renames the package still matches.
 */
object BrowserHostExtractor {

    /** Hard cap on the nodes visited in one walk. */
    const val MAX_NODES = 80

    /** Class names that mark web content, which is never descended into. */
    private val WEB_CONTENT_CLASSES = listOf("webview", "webcontents")

    /**
     * Reads the address bar of [root] for the browser described by [spec].
     *
     * Returns null when no bar was found, when the found bar is empty, or when the bar
     * holds a search query rather than a host.
     */
    fun extractHostFromTree(root: UiNode?, spec: BrowserSpec): AddressBarReading? {
        if (root == null) return null
        val queue = ArrayDeque<UiNode>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty()) {
            if (visited >= MAX_NODES) return null
            val node = queue.removeFirst()
            visited++
            if (isWebContent(node)) continue
            if (matchesAddressBar(node, spec)) {
                val host = HostNormalizer.normalizeHost(node.text ?: node.contentDesc)
                if (host != null) return AddressBarReading(host = host, focused = node.isFocused)
            }
            for (i in 0 until node.childCount) node.child(i)?.let { queue.addLast(it) }
        }
        return null
    }

    /** True when the node is one of the browser's address-bar widgets. */
    private fun matchesAddressBar(node: UiNode, spec: BrowserSpec): Boolean {
        val viewId = node.viewId ?: return false
        val suffix = viewId.substringAfterLast('/')
        return suffix in spec.urlBarIdSet
    }

    /** True for web-content nodes (a page's own DOM), which are never entered. */
    private fun isWebContent(node: UiNode): Boolean {
        val className = node.className?.lowercase() ?: return false
        return WEB_CONTENT_CLASSES.any { className.contains(it) }
    }
}
