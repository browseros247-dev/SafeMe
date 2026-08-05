package com.safeme.app.ui.screens.blockscreen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.safeme.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BlockScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val _dwell = MutableStateFlow(5)
    val dwell: StateFlow<Int> = _dwell.asStateFlow()

    private val _message = MutableStateFlow(
        application.getString(R.string.bs_preview_msg_default)
    )
    val message: StateFlow<String> = _message.asStateFlow()

    private val _img = MutableStateFlow("")
    val img: StateFlow<String> = _img.asStateFlow()

    private val _redirect = MutableStateFlow("")
    val redirect: StateFlow<String> = _redirect.asStateFlow()

    private val _whyOn = MutableStateFlow(true)
    val whyOn: StateFlow<Boolean> = _whyOn.asStateFlow()

    fun stepDwell(delta: Int) {
        _dwell.value = (_dwell.value + delta).coerceIn(3, 120)
    }

    fun setMessage(value: String) {
        _message.value = value
    }

    fun setImg(value: String) {
        _img.value = value
    }

    fun setRedirect(value: String) {
        _redirect.value = value
    }

    fun clearRedirect() {
        _redirect.value = ""
    }

    fun toggleWhy() {
        _whyOn.value = !_whyOn.value
    }
}
