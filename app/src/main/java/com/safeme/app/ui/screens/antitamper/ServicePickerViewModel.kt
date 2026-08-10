package com.safeme.app.ui.screens.antitamper

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeme.app.data.A11yProtectionPrefsState
import com.safeme.app.data.a11yProtectionPrefs
import com.safeme.app.data.addProtectedA11yComponent
import com.safeme.app.data.removeProtectedA11yComponent
import com.safeme.app.protect.A11yProtectionStateHolder
import com.safeme.app.protect.A11yProtectionUtils
import com.safeme.app.protect.ProtectedServiceEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ServicePickerUiState(
    val query: String = "",
    val protected: Set<String> = emptySet(),
    val services: List<ProtectedServiceEntry> = emptyList(),
)

/**
 * Picker state for the "Protect another app's Accessibility Service" screen:
 * every installed third-party accessibility service, searchable, with the
 * current protection selection. SafeMe's own service is always protected and
 * lives on the parent screen, so it is excluded here.
 */
class ServicePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<Application>()

    private val _uiState = MutableStateFlow(ServicePickerUiState())
    val uiState: StateFlow<ServicePickerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                app.a11yProtectionPrefs().collect { state ->
                    A11yProtectionStateHolder.protectedComponents = state.protectedComponents
                    refresh(state)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun refresh(state: A11yProtectionPrefsState) {
        // SafeMe's own service is always protected and shown on the parent
        // screen; this list is for *other* apps' services only.
        val entries = A11yProtectionUtils.listAllAccessibilityServices(app)
            .filterNot { it.isOurs }
        val current = _uiState.value
        _uiState.value = current.copy(
            protected = state.protectedComponents,
            services = entries,
        )
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    /**
     * Include ([protect] == true) or exclude a third-party service from the
     * protection mechanism. SafeMe's own service is never listed here.
     */
    fun setProtected(flat: String, protect: Boolean) {
        viewModelScope.launch {
            if (protect) {
                runCatching { app.addProtectedA11yComponent(flat) }
                A11yProtectionStateHolder.protectedComponents =
                    A11yProtectionStateHolder.protectedComponents + flat
                A11yProtectionUtils.selfHealAllAsync(app)
            } else {
                runCatching { app.removeProtectedA11yComponent(flat) }
                A11yProtectionStateHolder.protectedComponents =
                    A11yProtectionStateHolder.protectedComponents - flat
            }
            val state = runCatching { app.a11yProtectionPrefs().first() }
                .getOrDefault(A11yProtectionPrefsState())
            refresh(state)
        }
    }
}
