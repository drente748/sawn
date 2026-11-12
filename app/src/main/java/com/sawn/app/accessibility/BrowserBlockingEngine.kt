package com.sawn.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.sawn.app.sawnContainer
import com.sawn.domain.block.BlockCoordinator
import com.sawn.domain.block.BlockSource
import com.sawn.domain.block.AdultHosts
import com.sawn.domain.block.DomainMatcher
import com.sawn.domain.block.web.AddressBarReading
import com.sawn.domain.block.web.BrowserHostExtractor
import com.sawn.domain.block.web.BrowserRegistry
import com.sawn.domain.block.web.BrowserSpec
import com.sawn.domain.feature.UiNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The accessibility browser engine: it reads the address bar of a known browser and
 * blocks what the blocklist forbids.
 *
 * This is the engine that makes the light mode (no VPN) useful. It sees only what the
 * browser shows in its own chrome — never inside a WebView — so a page opened inside
 * another app is *not* covered. That limitation is real and is documented in SECURITY.md.
 *
 * Two behaviours the spec calls for:
 *  - a **focused** bar means the user is typing: the reading is deferred by
 *    [TYPING_SETTLE_MS] and then re-read, so a half-typed domain is never blocked;
 *  - an **unfocused** bar means the browser already navigated there: the block happens
 *    at once, with no delay the user could click past.
 */
class BrowserBlockingEngine(
    private val service: AccessibilityService,
    private val coordinator: BlockCoordinator,
    private val onBlocked: (String) -> Unit,
    /**
     * Fetches the current accessibility tree.
     *
     * It must be called on the **main thread** — that is where the accessibility service
     * may read its windows. The tree it returns is a snapshot, so the walk over it can
     * safely happen on a background thread.
     */
    private val rootProvider: () -> UiNode?,
) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "Browser block failed: ${error.javaClass.simpleName}")
        },
    )

    /** The main-thread handler used to fetch the tree. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** One address-bar read per package per [SCAN_INTERVAL_MS]. */
    private val lastScanAt = HashMap<String, Long>()

    /** The pending "the user stopped typing" re-read, per package. */
    private val pendingReads = HashMap<String, Job>()

    @Volatile private var destroyed = false

    /** The last host this engine blocked, so the same page is not blocked twice. */
    @Volatile var lastBlockedHost: String? = null
        private set

    /**
     * Called on every window/content change of a browser package.
     *
     * Returns without doing anything when the package is not a known browser, when the
     * browser engine is switched off, or when the read is throttled.
     */
    fun onWindowChanged(pkg: String) {
        val spec = BrowserRegistry.byPackage(pkg) ?: return
        if (destroyed) return
        if (!shouldScan(pkg)) return

        scope.launch {
            try {
                if (!isBrowserEngineActive()) return@launch
                val reading = read(spec) ?: return@launch
                val host = reading.host ?: return@launch

                if (reading.focused) {
                    // TYPING: the text is still being written. Wait for the user to stop,
                    // then read again — a domain that is only half typed must not be blocked.
                    scheduleReread(pkg, spec)
                    return@launch
                }

                // VISITING: the browser already went there.
                blockIfNeeded(host)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Browser scan failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Reads the address bar on the main thread and walks the snapshot off it.
     */
    private suspend fun read(spec: BrowserSpec): AddressBarReading? {
        val root = withMainThread { rootProvider() } ?: return null
        return BrowserHostExtractor.extractHostFromTree(root, spec)
    }

    /**
     * Re-reads the bar after the user stopped typing. The re-read is what decides: if the
     * bar is still focused, another wait is scheduled; otherwise the host is judged.
     */
    private fun scheduleReread(pkg: String, spec: BrowserSpec) {
        pendingReads[pkg]?.cancel()
        pendingReads[pkg] = scope.launch {
            delay(TYPING_SETTLE_MS)
            if (destroyed) return@launch
            val reading = read(spec)
            val host = reading?.host
            if (host == null) return@launch
            if (reading?.focused == true) {
                // Still typing: wait once more, but never forever.
                scheduleReread(pkg, spec)
                return@launch
            }
            blockIfNeeded(host)
        }
    }

    /** Blocks [host] when the coordinator says so, and reports it. */
    private suspend fun blockIfNeeded(host: String) {
        if (host == lastBlockedHost) return
        // The adult-content monitor owns the adult list and shows the full intercept
        // screen for it; two screens for one page would be a mess.
        if (DomainMatcher.matches(host, ADULT_HOSTS)) return
        val decision = coordinator.evaluateDomain(host)
        if (!decision.blocked) return
        lastBlockedHost = host
        coordinator.reportBlock(decision, decision.source ?: BlockSource.BROWSER)
        onBlocked(host)
    }

    /** True when the browser engine is switched on and the app is enforcing protection. */
    private suspend fun isBrowserEngineActive(): Boolean {
        val container = service.sawnContainer
        val snapshot = container.settings.protectionSnapshot()
        if (!snapshot.browserBlockingEnabled) return false
        return container.blockEngine.isBlockingActiveNow(peekOnly = true)
    }

    private fun shouldScan(pkg: String): Boolean {
        val now = SystemClock.uptimeMillis()
        synchronized(lastScanAt) {
            val last = lastScanAt[pkg] ?: 0L
            if (now - last < SCAN_INTERVAL_MS) return false
            lastScanAt[pkg] = now
        }
        return true
    }

    /** Forgets the last blocked host, so the same page can be blocked again later. */
    fun clearLastBlocked() {
        lastBlockedHost = null
    }

    fun destroy() {
        destroyed = true
        scope.cancel()
        synchronized(lastScanAt) { lastScanAt.clear() }
        pendingReads.values.forEach { it.cancel() }
        pendingReads.clear()
    }

    /**
     * Runs [block] on the main thread and waits for its result.
     *
     * The accessibility tree may only be read on the main thread; the walk over the
     * snapshot it returns happens on the caller's (background) thread.
     */
    private suspend fun <T> withMainThread(block: () -> T): T? = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            if (cont.isActive) {
                cont.resume(runCatching(block).getOrNull())
            }
        }
    }

    companion object {
        private const val TAG = "SawnBrowser"
        private const val SCAN_INTERVAL_MS = 500L

        /** How long to wait after the user stops typing before reading the bar again. */
        const val TYPING_SETTLE_MS = 300L

        /** The hosts the adult-content monitor already handles on its own. */
        private val ADULT_HOSTS: Set<String> =
            (AdultHosts.ALL + AdultHosts.REDDIT_MEDIA).toSet()
    }
}
