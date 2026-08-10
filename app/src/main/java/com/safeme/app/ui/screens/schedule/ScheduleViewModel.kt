package com.safeme.app.ui.screens.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.SCHEDULE_DAY_NAMES
import com.safeme.app.data.ScheduleBlock
import com.safeme.app.data.scheduleDaysLabel
import com.safeme.app.data.scheduleModeLabel
import com.safeme.app.data.schedulePrefs
import com.safeme.app.data.scheduleTimeLabel
import com.safeme.app.data.scheduleWindowLabel
import com.safeme.app.data.setA11yWarningDismissed
import com.safeme.app.data.shouldShowA11yWarning
import com.safeme.app.data.toggleSchedule
import com.safeme.app.protect.ScheduleEvaluator
import com.safeme.app.ui.util.isAccessibilityEnabled
import java.util.Calendar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleCard(
    val id: String,
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
    val heroPill: String = "",
    val nextBoundary: String = "",
    val showA11yWarning: Boolean = false,
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    @Volatile
    private var lastSchedules: List<ScheduleBlock> = emptyList()

    @Volatile
    private var lastA11yWarningDismissed: Boolean = false

    init {
        viewModelScope.launch {
            getApplication<Application>().schedulePrefs().collect { state ->
                lastSchedules = state.schedules
                lastA11yWarningDismissed = state.a11yWarningDismissed
                _uiState.update {
                    buildUiState(state.schedules, state.a11yWarningDismissed)
                }
            }
        }
    }

    private fun buildUiState(
        schedules: List<ScheduleBlock>,
        a11yWarningDismissed: Boolean,
    ): ScheduleUiState {
        val app = getApplication<Application>()
        val enabled = schedules.count { it.enabled }
        val heroCount = app.resources.getQuantityString(
            R.plurals.sch_hero_count,
            enabled,
            enabled,
        )
        val heroPill = app.getString(
            if (enabled > 0) R.string.sch_hero_pill_on else R.string.sch_hero_pill_off
        )
        val enabledSchedules = schedules.filter { it.enabled }
        val nextBoundary = when {
            enabledSchedules.isNotEmpty() -> {
                val nowMillis = System.currentTimeMillis()
                val next = enabledSchedules.mapNotNull { s ->
                    val m = ScheduleEvaluator.nextBoundary(listOf(s), nowMillis)
                    if (m == Long.MAX_VALUE) null else s to m
                }.minByOrNull { it.second }
                if (next == null) {
                    // No computable boundary (e.g. degenerate window); fall back to start time.
                    app.getString(
                        R.string.sch_hero_sub_on,
                        enabledSchedules.first().name,
                        scheduleTimeLabel(enabledSchedules.first().startMinute),
                    )
                } else {
                    app.getString(
                        R.string.sch_hero_sub_on,
                        next.first.name,
                        nextBoundaryLabel(next.second),
                    )
                }
            }
            schedules.isNotEmpty() -> app.getString(R.string.sch_hero_sub_paused)
            else -> app.getString(R.string.sch_hero_sub_empty)
        }
        val cards = schedules.map { s ->
            ScheduleCard(
                id = s.id,
                name = s.name,
                days = scheduleDaysLabel(s.days),
                time = scheduleWindowLabel(s.startMinute, s.endMinute),
                mode = scheduleModeLabel(s.mode),
                apps = if (s.blocksAllApps) {
                    app.getString(R.string.sch_card_apps_all)
                } else {
                    app.resources.getQuantityString(
                        R.plurals.sch_card_apps,
                        s.appPackages.size,
                        s.appPackages.size,
                    )
                },
                next = app.getString(R.string.sch_card_next, scheduleDaysLabel(s.days)),
                enabled = s.enabled,
            )
        }
        return ScheduleUiState(
            cards = cards,
            heroCount = heroCount,
            heroPill = heroPill,
            nextBoundary = nextBoundary,
            showA11yWarning = shouldShowA11yWarning(
                schedules,
                isAccessibilityEnabled(app),
                a11yWarningDismissed,
            ),
        )
    }

    /** Hides the a11y banner until the service is enabled again. */
    fun dismissA11yWarning() {
        viewModelScope.launch {
            getApplication<Application>().setA11yWarningDismissed(true)
        }
    }

    /**
     * Re-reads live service state (called on screen resume). When the
     * accessibility service is now enabled, clears any prior dismissal so a
     * future disable re-arms the banner.
     */
    fun refresh() {
        val app = getApplication<Application>()
        val a11yEnabled = isAccessibilityEnabled(app)
        viewModelScope.launch {
            val dismissed = if (a11yEnabled) {
                if (lastA11yWarningDismissed) app.setA11yWarningDismissed(false)
                false
            } else {
                lastA11yWarningDismissed
            }
            _uiState.update {
                it.copy(
                    showA11yWarning = shouldShowA11yWarning(
                        lastSchedules,
                        a11yEnabled,
                        dismissed,
                    )
                )
            }
        }
    }

    fun toggleSchedule(id: String) {
        val current = _uiState.value.cards.firstOrNull { it.id == id }?.enabled ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            app.toggleSchedule(id, !current)
        }
        _toasts.tryEmit(
            app.getString(if (current) R.string.sch_toast_off else R.string.sch_toast_on)
        )
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    /** "HH:mm" for today's boundary, "Day HH:mm" when it falls on a later day. */
    private fun nextBoundaryLabel(nextMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nextMillis }
        val now = Calendar.getInstance()
        val time = scheduleTimeLabel(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
        val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return time
        val dayIndex = ((cal.get(Calendar.DAY_OF_WEEK) - 1 + 6) % 7)
        return "${SCHEDULE_DAY_NAMES[dayIndex]} $time"
    }
}
