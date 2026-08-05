package com.safeme.app.ui.screens.blocking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.R
import com.safeme.app.data.BundledKeywords
import com.safeme.app.data.blockingPrefs
import com.safeme.app.data.setBlockingEnabled
import java.util.Locale
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
                    _uiState.value = BlockingUiState(
                        blocking = state.blockingEnabled,
                        blockedToday = state.blockedToday.toString(),
                        keywords = formatCount(keywordCount),
                        layersActive = "3",
                        manageSub = manageSub,
                    )
                }
            } catch (t: Throwable) {
                _uiState.value = BlockingUiState()
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

    fun showToast(message: String) {
        _toasts.tryEmit(message)
    }

    private fun formatCount(count: Int): String {
        return if (count >= 1000) {
            String.format(Locale.US, "%.0f", (count / 1000.0)) + ",000"
        } else {
            count.toString()
        }
    }
}
