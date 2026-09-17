package com.safeme.app.ui.screens.socialblocking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.data.InstalledApp
import com.safeme.app.data.SocialBlockingPrefs
import com.safeme.app.data.SocialBlockingState
import com.safeme.app.data.KEY_SOCIAL_FACEBOOK
import com.safeme.app.data.KEY_SOCIAL_SNAPCHAT
import com.safeme.app.data.KEY_SOCIAL_YOUTUBE
import com.safeme.app.data.applySocialPreset
import com.safeme.app.data.setSocialWholeBlocked
import com.safeme.app.data.socialBlockingPrefs
import com.safeme.app.data.toggleSocialEnabled
import com.safeme.app.data.toggleSocialVertical
import com.safeme.app.data.toggleSocialWholeBlocked
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SocialBlockingUiState(
    val enabled: Boolean = true,
    val wholeBlocked: Set<String> = SocialBlockingState.DEFAULT_WHOLE_BLOCKED,
    val youtube: Boolean = true,
    val facebook: Boolean = true,
    val snapchat: Boolean = true,
    val displayLaunchCount: Int = 5,
    val activeTabs: Int = 3,
    val preset: String = "deep", // deep / balanced / relax / none
) {
    val isDeep: Boolean get() = preset == "deep"
    val isBalanced: Boolean get() = preset == "balanced"
    val isRelax: Boolean get() = preset == "relax"
}

class SocialBlockingViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    private val _uiState = MutableStateFlow(SocialBlockingUiState())
    val uiState: StateFlow<SocialBlockingUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    // For GroupedAppPicker sheet — loaded off main.
    private val _allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val allApps: StateFlow<List<InstalledApp>> = _allApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                app.socialBlockingPrefs().catch { emit(SocialBlockingState()) },
                // placeholder: apps loaded separately below
                MutableStateFlow(Unit),
            ) { state, _ -> state }
                .collect { state ->
                    _uiState.value = SocialBlockingUiState(
                        enabled = state.enabled,
                        wholeBlocked = state.wholeBlocked,
                        youtube = state.youtube,
                        facebook = state.facebook,
                        snapchat = state.snapchat,
                        displayLaunchCount = state.displayLaunchCount,
                        activeTabs = state.activeTabs,
                        preset = resolvePreset(state),
                    )
                }
        }
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val list = com.safeme.app.data.AppCatalog.load(app)
                _allApps.value = list.sortedBy { it.label.lowercase() }
            } catch (_: Throwable) {
                _allApps.value = emptyList()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun refreshApps() = loadApps()

    private fun resolvePreset(s: SocialBlockingState): String {
        if (!s.enabled) return "relax"
        val whole = s.displayLaunchCount
        val tabs = s.activeTabs
        // 3 tabs = youtube+facebook+snapchat
        return when {
            whole >= 4 && tabs == 3 -> "deep"
            whole == 0 && tabs == 3 -> "balanced"
            else -> "none"
        }
    }

    fun toggleMaster() {
        viewModelScope.launch {
            // Atomic flip inside the DataStore transaction — rapid taps can
            // never be swallowed by a stale in-memory read.
            val next = app.toggleSocialEnabled()
            _toasts.emit(if (next) "Social Media Blocking — on" else "Social Media Blocking — off")
        }
    }

    fun applyPreset(which: String) {
        viewModelScope.launch {
            app.applySocialPreset(which)
            val label = when (which) {
                "deep" -> "Deep Work — all apps & tabs"
                "balanced" -> "Balanced — tabs only"
                "relax" -> "Relax — all paused"
                else -> which
            }
            _toasts.emit(label)
        }
    }

    fun toggleLaunch(pkg: String) {
        val cur = _uiState.value
        if (!cur.enabled) {
            viewModelScope.launch { _toasts.emit("Turn on Master first") }
            return
        }
        viewModelScope.launch {
            // Toggle the WHOLE FAMILY atomically: blocking "TikTok" stores every
            // TikTok variant, so the gate enforces on whichever one is installed
            // and the row can never disagree with enforcement.
            val family = SocialBlockingPrefs.familyOf(pkg) ?: setOf(pkg)
            val blocked = app.toggleSocialWholeBlocked(family)
            val name = pkg.substringAfterLast(".").takeIf { it.isNotEmpty() } ?: pkg
            _toasts.emit(if (blocked) "$name blocked" else "$name allowed")
        }
    }

    fun setWholeBlocked(pkgs: Set<String>) {
        viewModelScope.launch {
            app.setSocialWholeBlocked(pkgs)
            _toasts.emit(
                if (pkgs.isEmpty()) "No whole-app blocks"
                else "${SocialBlockingPrefs.displayCount(pkgs)} app${if (SocialBlockingPrefs.displayCount(pkgs) == 1) "" else "s"} blocked (whole-app)"
            )
        }
    }

    fun toggleYoutube() {
        val cur = _uiState.value
        if (!cur.enabled) {
            viewModelScope.launch { _toasts.emit("Turn on Social Media Blocking first") }
            return
        }
        viewModelScope.launch {
            val on = app.toggleSocialVertical(KEY_SOCIAL_YOUTUBE)
            _toasts.emit(if (on) "YouTube Shorts blocked" else "YouTube Shorts allowed")
        }
    }

    fun toggleFacebook() {
        val cur = _uiState.value
        if (!cur.enabled) {
            viewModelScope.launch { _toasts.emit("Turn on Social Media Blocking first") }
            return
        }
        viewModelScope.launch {
            val on = app.toggleSocialVertical(KEY_SOCIAL_FACEBOOK)
            _toasts.emit(if (on) "Facebook Reels blocked" else "Facebook Reels allowed")
        }
    }

    fun toggleSnapchat() {
        val cur = _uiState.value
        if (!cur.enabled) {
            viewModelScope.launch { _toasts.emit("Turn on Social Media Blocking first") }
            return
        }
        viewModelScope.launch {
            val on = app.toggleSocialVertical(KEY_SOCIAL_SNAPCHAT)
            _toasts.emit(if (on) "Snapchat Spotlight blocked" else "Snapchat Spotlight allowed")
        }
    }

    fun showToast(msg: String) {
        viewModelScope.launch { _toasts.emit(msg) }
    }
}
