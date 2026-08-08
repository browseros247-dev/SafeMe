package com.safeme.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safeme.app.data.dnsVpnSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class VpnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        // Read the persisted state and start the service synchronously while
        // onReceive is still executing: the background-start exemption granted
        // to a broadcast receiver only lasts for the duration of onReceive.
        // Deferring startForegroundService to a coroutine can throw
        // ForegroundServiceStartNotAllowedException on Android 12+.
        val settings = runCatching { runBlocking { context.dnsVpnSettings().first() } }
            .getOrNull() ?: return
        if (!settings.enabled) return
        val start = Intent(context, SafeMeVpnService::class.java)
            .setAction(SafeMeVpnService.ACTION_START)
        context.startForegroundService(start)
    }
}
