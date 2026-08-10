package com.safeme.app.ui.screens.applock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.AutoLockDelay
import com.safeme.app.data.LockType
import com.safeme.app.data.appLockPrefs
import com.safeme.app.data.setAppLockAutoLock
import com.safeme.app.data.setAppLockBiometric
import com.safeme.app.data.setAppLockForgotDisabled
import com.safeme.app.protect.AppLockBiometrics
import com.safeme.app.protect.AppLockManager
import com.safeme.app.protect.AppLockStateHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockUiState(
    val lockType: LockType = LockType.OFF,
    val enabled: Boolean = false,
    val autoLock: AutoLockDelay = AutoLockDelay.IMMEDIATELY,
    val biometricEnabled: Boolean = false,
    val forgotPasswordDisabled: Boolean = false,
    val biometricAvailable: Boolean = false,
)

/** State for the App Lock screen (hero, settings, setup wizard, disable). */
class AppLockViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                app.appLockPrefs().collect { state ->
                    AppLockStateHolder.update(state)
                    _uiState.update {
                        it.copy(
                            lockType = state.lockType,
                            enabled = state.lockType != LockType.OFF,
                            autoLock = state.autoLock,
                            biometricEnabled = state.biometricEnabled,
                            forgotPasswordDisabled = state.forgotPasswordDisabled,
                            biometricAvailable = AppLockBiometrics.isAvailable(app),
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun setAutoLock(delay: AutoLockDelay) {
        viewModelScope.launch {
            runCatching { app.setAppLockAutoLock(delay) }
            AppLockStateHolder.autoLock = delay
            _uiState.update { it.copy(autoLock = delay) }
            _toasts.tryEmit(app.getString(R.string.al_toast_auto, autoLockLabel(delay)))
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !AppLockBiometrics.isAvailable(app)) {
            _toasts.tryEmit(app.getString(R.string.al_toast_bio_unavailable))
            return
        }
        viewModelScope.launch {
            runCatching { app.setAppLockBiometric(enabled) }
            AppLockStateHolder.biometricEnabled = enabled
            _uiState.update { it.copy(biometricEnabled = enabled) }
        }
    }

    fun setForgotDisabled(disabled: Boolean) {
        viewModelScope.launch {
            runCatching { app.setAppLockForgotDisabled(disabled) }
            AppLockStateHolder.forgotPasswordDisabled = disabled
            _uiState.update { it.copy(forgotPasswordDisabled = disabled) }
        }
    }

    /** Save a new/updated lock from the setup wizard. */
    fun saveLock(type: LockType, input: String) {
        viewModelScope.launch {
            runCatching { AppLockManager.setLock(app, type, input) }
                .onFailure { _toasts.tryEmit(app.getString(R.string.kw_toast_error)) }
                .onSuccess {
                    refresh()
                    _toasts.tryEmit(app.getString(R.string.al_toast_on))
                }
        }
    }

    /** Turn App Lock off (also clears biometric + forgot-password flags). */
    fun disableLock() {
        viewModelScope.launch {
            runCatching { AppLockManager.disableLock(app) }
                .onFailure { _toasts.tryEmit(app.getString(R.string.kw_toast_error)) }
                .onSuccess {
                    refresh()
                    _toasts.tryEmit(app.getString(R.string.al_toast_disabled))
                }
        }
    }

    fun lockNow() {
        AppLockGateController.lockNow()
    }

    /** Surface a message from a child sheet (e.g. wizard mismatch). */
    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    private suspend fun refresh() {
        val state = runCatching { app.appLockPrefs().first() }.getOrNull() ?: return
        AppLockStateHolder.update(state)
        _uiState.update {
            it.copy(
                lockType = state.lockType,
                enabled = state.lockType != LockType.OFF,
                autoLock = state.autoLock,
                biometricEnabled = state.biometricEnabled,
                forgotPasswordDisabled = state.forgotPasswordDisabled,
                biometricAvailable = AppLockBiometrics.isAvailable(app),
            )
        }
    }

    private fun autoLockLabel(delay: AutoLockDelay): String = when (delay) {
        AutoLockDelay.IMMEDIATELY -> app.getString(R.string.al_auto_immediately)
        AutoLockDelay.AFTER_15S -> app.getString(R.string.al_auto_15s)
        AutoLockDelay.AFTER_30S -> app.getString(R.string.al_auto_30s)
        AutoLockDelay.AFTER_1M -> app.getString(R.string.al_auto_1m)
        AutoLockDelay.AFTER_5M -> app.getString(R.string.al_auto_5m)
        AutoLockDelay.OFF -> app.getString(R.string.al_auto_off)
    }
}
