package com.safeme.app

import android.app.Application
import com.safeme.app.data.ThemePref
import com.safeme.app.data.a11yProtectionPrefs
import com.safeme.app.data.appLockPrefs
import com.safeme.app.data.schedulePrefs
import com.safeme.app.data.themePref
import com.safeme.app.protect.A11yProtectionGuard
import com.safeme.app.protect.A11yProtectionStateHolder
import com.safeme.app.protect.A11yProtectionUtils
import com.safeme.app.protect.AppLockStateHolder
import com.safeme.app.protect.ScheduleEngine
import com.safeme.app.ui.screens.applock.AppLockGateController
import com.safeme.app.ui.theme.ThemePrefHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Application entry point. Feeds the cached protection state from DataStore
 * and starts/stops the Accessibility Service protection guard whenever the
 * master toggle flips.
 */
class SafeMeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while at least one schedule is enabled — gates the safety ticker. */
    @Volatile
    private var hasEnabledSchedules = false

    override fun onCreate() {
        super.onCreate()
        val app = this
        // Read the theme pref synchronously before the first frame so the
        // initial composition uses the stored theme (no light/dark flash),
        // then keep the holder fed for live changes. A DataStore first read
        // is a few ms; runBlocking here is the same "lock before first
        // frame" pattern the App Lock gate already uses.
        ThemePrefHolder.pref = runCatching {
            runBlocking { app.themePref().first() }
        }.getOrDefault(ThemePref.SYSTEM)
        appScope.launch {
            try {
                app.themePref().collect { ThemePrefHolder.pref = it }
            } catch (_: Throwable) {
            }
        }
        appScope.launch {
            try {
                app.a11yProtectionPrefs().collect { state ->
                    A11yProtectionStateHolder.protectionEnabled = state.protectionEnabled
                    A11yProtectionStateHolder.protectedComponents = state.protectedComponents
                    if (state.protectionEnabled) {
                        A11yProtectionUtils.selfHealAllAsync(app)
                        A11yProtectionGuard.getInstance().ensureWatching(app)
                    } else {
                        A11yProtectionGuard.getInstance().stopWatching()
                    }
                }
            } catch (_: Throwable) {
            }
        }
        // Feed the App Lock gate: cold start locks before the first frame when
        // a lock is configured, and any change re-evaluates the gate.
        appScope.launch {
            try {
                app.appLockPrefs().collect { state ->
                    AppLockStateHolder.update(state)
                    AppLockGateController.onStateLoaded()
                }
            } catch (_: Throwable) {
            }
        }
        // Feed the schedule engine: every persisted change re-applies launch /
        // internet blocking and re-arms the next boundary alarm. A 60s safety
        // ticker bounds drift when boundary alarms are inexact (no exact-alarm
        // permission on API 31+) or missed in doze.
        appScope.launch {
            try {
                app.schedulePrefs().collect { state ->
                    hasEnabledSchedules = state.schedules.any { it.enabled }
                    ScheduleEngine.apply(app, state.schedules)
                }
            } catch (_: Throwable) {
            }
        }
        appScope.launch {
            try {
                while (true) {
                    delay(SAFETY_TICKER_MS)
                    // Tick only while a schedule is enabled. With none, there
                    // is nothing that can drift, and a reevaluate would just
                    // re-read DataStore from disk every minute for no effect.
                    if (hasEnabledSchedules) {
                        ScheduleEngine.reevaluate(app)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private companion object {
        const val SAFETY_TICKER_MS = 60_000L
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}
