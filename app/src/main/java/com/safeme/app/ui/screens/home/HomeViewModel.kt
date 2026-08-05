package com.safeme.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.blockedTodayFlow
import com.safeme.app.data.blockingEnabled
import com.safeme.app.data.setBlockingEnabled
import com.safeme.app.ui.util.isAccessibilityEnabled
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val masterProtection: Boolean = true,
    val a11yEnabled: Boolean = false,
    val greeting: String = "",
    val dateLine: String = "",
    val blockedToday: String = "0",
    val focusTime: String = "2h 05m",
    val scheduleCount: String = "4",
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application

    private val _uiState = MutableStateFlow(
        HomeUiState(greeting = currentGreeting(), dateLine = currentDateLine())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        // Master protection reflects the real persisted blocking state (shared
        // with the Block screen), so toggling it here actually takes effect.
        viewModelScope.launch {
            app.blockingEnabled().collect { enabled ->
                _uiState.update { it.copy(masterProtection = enabled) }
            }
        }
        viewModelScope.launch {
            app.blockedTodayFlow().collect { count ->
                _uiState.update { it.copy(blockedToday = count.toString()) }
            }
        }
    }

    fun toggleMasterProtection() {
        val enabled = !_uiState.value.masterProtection
        _uiState.update { it.copy(masterProtection = enabled) }
        val text = app.getString(
            if (enabled) R.string.home_toast_on else R.string.home_toast_off
        )
        _toasts.tryEmit(text)
        viewModelScope.launch {
            runCatching { app.setBlockingEnabled(enabled) }
        }
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    fun refresh() {
        _uiState.update {
            it.copy(a11yEnabled = isAccessibilityEnabled(app), dateLine = currentDateLine())
        }
    }

    private fun currentGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val res = when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
        return app.getString(res)
    }

    private fun currentDateLine(): String =
        SimpleDateFormat("EEEE, h:mm a", Locale.getDefault()).format(Date())
}

