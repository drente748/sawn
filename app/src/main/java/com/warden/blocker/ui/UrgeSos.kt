package com.warden.blocker.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.warden.blocker.R
import kotlinx.coroutines.delay

/** مرحلة التنفّس 4 – 7 – 8. أرقامٌ قصيرة يكفي أن تتبعها العين. */
private enum class BreathPhase(val seconds: Int) {
    INHALE(4), HOLD(7), EXHALE(8);

    fun next(): BreathPhase = when (this) {
        INHALE -> HOLD
        HOLD -> EXHALE
        EXHALE -> INHALE
    }
}

private const val SOS_TOTAL_SECONDS = 60

/**
 * «درع الرغبة»: دقيقة تنفّسٍ موجّهة تُفتح عند ذروة الرغبة.
 *
 * لا شيء يُخزَّن ولا يُرسَل؛ الشاشة كلها في الذاكرة وتُغلق مع الحوار. الهدف هو كسر
 * حلقة «الفتح التلقائي» بدقيقةٍ واحدةٍ محسوبة، لا منع المستخدم من أي شيء.
 */
@Composable
fun UrgeSosDialog(onDismiss: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(SOS_TOTAL_SECONDS) }
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }
    var phaseLeft by remember { mutableIntStateOf(BreathPhase.INHALE.seconds) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(round) {
        remaining = SOS_TOTAL_SECONDS
        phase = BreathPhase.INHALE
        phaseLeft = BreathPhase.INHALE.seconds
        finished = false
        while (remaining > 0) {
            delay(1_000L)
            remaining -= 1
            phaseLeft -= 1
            if (phaseLeft <= 0) {
                phase = phase.next()
                phaseLeft = phase.seconds
            }
        }
        finished = true
    }

    val target = when {
        finished -> 1f
        phase == BreathPhase.EXHALE -> 0.78f
        else -> 1.16f
    }
    val breath by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (finished) 600 else phase.seconds * 1000,
            easing = FastOutSlowInEasing,
        ),
        label = "sosBreath",
    )

    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val background = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(primaryContainer, background)),
                ),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!finished) {
                    Text(
                        stringResource(R.string.sos_intro),
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(
                                color = primary.copy(alpha = 0.10f),
                                radius = size.minDimension / 2f,
                            )
                        }
                        Canvas(Modifier.fillMaxSize().scale(breath)) {
                            drawCircle(
                                color = primary.copy(alpha = 0.20f),
                                radius = size.minDimension / 2f,
                            )
                            drawCircle(
                                color = primary,
                                radius = size.minDimension / 2f - 8.dp.toPx(),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when (phase) {
                                    BreathPhase.INHALE -> stringResource(R.string.sos_inhale)
                                    BreathPhase.HOLD -> stringResource(R.string.sos_hold)
                                    BreathPhase.EXHALE -> stringResource(R.string.sos_exhale)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.sos_counter, remaining),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.sos_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.sos_close))
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = primary,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        stringResource(R.string.sos_done_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sos_done_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { round += 1 }) {
                            Text(stringResource(R.string.sos_again))
                        }
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.sos_close))
                        }
                    }
                }
            }
        }
    }
}
