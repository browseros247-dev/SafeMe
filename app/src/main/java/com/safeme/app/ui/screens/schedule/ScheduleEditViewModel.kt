package com.safeme.app.ui.screens.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.AppCatalog
import com.safeme.app.data.InstalledApp
import com.safeme.app.data.ScheduleBlock
import com.safeme.app.data.ScheduleMode
import com.safeme.app.data.addSchedule
import com.safeme.app.data.deleteSchedule
import com.safeme.app.data.newScheduleId
import com.safeme.app.data.schedulePrefs
import com.safeme.app.data.updateSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScheduleEditUiState(
    val loading: Boolean = true,
    val editId: String? = null,
    val name: String = "",
    val days: Set<Int> = setOf(0, 1, 2),
    val startMinute: Int = 21 * 60,
    val endMinute: Int = 23 * 60,
    val mode: ScheduleMode = ScheduleMode.BOTH,
    val selectedApps: Set<String> = emptySet(),
    val installedApps: List<InstalledApp> = emptyList(),
    val appsLoaded: Boolean = false,
)

class ScheduleEditViewModel(
    application: Application,
    private val editId: String?,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScheduleEditUiState(editId = editId))
    val uiState: StateFlow<ScheduleEditUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** Emitted after a successful save/delete so the screen can navigate back. */
    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val schedules = app.schedulePrefs().first().schedules
            val existing = editId?.let { id -> schedules.firstOrNull { it.id == id } }
            _uiState.update {
                it.copy(
                    loading = false,
                    name = existing?.name ?: "",
                    days = (existing?.days ?: listOf(0, 1, 2)).toSet(),
                    startMinute = existing?.startMinute ?: (21 * 60),
                    endMinute = existing?.endMinute ?: (23 * 60),
                    mode = existing?.mode ?: ScheduleMode.BOTH,
                    selectedApps = existing?.appPackages?.toSet() ?: emptySet(),
                )
            }
        }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.Default) { enumerateApps() }
            _uiState.update { it.copy(installedApps = apps, appsLoaded = true) }
        }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun toggleDay(day: Int) {
        _uiState.update {
            val days = if (day in it.days) it.days - day else it.days + day
            it.copy(days = days)
        }
    }

    fun setStartMinute(minute: Int) {
        _uiState.update { it.copy(startMinute = minute) }
    }

    fun setEndMinute(minute: Int) {
        _uiState.update { it.copy(endMinute = minute) }
    }

    fun setMode(mode: ScheduleMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun toggleApp(pkg: String) {
        _uiState.update {
            val apps = if (pkg in it.selectedApps) it.selectedApps - pkg else it.selectedApps + pkg
            it.copy(selectedApps = apps)
        }
    }

    /** Selects every app in the picker (empty search results are a no-op). */
    fun selectAllApps() {
        _uiState.update {
            it.copy(selectedApps = it.selectedApps + it.installedApps.map { app -> app.packageName })
        }
    }

    /** Deselects every app in the picker. */
    fun deselectAllApps() {
        _uiState.update {
            it.copy(selectedApps = it.selectedApps - it.installedApps.map { app -> app.packageName })
        }
    }

    fun applyApps(apps: Set<String>) {
        _uiState.update { it.copy(selectedApps = apps) }
    }

    /** Validates and persists. Returns true when the schedule was saved. */
    fun save(): Boolean {
        val app = getApplication<Application>()
        val state = _uiState.value
        if (state.name.isBlank()) {
            _toasts.tryEmit(app.getString(R.string.sche_toast_name))
            return false
        }
        if (state.days.isEmpty()) {
            _toasts.tryEmit(app.getString(R.string.sche_toast_day))
            return false
        }
        if (state.endMinute <= state.startMinute) {
            _toasts.tryEmit(app.getString(R.string.sche_toast_time))
            return false
        }
        val schedule = ScheduleBlock(
            id = state.editId ?: newScheduleId(),
            name = state.name.trim(),
            days = state.days.toList(),
            startMinute = state.startMinute,
            endMinute = state.endMinute,
            mode = state.mode,
            appPackages = state.selectedApps.toList(),
            enabled = true,
        )
        viewModelScope.launch {
            if (state.editId != null) {
                app.updateSchedule(schedule)
            } else {
                app.addSchedule(schedule)
            }
            _toasts.tryEmit(app.getString(R.string.sche_toast_saved))
            _done.tryEmit(Unit)
        }
        return true
    }

    fun delete() {
        val app = getApplication<Application>()
        val id = _uiState.value.editId ?: return
        viewModelScope.launch {
            app.deleteSchedule(id)
            _toasts.tryEmit(app.getString(R.string.sche_toast_deleted))
            _done.tryEmit(Unit)
        }
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    private fun enumerateApps(): List<InstalledApp> = AppCatalog.load(getApplication())

    class Factory(
        private val app: Application,
        private val editId: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleEditViewModel(app, editId) as T
    }
}
