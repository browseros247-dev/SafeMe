package com.safeme.app.protect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeme.app.MainActivity
import com.safeme.app.R
import com.safeme.app.data.ACTIVITY_A11Y
import com.safeme.app.data.addActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/**
 * A11yProtectionGuard — watches for protected accessibility services being
 * disabled/stopped and re-arms them.
 *
 * Two detection layers:
 *   1. **ContentObserver** on `ENABLED_ACCESSIBILITY_SERVICES` and
 *      `ACCESSIBILITY_ENABLED` — fires the instant the list or master switch
 *      changes; triggers a background self-heal.
 *   2. **30-second polling** (fallback for OEMs that don't deliver the
 *      change notification) — also detects "listed but unbound" services
 *      (OEM battery killers) via the actually-bound service list.
 *
 * Every action is a no-op while the protection toggle is off, and every
 * write additionally requires `WRITE_SECURE_SETTINGS` (see
 * [A11yProtectionUtils]). When restore is impossible (permission missing /
 * OEM blocked write) the guard posts a notification, throttled to once per
 * hour.
 */
class A11yProtectionGuard {

    private val handler = Handler(Looper.getMainLooper())
    private val isWatching = AtomicBoolean(false)
    private val observerRegistered = AtomicBoolean(false)

    @Volatile
    private var context: Context? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkProtectedServices()
            // Keep polling only while protection is on: checkProtectedServices()
            // is a no-op once the toggle is off, so an off-state poll is pure
            // wakeup waste. The ContentObserver still covers re-enable.
            if (isWatching.get() && A11yProtectionStateHolder.protectionEnabled) {
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    private val servicesObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val ctx = context ?: return
            // Serialized on the shared heal executor; self-heal only writes
            // when something actually changed, so our own writes don't loop.
            A11yProtectionUtils.selfHealAllAsync(ctx)
        }
    }

    fun startWatching(context: Context) {
        this.context = context.applicationContext
        registerObserver(context.applicationContext)
        // The poll is the OEM fallback; it only matters while protection is
        // on. The collector sets the holder flag before calling this, so the
        // gate is always in sync with the toggle.
        if (A11yProtectionStateHolder.protectionEnabled &&
            isWatching.compareAndSet(false, true)
        ) {
            handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
        }
    }

    /** Re-arm the watcher (idempotent) — call from every heal entry point. */
    fun ensureWatching(context: Context) {
        this.context = context.applicationContext
        registerObserver(context.applicationContext)
        if (A11yProtectionStateHolder.protectionEnabled &&
            isWatching.compareAndSet(false, true)
        ) {
            handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
        }
    }

    fun stopWatching() {
        if (isWatching.compareAndSet(true, false)) {
            handler.removeCallbacks(checkRunnable)
        }
        unregisterObserver()
    }

    private fun registerObserver(context: Context) {
        if (!observerRegistered.compareAndSet(false, true)) return
        try {
            val cr = context.contentResolver
            cr.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                servicesObserver
            )
            cr.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                false,
                servicesObserver
            )
            Log.i(TAG, "ContentObserver registered (services list + master switch)")
        } catch (t: Throwable) {
            observerRegistered.set(false)
            Log.w(TAG, "observer registration failed", t)
        }
    }

    private fun unregisterObserver() {
        if (!observerRegistered.compareAndSet(true, false)) return
        try {
            context?.contentResolver?.unregisterContentObserver(servicesObserver)
        } catch (_: Throwable) {
        }
    }

    private fun checkProtectedServices() {
        val ctx = context ?: return
        if (!A11yProtectionStateHolder.protectionEnabled) return
        val targets = A11yProtectionUtils.protectedTargets(ctx)
        if (targets.isEmpty()) return
        val anyDisabled = targets.any { !A11yProtectionUtils.isServiceEffectivelyEnabled(ctx, it) }
        val anyListedButUnbound = targets.any {
            A11yProtectionUtils.isServiceEnabled(ctx, it) &&
                !A11yProtectionUtils.isServiceActuallyBound(ctx, it)
        }
        if (anyDisabled || anyListedButUnbound) {
            A11yProtectionUtils.selfHealAllAsync(ctx)
            logProtectedServiceDisabled(ctx)
            // If we can't write, the heal will no-op — tell the user (1/hour).
            if (!A11yProtectionUtils.isWriteSecureSettingsGranted(ctx)) {
                notifyProtectedServiceDisabled(ctx)
            }
        }
    }

    /**
     * Activity-feed event for a protected accessibility service going down.
     * The store dedupes consecutive repeats, so the 30s poll stays quiet.
     */
    private fun logProtectedServiceDisabled(context: Context) {
        try {
            val appCtx = context.applicationContext
            Thread {
                try {
                    runBlocking {
                        appCtx.addActivity(
                            ACTIVITY_A11Y,
                            "Accessibility service disabled",
                            "Protected services need attention"
                        )
                    }
                } catch (_: Throwable) {
                }
            }.start()
        } catch (_: Throwable) {
        }
    }

    private fun notifyProtectedServiceDisabled(context: Context) {
        try {
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_LAST_NOTIF_MS, 0L) < NOTIF_THROTTLE_MS) return

            val manager = appCtx.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    appCtx.getString(R.string.ap_notif_channel),
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
            val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(appCtx.getString(R.string.ap_notif_title))
                .setContentText(appCtx.getString(R.string.ap_notif_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(NOTIF_ID, notification)
            prefs.edit().putLong(KEY_LAST_NOTIF_MS, now).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "notification failed", t)
        }
    }

    companion object {
        private const val TAG = "SafeMeA11yGuard"
        private const val CHECK_INTERVAL_MS = 30_000L
        private const val NOTIF_THROTTLE_MS = 60 * 60 * 1000L
        private const val NOTIF_PREFS = "a11y_protection_notif"
        private const val KEY_LAST_NOTIF_MS = "last_disabled_notif_ms"
        private const val CHANNEL_ID = "safeme_a11y_protection"
        private const val NOTIF_ID = 0xA11

        @Volatile
        private var instance: A11yProtectionGuard? = null

        fun getInstance(): A11yProtectionGuard {
            return instance ?: synchronized(this) {
                instance ?: A11yProtectionGuard().also { instance = it }
            }
        }
    }
}
