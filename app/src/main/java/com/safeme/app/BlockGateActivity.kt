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
import com.safeme.app.data.ACTIVITY_BLOCK
import com.safeme.app.data.addActivity
import com.safeme.app.data.incrementBlockedToday
import com.safeme.app.ui.screens.blockscreen.BlockOverlay
import com.safeme.app.ui.theme.SafeMeApp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import com.safeme.app.R
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

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val matched = intent.getStringExtra(EXTRA_MATCHED).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val redirect = intent.getStringExtra(EXTRA_REDIRECT).orEmpty()

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                try {
                    incrementBlockedToday()
                    addBlockActivity(pkg, matched, type)
                } catch (t: Throwable) {
                    // Persistence failure must never crash the gate.
                }
            }
        }

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

    private suspend fun addBlockActivity(pkg: String, matched: String, type: String) {
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg.ifBlank { "an app" })
        val title = when (type) {
            "website" -> "Website blocked"
            "title" -> "Settings page blocked"
            "schedule" -> "Blocked $label"
            "pu" -> "Uninstall blocked"
            else -> if (matched.isNotEmpty()) "Keyword blocked" else "Blocked $label"
        }
        val sub = when {
            matched.isNotEmpty() -> matched
            type == "schedule" -> "Launch blocked by schedule"
            type == "pu" -> "Prevent Uninstall is on"
            else -> "Blocked by SafeMe"
        }
        addActivity(ACTIVITY_BLOCK, title, sub)
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
    // [L1 fix] locale is captured as a remember key so the PU gate message
    // updates when the system locale changes mid-display.
    val locale = LocalConfiguration.current.locales[0]
    val puGateMessage = stringResource(R.string.pu_gate_message)
    val scheduleGateMessage = stringResource(R.string.schedule_gate_message)
    val msg = remember(pkg, matched, type, locale) {
        when (type) {
            "website" ->
                if (matched.isNotEmpty()) "Website blocked by SafeMe: $matched" else "This site is blocked by SafeMe"
            "title" ->
                if (matched.isNotEmpty()) "Settings page blocked by SafeMe: $matched" else "Settings page blocked by SafeMe"
            "pu" -> puGateMessage
            "schedule" -> scheduleGateMessage
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
