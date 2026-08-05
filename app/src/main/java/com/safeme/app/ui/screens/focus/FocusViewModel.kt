package com.safeme.app.ui.screens.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.safeme.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FocusPreset(
    val minutes: Int,
)

data class FocusSession(
    val id: Int,
    val name: String,
    val sub: String,
    val enabled: Boolean = true,
)

data class FocusUiState(
    val selectedPreset: Int = 25,
    val sessions: List<FocusSession> = emptyList(),
)

class FocusViewModel(application: Application) : AndroidViewModel(application) {

    val presets = listOf(
        FocusPreset(minutes = 15),
        FocusPreset(minutes = 25),
        FocusPreset(minutes = 45),
        FocusPreset(minutes = 90),
    )

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                sessions = listOf(
                    FocusSession(
                        id = 1,
                        name = app.getString(R.string.foc_sess_deep_name),
                        sub = app.getString(R.string.foc_sess_deep_sub),
                    ),
                    FocusSession(
                        id = 2,
                        name = app.getString(R.string.foc_sess_evening_name),
                        sub = app.getString(R.string.foc_sess_evening_sub),
                        enabled = false,
                    ),
                )
            )
        }
    }

    fun selectPreset(minutes: Int) {
        _uiState.update { it.copy(selectedPreset = minutes) }
    }

    fun toggleSession(id: Int) {
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    if (session.id == id) session.copy(enabled = !session.enabled) else session
                }
            )
        }
        val app = getApplication<Application>()
        val message = app.getString(
            if (_uiState.value.sessions.firstOrNull { it.id == id }?.enabled == true) {
                R.string.foc_toast_on
            } else {
                R.string.foc_toast_off
            }
        )
        _toasts.tryEmit(message)
    }

    fun startFocus() {
        val app = getApplication<Application>()
        _toasts.tryEmit(app.getString(R.string.foc_toast_start))
    }

    fun customDuration() {
        val app = getApplication<Application>()
        _toasts.tryEmit(app.getString(R.string.foc_toast_custom))
    }

    fun addWidget() {
        val app = getApplication<Application>()
        _toasts.tryEmit(app.getString(R.string.foc_toast_widget))
    }
}
