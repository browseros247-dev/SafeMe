package com.safeme.app.ui.screens.antitamper

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.preventUninstallPrefs
import com.safeme.app.data.setPreventUninstallEnabled
import com.safeme.app.protect.DeviceAdminUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AntiTamperUiState(
    val enabled: Boolean = false,
    val adminActive: Boolean = false,
)

/**
 * Anti-Tamper screen state. The [enabled] flag (the PU DataStore switch) drives
 * the accessibility page guards; [adminActive] reflects the Device Admin state
 * that makes Uninstall become Disable. The screen refreshes [adminActive] after
 * the system activation page closes.
 */
class AntiTamperViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

    private val _uiState = MutableStateFlow(AntiTamperUiState())
    val uiState: StateFlow<AntiTamperUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                app.preventUninstallPrefs().collect { state ->
                    _uiState.value = _uiState.value.copy(
                        enabled = state.preventUninstallEnabled,
                        adminActive = DeviceAdminUtils.isActive(app),
                    )
                }
            } catch (t: Throwable) {
                _uiState.value = AntiTamperUiState()
            }
        }
    }

    /**
     * Re-check Device Admin (called when the system activation page closes).
     * The PU DataStore flag always tracks the actual admin state, so the
     * accessibility guards can never drift from reality after a crash between
     * "admin became active" and "flag persisted".
     */
    fun refreshAdminActive() {
        // [L2 fix] Capture old value before the write to avoid a fragile
        // read-after-write pattern that would break if a suspend is added.
        val oldEnabled = _uiState.value.enabled
        val adminActive = DeviceAdminUtils.isActive(app)
        _uiState.value = _uiState.value.copy(adminActive = adminActive)
        if (adminActive != oldEnabled) {
            viewModelScope.launch {
                try {
                    app.setPreventUninstallEnabled(adminActive)
                    _uiState.value = _uiState.value.copy(enabled = adminActive)
                    if (adminActive) {
                        // Activation was confirmed on the system page — confirm it here.
                        _toasts.tryEmit(app.getString(R.string.at_toast_activated))
                    }
                } catch (t: Throwable) {
                    _toasts.tryEmit(app.getString(R.string.kw_toast_error))
                }
            }
        }
    }

    /** User tapped Activate: toast + the screen launches the system admin page. */
    fun activate() {
        _toasts.tryEmit(app.getString(R.string.at_toast_activating))
    }

    /** User tapped Deactivate: revoke Device Admin via the in-app path. */
    fun deactivate() {
        viewModelScope.launch {
            try {
                // Turn the PU guards off BEFORE revoking Device Admin: any
                // system/OEM confirmation dialog that appears must never be
                // covered by our own gate while the flag is still propagating.
                app.setPreventUninstallEnabled(false)
                DeviceAdminUtils.removeActive(app)
                _uiState.value = _uiState.value.copy(adminActive = false, enabled = false)
                _toasts.tryEmit(app.getString(R.string.at_toast_deactivated))
            } catch (t: Throwable) {
                _toasts.tryEmit(app.getString(R.string.kw_toast_error))
            }
        }
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }
}
