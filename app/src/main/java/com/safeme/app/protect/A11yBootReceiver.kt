package com.safeme.app.protect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safeme.app.data.a11yProtectionPrefs
import com.safeme.app.data.contentEnginePrefs
import com.safeme.app.data.preventUninstallPrefs
import com.safeme.app.service.SafeMeProtectionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restores the configured Accessibility Service protection after device boot
 * or an app update. Runs the settings writes synchronously inside onReceive
 * (the receiver's background-start window), re-arms the watcher, and starts
 * the foreground keep-alive service while ANY protection feature is on so an
 * OEM battery killer cannot take the process (and the a11y service with it)
 * down silently.
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
        // Start the keep-alive synchronously inside onReceive (the
        // background-start exemption only lasts for the duration of onReceive,
        // mirroring VpnBootReceiver).
        val puEnabled = runCatching {
            runBlocking { context.preventUninstallPrefs().first() }
        }.getOrNull()?.preventUninstallEnabled == true
        val contentEnginesOn = runCatching {
            runBlocking { context.contentEnginePrefs().first() }
        }.getOrNull()?.blockImageVideoSearch == true
        if (state.protectionEnabled || puEnabled || contentEnginesOn) {
            SafeMeProtectionService.start(context)
        }
        if (state.protectionEnabled) {
            A11yProtectionUtils.selfHealAllAsync(context)
            A11yProtectionGuard.getInstance().ensureWatching(context)
        }
    }
}
