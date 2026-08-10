package com.safeme.app.protect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safeme.app.data.a11yProtectionPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restores the configured Accessibility Service protection after device boot
 * or an app update. Runs the settings writes synchronously inside onReceive
 * (the receiver's background-start window) and re-arms the watcher.
 */
class A11yBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val state = runCatching {
            runBlocking { context.a11yProtectionPrefs().first() }
        }.getOrNull() ?: return
        A11yProtectionStateHolder.protectionEnabled = state.protectionEnabled
        A11yProtectionStateHolder.protectedComponents = state.protectedComponents
        if (state.protectionEnabled) {
            A11yProtectionUtils.selfHealAllAsync(context)
            A11yProtectionGuard.getInstance().ensureWatching(context)
        }
    }
}
