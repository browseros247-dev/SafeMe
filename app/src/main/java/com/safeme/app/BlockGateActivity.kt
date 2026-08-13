package com.safeme.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.safeme.app.R
import com.safeme.app.data.ACTIVITY_BLOCK
import com.safeme.app.data.BlockScreenPrefsState
import com.safeme.app.data.addActivity
import com.safeme.app.data.blockScreenPrefs
import com.safeme.app.data.incrementBlockedToday
import com.safeme.app.ui.screens.blockscreen.BlockOverlay
import com.safeme.app.ui.theme.SafeMeApp
import kotlinx.coroutines.launch

/**
 * Full-screen block gate raised by [service.SafeMeAccessibilityService] over an offending
 * app. Reuses the self-contained [BlockOverlay] composable (dwell countdown + ready-gated
 * Close) and honors the persisted Block Screen settings: dwell countdown, custom message,
 * close-gate redirect and the "Why am I seeing this?" toggle. Increments the persisted
 * blocked-today counter for every real block it shows.
 *
 * Robustness:
 *  - Increment happens only on first creation (never re-incremented on recreation/rotation).
 *  - Close resolves to an ACTION_VIEW redirect when one is supplied (a per-gate extra wins
 *    over the persisted redirect), otherwise finishes, returning the user to the app they
 *    were in.
 *  - Settings load asynchronously (defaults render first); failure to persist the counter
 *    is swallowed and never crashes the gate.
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
                    onClose = { closeGate(it) },
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
    onClose: (String) -> Unit,
) {
    val context = LocalContext.current
    // Persisted Block Screen settings (dwell, custom message, redirect, why
    // toggle). The flow renders defaults on the first frame, then the saved
    // values, so the gate never blocks on DataStore.
    val prefs by context.blockScreenPrefs().collectAsState(initial = BlockScreenPrefsState())
    // [L1 fix] locale is captured as a remember key so the PU/schedule reason
    // text updates when the system locale changes mid-display.
    val locale = LocalConfiguration.current.locales[0]
    val defaultMessage = stringResource(R.string.bs_preview_msg_default)
    val puGateMessage = stringResource(R.string.pu_gate_message)
    val scheduleGateMessage = stringResource(R.string.schedule_gate_message)
    // A per-gate redirect extra (functional redirects like SafeSearch) wins
    // over the user's persisted close-gate redirect.
    val effectiveRedirect = remember(redirect, prefs.redirect) {
        redirect.ifEmpty { prefs.redirect }
    }
    // The persisted custom message is the gate message (default fallback), so
    // the live gate matches the Block Screen preview and the prototype.
    val msg = remember(prefs.message, defaultMessage, locale) {
        prefs.message.ifEmpty { defaultMessage }
    }
    // The block context moves to the "Why am I seeing this?" toast.
    val whyReason = remember(pkg, matched, type, puGateMessage, scheduleGateMessage, locale) {
        when (type) {
            "website" ->
                if (matched.isNotEmpty()) "Why: website blocked by SafeMe ($matched)" else "Why: website blocked by SafeMe"
            "title" ->
                if (matched.isNotEmpty()) "Why: Settings page blocked by SafeMe ($matched)" else "Why: Settings page blocked by SafeMe"
            "pu" -> puGateMessage
            "schedule" -> scheduleGateMessage
            else ->
                if (matched.isNotEmpty()) "Why: $matched blocked by SafeMe" else null
        }
    }
    BlockOverlay(
        dwell = prefs.dwell,
        msg = msg,
        whyOn = prefs.whyOn,
        redirect = effectiveRedirect,
        whyReason = whyReason,
        onClose = { onClose(effectiveRedirect) },
    )
}
