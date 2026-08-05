package com.safeme.app.ui.screens.schedule

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

data class ScheduleCard(
    val id: Int,
    val name: String,
    val days: String,
    val time: String,
    val mode: String,
    val apps: String,
    val next: String,
    val enabled: Boolean = true,
)

data class ScheduleUiState(
    val cards: List<ScheduleCard> = emptyList(),
    val heroCount: String = "",
    val nextBoundary: String = "",
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                heroCount = app.getString(R.string.sch_hero_count),
                nextBoundary = app.getString(R.string.sch_hero_sub),
                cards = listOf(
                    ScheduleCard(
                        id = 1,
                        name = app.getString(R.string.sch_study_name),
                        days = app.getString(R.string.sch_study_days),
                        time = app.getString(R.string.sch_study_time),
                        mode = app.getString(R.string.sch_study_mode),
                        apps = app.getString(R.string.sch_study_apps),
                        next = app.getString(R.string.sch_study_next),
                    ),
                    ScheduleCard(
                        id = 2,
                        name = app.getString(R.string.sch_mornings_name),
                        days = app.getString(R.string.sch_mornings_days),
                        time = app.getString(R.string.sch_mornings_time),
                        mode = app.getString(R.string.sch_mornings_mode),
                        apps = app.getString(R.string.sch_mornings_apps),
                        next = app.getString(R.string.sch_mornings_next),
                    ),
                )
            )
        }
    }

    fun toggleSchedule(id: Int) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.id == id) card.copy(enabled = !card.enabled) else card
                }
            )
        }
        val app = getApplication<Application>()
        val message = app.getString(
            if (_uiState.value.cards.firstOrNull { it.id == id }?.enabled == true) {
                R.string.sch_toast_on
            } else {
                R.string.sch_toast_off
            }
        )
        _toasts.tryEmit(message)
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }
}
