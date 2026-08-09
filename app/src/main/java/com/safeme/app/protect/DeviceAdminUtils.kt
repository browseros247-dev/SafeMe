package com.safeme.app.protect

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeme.app.MainActivity
import com.safeme.app.R

/**
 * Device Admin utilities + receiver for anti-uninstall.
 *
 * ## How Device Admin prevents uninstall
 *
 * When an app is a Device Admin, Android replaces the "Uninstall" button in
 * Settings -> Apps with "Disable". Tapping "Disable" opens the Device Admin
 * deactivation screen first — which the accessibility service detects and
 * blocks while Prevent Uninstall is ON.
 *
 * ## Scoping
 *
 * This is deliberately the minimum-impact Device Admin use on stock Android:
 * an empty `<uses-policies/>` (no admin capabilities), and the receiver only
 * handles notifications (throttled) — no lock, no wipe, no device-owner logic.
 *
 * ## Error handling
 *
 * Every call is wrapped in try/catch (the reference pattern). DevicePolicyManager
 * can throw on rooted devices or OEM ROMs; the safe fallback is false / no-op.
 */
object DeviceAdminUtils {

    /**
     * Receiver registered in the manifest. Prevents uninstall via the
     * "Disable" button. [onDisableRequested] returns an empty CharSequence so
     * Android shows its own default confirmation dialog — a custom message can
     * cause the dialog to be dismissed on some OEM ROMs (MIUI, EMUI).
     */
    class SafeMeDeviceAdminReceiver : DeviceAdminReceiver() {

        override fun onDisabled(context: Context, intent: Intent) {
            try {
                super.onDisabled(context, intent)
                val now = System.currentTimeMillis()
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastNotifMs = prefs.getLong(KEY_LAST_DISABLED_NOTIF_MS, 0L)
                if (now - lastNotifMs >= THROTTLE_MS) {
                    prefs.edit().putLong(KEY_LAST_DISABLED_NOTIF_MS, now).apply()
                    showDisabledNotification(context)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "onDisabled threw", t)
            }
        }

        override fun onDisableRequested(context: Context, intent: Intent): CharSequence = ""
    }

    private fun showDisabledNotification(context: Context) {
        try {
            val appCtx = context.applicationContext
            val manager = appCtx.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    DISABLED_CHANNEL_ID,
                    appCtx.getString(R.string.pu_notif_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                appCtx,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appCtx, DISABLED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(appCtx.getString(R.string.pu_disabled_notif_title))
                .setContentText(appCtx.getString(R.string.pu_disabled_notif_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(NOTIF_ID_DISABLED, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "showDisabledNotification threw", t)
        }
    }

    private const val TAG = "SafeMeDeviceAdmin"
    private const val PREFS_NAME = "safeme_device_admin"
    private const val KEY_LAST_DISABLED_NOTIF_MS = "last_disabled_notif_ms"
    private const val THROTTLE_MS = 5 * 60 * 1000L
    private const val DISABLED_CHANNEL_ID = "safeme_device_admin_disabled"
    private const val NOTIF_ID_DISABLED = 0xDA11

    /** ComponentName for [SafeMeDeviceAdminReceiver] — used by [isActive] and activation. */
    fun getComponentName(context: Context): ComponentName =
        ComponentName(context, SafeMeDeviceAdminReceiver::class.java)

    /** Is our Device Admin currently active? Safe fallback: false on error. */
    fun isActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
            dpm.isAdminActive(getComponentName(context))
        } catch (t: Throwable) {
            Log.w(TAG, "isActive threw", t)
            false
        }
    }

    /**
     * Build the system "Activate device admin app" intent for this app.
     * Launched via an Activity Result launcher on the Anti-Tamper screen;
     * there is no trusted result, so the screen re-checks [isActive] on
     * onResume after the system page closes.
     */
    fun activationIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.pu_admin_explanation)
            )
        }
    }

    /** Remove Device Admin (the in-app "Deactivate" path). Safe no-op when not active. */
    fun removeActive(context: Context) {
        try {
            val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
            val component = getComponentName(context)
            if (dpm.isAdminActive(component)) {
                dpm.removeActiveAdmin(component)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "removeActive threw", t)
        }
    }
}
