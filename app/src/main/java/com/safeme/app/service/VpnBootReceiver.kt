package com.safeme.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safeme.app.data.dnsVpnSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VpnBootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        scope.launch {
            val settings = context.dnsVpnSettings().first()
            if (!settings.enabled) return@launch
            val start = Intent(context, SafeMeVpnService::class.java)
                .setAction(SafeMeVpnService.ACTION_START)
            context.startForegroundService(start)
        }
    }
}
