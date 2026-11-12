package com.sawn.app.ui.lockdown

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sawn.app.R
import com.sawn.app.tile.SawnLockdownTileService

/**
 * The button that offers the tile to the Quick Settings panel.
 *
 * The system decides whether the tile is actually added — the app can only ask, and
 * the ask is the standard "request add tile" intent.
 */
@Composable
fun AddToHomeButton() {
    val context = LocalContext.current
    OutlinedButton(onClick = { requestAddTile(context) }) {
        Text(stringResource(R.string.panic_add_tile))
    }
}

/** Asks the system to add the panic tile to the Quick Settings panel. */
private fun requestAddTile(context: Context) {
    runCatching {
        context.startActivity(
            // The action and the extra are hidden API in the SDK, but they are the
            // documented way for an app to offer its tile (the constants are not
            // public, so the literals are used here on purpose).
            Intent(ACTION_REQUEST_ADD_TILE)
                .putExtra(
                    EXTRA_COMPONENT_NAME,
                    ComponentName(context, SawnLockdownTileService::class.java),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Asks the Quick Settings panel to add a tile. */
private const val ACTION_REQUEST_ADD_TILE = "android.settings.REQUEST_ADD_TILE"

/** The component of the tile to add. */
private const val EXTRA_COMPONENT_NAME = "android.intent.extra.COMPONENT_NAME"
