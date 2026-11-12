package com.sawn.domain.block.web

import com.sawn.domain.feature.UiNode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The browser engine's read of the address bar. The two rules that matter: the walk is
 * bounded (a heavy page must never freeze the accessibility service), and web content is
 * never entered (light mode cannot see inside a WebView, and must not pretend to).
 */
class BrowserHostExtractorTest : StringSpec({

    "the address bar of a known browser is found and read" {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.android.chrome:id/toolbar"),
                FakeNode(
                    viewId = "com.android.chrome:id/url_bar",
                    text = "https://www.example.com/page",
                ),
            ),
        )

        val reading = BrowserHostExtractor.extractHostFromTree(root, BrowserRegistry.CHROME)

        reading?.host shouldBe "example.com"
        reading?.isTyping shouldBe false
    }

    "a node is matched by the last segment of its view id" {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com"),
            ),
        )

        BrowserHostExtractor.extractHostFromTree(root, BrowserRegistry.CHROME)?.host shouldBe
            "example.com"
    }

    "another browser's bar is not read as this browser's bar" {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com"),
            ),
        )

        BrowserHostExtractor.extractHostFromTree(root, BrowserRegistry.FIREFOX) shouldBe null
    }

    "a focused bar is reported as typing, so it is never blocked mid-word" {
        val root = FakeNode(
            children = listOf(
                FakeNode(
                    viewId = "com.android.chrome:id/url_bar",
                    text = "example.com",
                    isFocused = true,
                ),
            ),
        )
        val reading = BrowserHostExtractor.extractHostFromTree(root, BrowserRegistry.CHROME)

        reading?.host shouldBe "example.com"
        reading?.isTyping shouldBe true
    }

    "web content is never entered" {
        val root = FakeNode(
            children = listOf(
                FakeNode(
                    className = "android.webkit.WebView",
                    children = listOf(
                        FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com"),
                    ),
                ),
            ),
        )

        BrowserHostExtractor.extractHostFromTree(root, BrowserRegistry.CHROME) shouldBe null
    }

    "the walk stops after the node budget" {
        // A deep chain with the bar only at the very bottom: the walk must give up
        // rather than reach it.
        var node = FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com")
        repeat(BrowserHostExtractor.MAX_NODES + 50) {
            node = FakeNode(children = listOf(node))
        }

        BrowserHostExtractor.extractHostFromTree(node, BrowserRegistry.CHROME) shouldBe null
    }

    "a bar inside the budget is still found" {
        var node = FakeNode(viewId = "com.android.chrome:id/url_bar", text = "example.com")
        repeat(10) { node = FakeNode(children = listOf(node)) }

        BrowserHostExtractor.extractHostFromTree(node, BrowserRegistry.CHROME)?.host shouldBe
            "example.com"
    }

    "a search query in the bar is not a host" {
        val root = FakeNode(
            children = listOf(
                FakeNode(viewId = "com.android.chrome:id/url_bar", text = "how to block a site"),
            ),
        )

        BrowserHostExtractor.extractHostFromTree(root, BrowserRegistry.CHROME) shouldBe null
    }

    "an empty tree yields nothing" {
        BrowserHostExtractor.extractHostFromTree(null, BrowserRegistry.CHROME) shouldBe null
    }
})

/** A [UiNode] built in memory, so the walk can be tested without a device. */
private class FakeNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDesc: String? = null,
    override val className: String? = null,
    override val isFocused: Boolean = false,
    private val children: List<FakeNode> = emptyList(),
) : UiNode {
    override val childCount: Int get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
}
