package com.safeme.app.ui.screens.blocking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.BundledKeywords
import com.safeme.app.data.blockingPrefs
import com.safeme.app.data.contentEnginePrefs
import com.safeme.app.data.maybeRolloverBlockedCounter
import com.safeme.app.data.setBlockImageVideoSearch
import com.safeme.app.data.setBlockingEnabled
import com.safeme.app.data.AppCatalog
import com.safeme.app.data.InstalledApp
import com.safeme.app.data.setBlockingExcludedApps
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockingUiState(
    val blocking: Boolean = true,
    val blockedToday: String = "0",
    val keywords: String = "0",
    val layersActive: String = "3",
    val manageSub: String = "",
    val blockImageVideoSearch: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
    val installedApps: List<InstalledApp> = emptyList(),
    val appsLoaded: Boolean = false,
)

class BlockingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BlockingUiState())
    val uiState: StateFlow<BlockingUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val app = getApplication<Application>()

    init {
        viewModelScope.launch {
            try {
                app.blockingPrefs().collect { state ->
                    val keywordCount = state.blocklistKeywords.size + BundledKeywords.keywords.size
                    val manageSub = app.getString(
                        R.string.blk_manage_sub,
                        state.blocklistKeywords.size.toString(),
                        state.blockedWebsites.size.toString(),
                        state.trustedWebsites.size.toString(),
                    )
                    _uiState.update {
                        it.copy(
                            blocking = state.blockingEnabled,
                            blockedToday = state.blockedToday.toString(),
                            keywords = formatCount(keywordCount),
                            layersActive = "3",
                            manageSub = manageSub,
                            excludedApps = state.excludedApps,
                        )
                    }
                }
            } catch (t: Throwable) {
                _uiState.value = BlockingUiState()
            }
        }
        viewModelScope.launch {
            try {
                app.contentEnginePrefs().collect { state ->
                    _uiState.update {
                        it.copy(blockImageVideoSearch = state.blockImageVideoSearch)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun toggleBlocking() {
        val next = !_uiState.value.blocking
        _uiState.update { it.copy(blocking = next) }
        val message = app.getString(
            if (next) R.string.blk_toast_on else R.string.blk_toast_off
        )
        _toasts.tryEmit(message)
        viewModelScope.launch {
            try {
                app.setBlockingEnabled(next)
            } catch (t: Throwable) {
                // Persistence failure: keep the UI in sync with the last known state.
                _uiState.update { it.copy(blocking = !next) }
            }
        }
    }

    fun toggleImageVideoSearch() {
        val next = !_uiState.value.blockImageVideoSearch
        _uiState.update { it.copy(blockImageVideoSearch = next) }
        val message = app.getString(
            if (next) R.string.blk_toast_on else R.string.blk_toast_off
        )
        _toasts.tryEmit(message)
        viewModelScope.launch {
            try {
                app.setBlockImageVideoSearch(next)
            } catch (t: Throwable) {
                // Persistence failure: keep the UI in sync with the last known state.
                _uiState.update { it.copy(blockImageVideoSearch = !next) }
            }
        }
    }

    fun setExcludedApps(apps: Set<String>) {
        val sanitized = apps.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val prev = _uiState.value.excludedApps
        _uiState.update { it.copy(excludedApps = sanitized) }
        viewModelScope.launch {
            try {
                app.setBlockingExcludedApps(sanitized)
            } catch (t: Throwable) {
                _uiState.update { it.copy(excludedApps = prev) }
            }
        }
    }

    fun ensureAppsLoaded() {
        if (_uiState.value.appsLoaded) return
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.Default) { AppCatalog.load(app) }
                // Only pin as loaded when we actually got apps; empty or
                // broken results retry on the next Manage tap.
                if (apps.isNotEmpty()) {
                    _uiState.update { it.copy(installedApps = apps, appsLoaded = true) }
                }
            } catch (t: Throwable) {
                // Leave appsLoaded=false so the next tap retries.
            }
        }
    }

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    /**
     * B1 freshness: rolls the daily counter on resume so a screen left open
     * across midnight corrects itself. No-op write when already current.
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching { app.maybeRolloverBlockedCounter() }
        }
    }

    private fun formatCount(count: Int): String {
        return if (count >= 1000) {
            String.format(Locale.US, "%.0f", (count / 1000.0)) + ",000"
        } else {
            count.toString()
        }
    }
}
