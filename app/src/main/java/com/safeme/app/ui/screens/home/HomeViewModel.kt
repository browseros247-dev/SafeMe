package com.safeme.app.ui.screens.home

import android.app.Application
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.A11yProtectionPrefsState
import com.safeme.app.data.ActivityEntry
import com.safeme.app.data.AppLockPrefsState
import com.safeme.app.data.BlockingPrefsState
import com.safeme.app.data.DnsVpnSettings
import com.safeme.app.data.LockType
import com.safeme.app.data.PreventUninstallPrefsState
import com.safeme.app.data.QuickActionType
import com.safeme.app.data.SchedulePrefsState
import com.safeme.app.data.a11yProtectionPrefs
import com.safeme.app.data.activityLog
import com.safeme.app.data.appLockPrefs
import com.safeme.app.data.blockingPrefs
import com.safeme.app.data.dnsVpnSettings
import com.safeme.app.data.preventUninstallPrefs
import com.safeme.app.data.quickActionPrefs
import com.safeme.app.data.schedulePrefs
import com.safeme.app.data.setBlockingEnabled
import com.safeme.app.protect.DeviceAdminUtils
import com.safeme.app.protect.ProtectionLayersEvaluator
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val masterProtection: Boolean = true,
    val a11yEnabled: Boolean = false,
    val greeting: String = "",
    val dateLine: String = "",
    val blockedToday: String = "0",
    /** Honest focus time — no focus tracker exists yet, so it is "0m" until the feature lands. */
    val focusTime: String = "0m",
    val scheduleCount: String = "0",
    val heroProgress: Float = 0f,
    val heroTitleRes: Int = R.string.home_hero_title,
    val heroSubtitle: String = "",
    /** Header pill: green "Protected" vs amber "Paused"/"Attention". */
    val pillGreen: Boolean = true,
    val pillTextRes: Int = R.string.home_protected,
    /** Latest 3 real activity entries, newest first. */
    val feed: List<ActivityEntry> = emptyList(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application

    private val _uiState = MutableStateFlow(
        HomeUiState(greeting = currentGreeting(), dateLine = currentDateLine())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** The user-curated Quick Actions grid (enabled, in display order). */
    private val _quickActions = MutableStateFlow<List<QuickActionType>>(emptyList())
    val quickActions: StateFlow<List<QuickActionType>> = _quickActions.asStateFlow()

    // One-shot device checks (no DataStore flow exists for these) — re-read on
    // resume so the hero reflects the real system state.
    @Volatile private var a11yEnabled = isAccessibilityEnabled(app)
    @Volatile private var deviceAdminActive = DeviceAdminUtils.isActive(app)
    @Volatile private var vpnConsentGranted = VpnService.prepare(app) == null

    // Last prefs snapshots so refresh() can recompute immediately instead of
    // waiting for the next DataStore emission.
    private var lastBlocking = BlockingPrefsState()
    private var lastAppLock = AppLockPrefsState()
    private var lastA11yProt = A11yProtectionPrefsState()
    private var lastPu = PreventUninstallPrefsState()
    private var lastVpn = DnsVpnSettings()
    private var lastSchedules = SchedulePrefsState()

    init {
        val prefs = combine(
            app.blockingPrefs(),
            app.appLockPrefs(),
            app.a11yProtectionPrefs(),
            app.preventUninstallPrefs(),
            app.dnsVpnSettings(),
        ) { blocking, appLock, a11yProt, pu, vpn ->
            PrefsSnapshot(blocking, appLock, a11yProt, pu, vpn)
        }
        viewModelScope.launch {
            combine(prefs, app.schedulePrefs()) { snapshot, schedules ->
                lastBlocking = snapshot.blocking
                lastAppLock = snapshot.appLock
                lastA11yProt = snapshot.a11yProt
                lastPu = snapshot.pu
                lastVpn = snapshot.vpn
                lastSchedules = schedules
                rebuild()
            }.collect { }
        }
        // Real activity feed (blocks, schedules, VPN/a11y changes).
        viewModelScope.launch {
            app.activityLog().collect { entries ->
                _uiState.update { it.copy(feed = entries.take(3)) }
            }
        }
        // User-curated Quick Actions grid.
        viewModelScope.launch {
            app.quickActionPrefs().collect { _quickActions.value = it }
        }
    }

    private fun rebuild() {
        val blocking = lastBlocking
        val layers = ProtectionLayersEvaluator.evaluate(
            masterBlocking = blocking.blockingEnabled,
            accessibilityEnabled = a11yEnabled,
            vpnEnabled = lastVpn.enabled && vpnConsentGranted,
            appLockEnabled = lastAppLock.lockType != LockType.OFF,
            a11yProtectionEnabled = lastA11yProt.protectionEnabled,
            preventUninstallEnabled = lastPu.preventUninstallEnabled,
            hasEnabledSchedule = lastSchedules.schedules.any { it.enabled },
            hasContentRules =
                blocking.blocklistKeywords.isNotEmpty() || blocking.blockedWebsites.isNotEmpty(),
            hasTitleRules = blocking.titleBlockRules.isNotEmpty(),
            deviceAdminActive = deviceAdminActive,
        )
        val paused = !blocking.blockingEnabled
        val enabledSchedules = lastSchedules.schedules.count { it.enabled }
        val titleRes = when {
            paused -> R.string.home_hero_paused
            layers.attention.isEmpty() -> R.string.home_hero_title
            else -> R.string.home_hero_partial
        }
        val subtitle = when {
            paused -> app.getString(R.string.home_hero_resume)
            layers.attention.isEmpty() ->
                app.getString(R.string.home_hero_all_active, layers.total)
            else ->
                app.getString(R.string.home_hero_layers_active, layers.active, layers.total) +
                    " · " +
                    app.getString(R.string.home_hero_attention, layers.attention.size)
        }
        val pillGreen = !paused && layers.attention.isEmpty()
        val pillTextRes = when {
            paused -> R.string.home_paused
            pillGreen -> R.string.home_protected
            else -> R.string.home_attention
        }
        _uiState.update {
            it.copy(
                masterProtection = blocking.blockingEnabled,
                blockedToday = blocking.blockedToday.toString(),
                scheduleCount = enabledSchedules.toString(),
                heroProgress = if (paused) 0f else layers.progress,
                heroTitleRes = titleRes,
                heroSubtitle = subtitle,
                pillGreen = pillGreen,
                pillTextRes = pillTextRes,
            )
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
        a11yEnabled = isAccessibilityEnabled(app)
        deviceAdminActive = DeviceAdminUtils.isActive(app)
        vpnConsentGranted = VpnService.prepare(app) == null
        _uiState.update {
            it.copy(a11yEnabled = a11yEnabled, dateLine = currentDateLine())
        }
        rebuild()
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

/** Bundle of the five independent prefs states consumed by the Home screen. */
private data class PrefsSnapshot(
    val blocking: BlockingPrefsState,
    val appLock: AppLockPrefsState,
    val a11yProt: A11yProtectionPrefsState,
    val pu: PreventUninstallPrefsState,
    val vpn: DnsVpnSettings,
)
