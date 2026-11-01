package com.warden.blocker.ui.intercept

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.warden.blocker.block.LimitReason
import com.warden.blocker.data.BlockedItem
import com.warden.blocker.ui.theme.WardenTheme
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shown over a blocked app. Renders one of three experiences depending on the decision:
 * a hard block, a limit-reached notice, or the mindful pause that can grant timed access.
 */
class InterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anti-tapjacking: a malicious overlay must not be able to trick taps on "Continue"
        // (which grants access) or dismiss the block.
        window.decorView.filterTouchesWhenObscured = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_BLOCK
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        val reason = intent.getStringExtra(EXTRA_REASON)?.let { runCatching { LimitReason.valueOf(it) }.getOrNull() }
        val escalations = intent.getIntExtra(EXTRA_ESCALATIONS, 0)
        val adultHost = intent.getStringExtra(EXTRA_HOST)
        val contentLabel = intent.getStringExtra(EXTRA_FEATURE_LABEL)

        setContent {
            WardenTheme {
                var item by remember { mutableStateOf<BlockedItem?>(null) }
                LaunchedEffect(itemId) { item = wardenContainer.repository.item(itemId) }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = item
                    when {
                        kind == KIND_ADULT -> AdultBlockScreen(onHome = ::goHome)
                        kind == KIND_SOCIAL -> SocialBlockScreen(platformLabel = contentLabel, onHome = ::goHome)
                        kind == KIND_FEATURE -> FeatureBlockScreen(featureLabel = contentLabel, onHome = ::goHome)
                        kind == KIND_PAUSE && current != null -> PauseScreen(
                            item = current,
                            escalations = escalations,
                            onContinue = { minutes -> grantAndEnter(current, minutes) },
                            onGiveUp = { recordDefeatAndGoHome() },
                        )
                        kind == KIND_LIMIT && current != null -> LimitScreen(current, reason, ::goHome)
                        else -> HardBlockScreen(current, ::goHome)
                    }
                }
            }
        }
    }

    private fun grantAndEnter(item: BlockedItem, minutes: Int) {
        lifecycleScope.launch {
            wardenContainer.repository.grantAccess(item, minutes)
            finish() // return to the app that is waiting behind us; the grant lets it through
        }
    }

    /** Exiting an active pause counts as a defeat — next attempt waits longer. */
    private fun recordDefeatAndGoHome() {
        lifecycleScope.launch {
            wardenContainer.settings.recordPauseDefeat(wardenContainer.settings.trustedNow())
            goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    @Deprecated("Back should return home, never silently enter the app")
    override fun onBackPressed() = goHome()

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_REASON = "reason"
        const val EXTRA_ESCALATIONS = "escalations"
        const val EXTRA_FEATURE_LABEL = "feature_label"
        const val EXTRA_HOST = "host"
        const val KIND_PAUSE = "pause"
        const val KIND_BLOCK = "block"
        const val KIND_LIMIT = "limit"
        const val KIND_FEATURE = "feature"
        const val KIND_ADULT = "adult"
        const val KIND_SOCIAL = "social"
    }
}

@Composable
private fun SocialBlockScreen(platformLabel: String?, onHome: () -> Unit) {
    val platform = platformLabel ?: "this app"
    CenteredHoldToLeave(
        title = "Adult content blocked",
        body = "Warden detected an adult or NSFW screen on $platform. " +
            "The rest of the app remains available; this screen is not. Hold the button to head somewhere else.",
        onHome = onHome,
    )
}

@Composable
private fun FeatureBlockScreen(featureLabel: String?, onHome: () -> Unit) {
    CenteredHoldToLeave(
        title = "${featureLabel ?: "That feed"} is off-limits",
        body = "Warden is keeping you out of ${featureLabel ?: "this feed"}. The rest of the app still works — just not the endless scroll.",
        onHome = onHome,
    )
}

@Composable
private fun AdultBlockScreen(onHome: () -> Unit) {
    CenteredHoldToLeave(
        title = "Blocked — adult content",
        body = "Warden's Porn Guard keeps this site out of reach, in every browser. " +
            "The urge passes; the page stays blocked. Hold the button to head somewhere else.",
        onHome = onHome,
    )
}

/**
 * Hold-to-leave (Flint-style): the exit button needs a deliberate 1.2s press — no leaving
 * by accident, no muscle-memory taps. The ring around the button fills as you hold.
 */
@Composable
private fun CenteredHoldToLeave(title: String, body: String, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🛡", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        HoldToLeaveButton(onDone = onHome)
    }
}

private const val HOLD_MS = 1200

/**
 * Deliberate exit (Flint-style hold-to-leave): the user must hold the center button for
 * ~1.2 s while a ring fills; releasing early cancels. Uses detectTapGestures' onPress
 * suspension (clean down→release lifecycle) instead of raw pointer plumbing.
 */
@Composable
private fun HoldToLeaveButton(onDone: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.size(72.dp),
            color = MaterialTheme.colorScheme.secondary,
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(60.dp)
                .pointerInput(Unit) {
                    // awaitEachGesture: explicit down→up window; null from
                    // waitForUpOrCancellation means cancelled — only a real, sustained
                    // release past HOLD_MS counts as a deliberate exit.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val start = System.currentTimeMillis()
                        val up = waitForUpOrCancellation()
                        val held = System.currentTimeMillis() - start
                        if (up != null && held >= HOLD_MS) onDone()
                    }
                },
        ) {}
        Text("Hold", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun PauseScreen(item: BlockedItem, escalations: Int, onContinue: (Int) -> Unit, onGiveUp: () -> Unit) {
    // Escalating friction: each same-day exit from a pause makes the next wait longer.
    val extraWait = escalations * item.pauseSeconds.coerceAtLeast(5)
    var remaining by remember { mutableStateOf((item.pauseSeconds + extraWait).coerceAtLeast(1)) }
    LaunchedEffect(Unit) {
        while (remaining > 0) { delay(1000); remaining-- }
    }
    val breathing by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.72f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(220.dp).scale(breathing),
            ) {}
            Text(
                if (remaining > 0) "Breathe\n${remaining}s" else "Ready?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            item.customPrompt?.takeIf { it.isNotBlank() } ?: "Why are you opening ${item.label}?",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        if (remaining > 0) {
            if (escalations > 0) {
                Text(
                    "You've stepped away ${escalations}× today — the wait grows each time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Take a moment. If you still want in, you can continue in a second.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        } else {
            Text("Continue for…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 5, 15).forEach { min ->
                    OutlinedButton(onClick = { onContinue(min) }) { Text("$min min") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HoldToLeaveButton(onDone = onGiveUp)
    }
}

@Composable
private fun HardBlockScreen(item: BlockedItem?, onHome: () -> Unit) {
    CenteredHoldToLeave(
        title = "Blocked by Warden",
        body = item?.let { "You asked Warden to keep you off ${it.label}. The urge passes — you've got this." }
            ?: "This is blocked right now.",
        onHome = onHome,
    )
}

@Composable
private fun LimitScreen(item: BlockedItem, reason: LimitReason?, onHome: () -> Unit) {
    val body = when (reason) {
        LimitReason.OPENS -> "You've used up today's opens for ${item.label}. It resets tomorrow."
        LimitReason.TIME -> "You've hit today's time limit for ${item.label}. It resets tomorrow."
        LimitReason.COOLDOWN -> "You just used ${item.label}. Give it a little while before the next visit."
        null -> "${item.label} is limited right now."
    }
    CenteredHoldToLeave(title = "Limit reached", body = body, onHome = onHome)
}
