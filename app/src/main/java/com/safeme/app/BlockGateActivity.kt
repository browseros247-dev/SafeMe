package com.safeme.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.safeme.app.data.incrementBlockedToday
import com.safeme.app.ui.screens.blockscreen.BlockOverlay
import com.safeme.app.ui.theme.SafeMeApp
import kotlinx.coroutines.launch

/**
 * Full-screen block gate raised by [service.SafeMeAccessibilityService] over an offending
 * app. Reuses the self-contained [BlockOverlay] composable (dwell countdown + ready-gated
 * Close). Increments the persisted blocked-today counter for every real block it shows.
 *
 * Robustness:
 *  - Increment happens only on first creation (never re-incremented on recreation/rotation).
 *  - Close resolves to an ACTION_VIEW redirect when one is supplied, otherwise finishes,
 *    returning the user to the app they were in.
 *  - Failure to persist the counter is swallowed and never crashes the gate.
 */
class BlockGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                try {
                    incrementBlockedToday()
                } catch (t: Throwable) {
                    // Persistence failure must never crash the gate.
                }
            }
        }

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val matched = intent.getStringExtra(EXTRA_MATCHED).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val redirect = intent.getStringExtra(EXTRA_REDIRECT).orEmpty()

        setContent {
            SafeMeApp {
                BlockGate(
                    pkg = pkg,
                    matched = matched,
                    type = type,
                    redirect = redirect,
                    onClose = { closeGate(redirect) },
                )
            }
        }
    }

    private fun closeGate(redirect: String) {
        val trimmed = redirect.trim()
        if (trimmed.isNotEmpty()) {
            try {
                val uri = Uri.parse(trimmed)
                val target = if (uri.scheme == null) Uri.parse("https://$trimmed") else uri
                startActivity(
                    Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t: Throwable) {
                // Invalid URI: fall through and just close.
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "safeme.gate.package"
        const val EXTRA_MATCHED = "safeme.gate.matched"
        const val EXTRA_TYPE = "safeme.gate.type"
        const val EXTRA_REDIRECT = "safeme.gate.redirect"
    }
}

@Composable
private fun BlockGate(
    pkg: String,
    matched: String,
    type: String,
    redirect: String,
    onClose: () -> Unit,
) {
    var whyOn by remember { mutableStateOf(true) }
    val msg = remember(pkg, matched, type) {
        when (type) {
            "website" ->
                if (matched.isNotEmpty()) "Website blocked by SafeMe: $matched" else "This site is blocked by SafeMe"
            "title" ->
                if (matched.isNotEmpty()) "Settings page blocked by SafeMe: $matched" else "Settings page blocked by SafeMe"
            else ->
                if (matched.isNotEmpty()) "Keyword blocked by SafeMe: $matched" else "Blocked by SafeMe"
        }
    }
    BlockOverlay(
        dwell = GATE_DWELL,
        msg = msg,
        whyOn = whyOn,
        redirect = redirect,
        onClose = onClose,
    )
}

private const val GATE_DWELL = 5
