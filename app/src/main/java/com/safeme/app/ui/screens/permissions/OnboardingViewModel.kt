package com.safeme.app.ui.screens.permissions

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OnboardingViewModel : ViewModel() {

    private val _granted = MutableStateFlow<Set<String>>(emptySet())
    val granted: StateFlow<Set<String>> = _granted.asStateFlow()

    fun markGranted(perm: String) {
        _granted.update { it + perm }
    }

    fun isGranted(perm: String): Boolean = perm in _granted.value
}
