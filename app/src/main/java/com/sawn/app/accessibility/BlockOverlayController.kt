package com.sawn.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Shows the block notice over the browser, using an accessibility overlay.
 *
 * The overlay is a [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window: it
 * belongs to the accessibility service, so it needs **no** extra permission — the user
 * is never asked for "draw over other apps". That is the whole point of doing it here
 * instead of with SYSTEM_ALERT_WINDOW, which is one of the scariest permissions an app
 * can ask for and a real barrier to distribution.
 *
 * The window is deliberately transparent and non-focusable: it must not steal the
 * keyboard from the browser, and it must not trap the user. It shows the notice, and the
 * browser is left one BACK press away.
 */
class BlockOverlayController(private val service: AccessibilityService) {

    private var overlay: View? = null

    /** Removes the notice when it has been up long enough to be read. */
    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { hide() }

    /**
     * Shows the notice for [host] over the current app.
     *
     * Any previous overlay is replaced, so a second block never stacks two windows. The
     * notice removes itself after [VISIBLE_MS] — it is a message, not a wall: the page
     * behind it has already been left, and the user must not be trapped by it.
     */
    fun show(host: String) {
        val windowManager = service.getSystemService(WindowManager::class.java) ?: return
        hide()
        val view = buildView(host) ?: return
        val params = buildParams()
        // addView can throw when the service is being torn down at the same moment;
        // a failed overlay must never take the service down with it.
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (added) {
            overlay = view
            dismissHandler.removeCallbacks(dismissRunnable)
            dismissHandler.postDelayed(dismissRunnable, VISIBLE_MS)
        } else {
            runCatching { windowManager.removeView(view) }
        }
    }

    /** Removes the notice, if one is showing. */
    fun hide() {
        dismissHandler.removeCallbacks(dismissRunnable)
        val view = overlay ?: return
        overlay = null
        val windowManager = service.getSystemService(WindowManager::class.java) ?: return
        runCatching { windowManager.removeView(view) }
            .onFailure { Log.w(TAG, "Overlay removal failed: ${it.javaClass.simpleName}") }
    }

    /** True while a notice is on screen. */
    val isShowing: Boolean get() = overlay != null

    /**
     * The overlay is laid out over the whole screen, including the area behind the
     * status bar, so the notice is not pushed down by the system bars.
     */
    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun buildView(host: String): View {
        val density = service.resources.displayMetrics.density
        val padding = (12 * density).toInt()

        val container = FrameLayout(service).apply {
            setBackgroundColor(BACKGROUND_COLOR)
            setPadding(padding, padding, padding, padding)
            // The notice sits at the top of the screen, over the browser's own chrome.
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            )
        }
        val text = TextView(service).apply {
            this.text = service.getString(
                com.sawn.app.R.string.browser_blocked_notice,
                host,
            )
            setTextColor(TEXT_COLOR)
            textSize = 14f
        }
        container.addView(
            text,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return container
    }

    companion object {
        private const val TAG = "SawnOverlay"

        /** How long the notice stays up before it removes itself. */
        private const val VISIBLE_MS = 4_000L

        /** A dark, opaque enough band that the text is readable over any page. */
        private const val BACKGROUND_COLOR = 0xE6101418.toInt()
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    }
}
