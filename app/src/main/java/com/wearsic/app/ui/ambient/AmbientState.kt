package com.wearsic.app.ui.ambient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide ambient (always-on display) state, reported by MainActivity via
 * AmbientModeSupport callbacks and consumed by the Now Playing screen to switch
 * to its low-power, monochrome rendering.
 */
object AmbientState {
    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    private val _burnInProtectionRequired = MutableStateFlow(false)
    val burnInProtectionRequired: StateFlow<Boolean> = _burnInProtectionRequired.asStateFlow()

    private val _lowBitAmbient = MutableStateFlow(false)
    val lowBitAmbient: StateFlow<Boolean> = _lowBitAmbient.asStateFlow()

    fun enterAmbient(burnInProtectionRequired: Boolean, lowBitAmbient: Boolean) {
        _burnInProtectionRequired.value = burnInProtectionRequired
        _lowBitAmbient.value = lowBitAmbient
        _isAmbient.value = true
    }

    fun exitAmbient() {
        _isAmbient.value = false
    }

    /** Called periodically while ambient; a hook for burn-in shifting if needed. */
    fun updateAmbient() = Unit
}
