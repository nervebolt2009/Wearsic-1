package com.wearsic.app.ui.ambient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide ambient (always-on display) state, reported by MainActivity via
 * AmbientLifecycleObserver callbacks and consumed by the app shell to render a
 * single low-power, monochrome overlay instead of full-color screens.
 */
object AmbientState {
    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    private val _burnInProtectionRequired = MutableStateFlow(false)
    val burnInProtectionRequired: StateFlow<Boolean> = _burnInProtectionRequired.asStateFlow()

    private val _lowBitAmbient = MutableStateFlow(false)
    val lowBitAmbient: StateFlow<Boolean> = _lowBitAmbient.asStateFlow()

    // Incremented on every periodic ambient update (roughly once a minute).
    // The ambient overlay shifts its static content by a few pixels per tick
    // when the system requests burn-in protection, with no timers of its own.
    private val _ambientTick = MutableStateFlow(0)
    val ambientTick: StateFlow<Int> = _ambientTick.asStateFlow()

    fun enterAmbient(burnInProtectionRequired: Boolean, lowBitAmbient: Boolean) {
        _burnInProtectionRequired.value = burnInProtectionRequired
        _lowBitAmbient.value = lowBitAmbient
        _isAmbient.value = true
    }

    fun exitAmbient() {
        _isAmbient.value = false
    }

    /** Called periodically while ambient; drives the burn-in pixel shift. */
    fun updateAmbient() {
        _ambientTick.value += 1
    }
}
