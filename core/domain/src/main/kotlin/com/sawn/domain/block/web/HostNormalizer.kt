package com.sawn.domain.block.web

/**
 * Normalizes an address-bar reading into a bare host.
 *
 * The browser engine reads *whatever* the address bar shows: a full URL, a bare domain,
 * a breadcrumb ("example.com › page"), or a title in mixed case. All of them must end
 * up as the same bare host ("example.com") so the blocklist can be compared against it.
 *
 * Two rules that the generic [com.sawn.domain.block.DomainMatcher.normalizeUrl] cannot
 * express, because a browser bar is not a URL field:
 *  - a **breadcrumb** separator (› or ／) truncates the reading: the host is whatever
 *    came before it, the rest is the page title;
 *  - the reading may be a **bare title** ("Example.com") with no scheme at all.
 *
 * A reading with spaces is a search query, not a host, and is rejected — the same rule
 * the URL field follows.
 */
object HostNormalizer {

    /** Separators a browser uses between the host and the rest of the bar content. */
    private val BREADCRUMB_CHARS = charArrayOf('›', '／')

    /** The longest text we are willing to treat as a host (a title is much longer). */
    private const val MAX_HOST_LENGTH = 253

    /**
     * The bare host of [raw], or null when the text cannot be a host.
     *
     * - "https://www.Example.com/path?q=1" -> "example.com"
     * - "example.com › Some Page"            -> "example.com"
     * - "Example.com"                        -> "example.com"
     * - "how to block a site"                -> null
     */
    fun normalizeHost(raw: String?): String? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.isEmpty()) return null

        // A breadcrumb means the host is what came before the separator.
        var cut = text
        for (sep in BREADCRUMB_CHARS) {
            val idx = cut.indexOf(sep)
            if (idx >= 0) cut = cut.substring(0, idx)
        }
        cut = cut.trim()
        if (cut.isEmpty()) return null

        // A search query is not a host.
        if (cut.any { it.isWhitespace() }) return null

        // Strip the scheme, then everything after the path/query/fragment.
        val afterScheme = cut
            .removePrefix("https://")
            .removePrefix("http://")
        var host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.contains('@')) host = host.substringAfterLast('@') // userinfo
        host = host.substringBefore(':') // port
        host = host.trimEnd('.')
        if (host.startsWith("www.")) host = host.substring(4)
        if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return null
        if (host.any { it.isWhitespace() }) return null
        return host
    }
}
