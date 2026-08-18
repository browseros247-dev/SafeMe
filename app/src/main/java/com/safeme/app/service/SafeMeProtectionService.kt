package com.safeme.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeme.app.MainActivity
import com.safeme.app.R
import com.safeme.app.protect.A11yProtectionGuard
import com.safeme.app.protect.A11yProtectionStateHolder
import com.safeme.app.protect.A11yProtectionUtils

/**
 * Foreground keep-alive service, run while a protection feature is ON
 * (Accessibility Protection and/or Prevent Uninstall).
 *
 * OEM battery killers — notably Vivo/FuntouchOS "i-Manager" — silently kill
 * the process hosting [SafeMeAccessibilityService], after which NO detection
 * happens at all until the user happens to open the app again (the classic
 * "sometimes not blocked" symptom). A foreground service keeps the process at
 * foreground importance so it survives normal background reclaims, and
 * `START_STICKY` makes the system restart this service (and with it the whole
 * process: the accessibility service, the guard's 30 s poll + ContentObserver,
 * and the self-heal) within seconds of any kill.
 *
 * The service itself does almost nothing — it exists to keep the process
 * alive. It re-arms the guard once per start in case the app-level collector
 * hasn't run yet in this process incarnation.
 */
class SafeMeProtectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startAsForeground()
            // Idempotent re-arm: the process may have been restarted by the
            // system (START_STICKY) without the Application collector re-firing
            // in a useful order — make sure the guard's observer + poll are
            // watching and a killed service is restored right away.
            if (A11yProtectionStateHolder.protectionEnabled) {
                A11yProtectionUtils.selfHealAllAsync(this)
                A11yProtectionGuard.getInstance().ensureWatching(this)
            }
        } catch (t: Throwable) {
            // Never take the process down over a notification hiccup.
            Log.w(TAG, "onStartCommand failed", t)
        }
        // START_STICKY: restart this service (and the process) after a kill.
        return START_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ps_notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.ps_notif_title))
            .setContentText(getString(R.string.ps_notif_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "SafeMeProtectSvc"
        private const val CHANNEL_ID = "safeme_protection_keepalive"
        private const val NOTIF_ID = 0xA12

        /** Starts the keep-alive service; a no-op (with logging) if not allowed. */
        fun start(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, SafeMeProtectionService::class.java)
                )
            } catch (t: Throwable) {
                Log.w(TAG, "start failed", t)
            }
        }

        /** Stops the keep-alive service; a no-op when it isn't running. */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SafeMeProtectionService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "stop failed", t)
            }
        }
    }
}
