package com.safeme.app.ui.screens.applock

import com.safeme.app.data.AutoLockDelay
import com.safeme.app.protect.AppLockStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the full-screen unlock gate.
 *
 * Semantics:
 *  - A fresh process with a lock enabled starts locked ([onStateLoaded] /
 *    [onAppForeground]); the user must unlock once per session.
 *  - [unlock] keeps the session unlocked while the app is foregrounded.
 *  - Backgrounding starts the auto-lock timer: "Immediately" locks at once;
 *    a delay locks only if the user returns after the delay; "Off" never
 *    auto-locks (manual "Lock now" only).
 *  - Disabling the lock clears the locked state ([onStateLoaded]).
 *
 * The controller reads [AppLockStateHolder] synchronously, so a cold start
 * can lock before the DataStore emits (the holder is fed by SafeMeApp).
 */
object AppLockGateController {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile
    private var sessionUnlocked = false

    @Volatile
    private var backgroundedAt = 0L

    /** Called whenever the lock prefs load or change (SafeMeApp collector). */
    fun onStateLoaded() {
        if (!AppLockStateHolder.enabled) {
            sessionUnlocked = false
            backgroundedAt = 0L
            _locked.value = false
        } else if (!sessionUnlocked) {
            _locked.value = true
        }
    }

    /** Lifecycle ON_START: apply auto-lock on return from background. */
    fun onAppForeground() {
        if (!AppLockStateHolder.enabled) {
            _locked.value = false
            backgroundedAt = 0L
            return
        }
        if (!sessionUnlocked) {
            _locked.value = true
            backgroundedAt = 0L
            return
        }
        val delay = AppLockStateHolder.autoLock
        val delayMs = delay.millis
        if (delay != AutoLockDelay.OFF && delayMs != null &&
            backgroundedAt > 0L &&
            System.currentTimeMillis() - backgroundedAt >= delayMs
        ) {
            _locked.value = true
        }
        backgroundedAt = 0L
    }

    /** Lifecycle ON_STOP: record the background timestamp / lock immediately. */
    fun onAppBackground() {
        backgroundedAt = System.currentTimeMillis()
        if (AppLockStateHolder.enabled &&
            AppLockStateHolder.autoLock == AutoLockDelay.IMMEDIATELY
        ) {
            _locked.value = true
        }
    }

    /** Manual lock ("Lock now" on the settings screen). */
    fun lockNow() {
        if (AppLockStateHolder.enabled) {
            _locked.value = true
        }
    }

    /** Successful unlock — keeps the session unlocked until backgrounding. */
    fun unlock() {
        sessionUnlocked = true
        _locked.value = false
    }
}
