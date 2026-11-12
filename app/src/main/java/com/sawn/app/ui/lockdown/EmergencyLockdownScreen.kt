package com.sawn.app.ui.lockdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.domain.lockdown.EmergencyLockdown
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import android.os.SystemClock

/**
 * The emergency lockdown screen.
 *
 * Structure only — a column, a text, and the buttons the feature actually needs:
 * three durations to start with, and one button to extend a running lock. There is no
 * cancel button, because there is no cancel.
 */
@Composable
fun EmergencyLockdownScreen(vm: EmergencyLockdownViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    // The countdown is refreshed here, once a second: the view model holds no timer of
    // its own, and the deadline it publishes is on the monotonic clock.
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.panic_title))

        if (state.isActive) {
            Text(
                stringResource(
                    R.string.panic_locked_until,
                    DateFormat.getDateTimeInstance().format(Date(state.endsAt)),
                ),
            )
            Text(
                stringResource(
                    R.string.panic_remaining,
                    formatRemaining((state.endsAtElapsed - now).coerceAtLeast(0L)),
                ),
            )
            OutlinedButton(onClick = { vm.extend(EXTEND_MINUTES) }) {
                Text(stringResource(R.string.panic_extend, EXTEND_MINUTES))
            }
        } else {
            Text(stringResource(R.string.panic_idle_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmergencyLockdown.DURATIONS_MINUTES.forEach { minutes ->
                    Button(onClick = { vm.start(minutes) }) {
                        Text(stringResource(R.string.panic_start_minutes, minutes))
                    }
                }
            }
        }

        AddToHomeButton()
    }
}

/** The remaining time, as minutes and seconds. */
private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

/** How much a single extension adds. */
private const val EXTEND_MINUTES = 15
