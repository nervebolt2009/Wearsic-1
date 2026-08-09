package com.wearsic.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local playback state. The service and app UI run in the same process,
 * so this keeps Media3 state (errors, progress, playback, track transitions)
 * visible even if it happens before the ViewModel starts collecting.
 */
object PlaybackEvents {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _trackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackEnded: SharedFlow<Unit> = _trackEnded.asSharedFlow()

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    /** Report playback position as a 0..1 fraction plus the raw duration. */
    fun reportProgress(positionMs: Long, durationMs: Long) {
        _progress.value = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    fun reportPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun reportTrackEnded() {
        _trackEnded.tryEmit(Unit)
    }
}
