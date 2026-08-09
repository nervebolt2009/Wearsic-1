package com.wearsic.app.service

import com.wearsic.app.data.model.Track
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local playback state. The service and app UI run in the same process,
 * so this keeps Media3 state (errors, progress, playback, track transitions,
 * queue contents) visible even if it happens before the ViewModel starts
 * collecting, or after the activity has been destroyed.
 *
 * The foreground service is the owner of the playback queue so music keeps
 * advancing even when the app is cleared from recents; the UI mirrors it here.
 */
object PlaybackEvents {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Raw playback position/duration. The UI computes the 0..1 fraction against
    // the track's known duration when the stream itself reports none.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Queue state owned by the foreground service.
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _trackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackEnded: SharedFlow<Unit> = _trackEnded.asSharedFlow()

    private val _cacheCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cacheCleared: SharedFlow<Unit> = _cacheCleared.asSharedFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors.asStateFlow()

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    /** Report raw playback position in milliseconds. */
    fun reportProgress(positionMs: Long, durationMs: Long) {
        _positionMs.value = positionMs.coerceAtLeast(0L)
        _durationMs.value = durationMs.coerceAtLeast(0L)
    }

    fun reportPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun reportTrackEnded() {
        _trackEnded.tryEmit(Unit)
    }

    fun reportCacheCleared() {
        _cacheCleared.tryEmit(Unit)
    }

    fun reportDownloadedIds(ids: Set<String>) {
        _downloadedIds.value = ids
    }

    fun reportDownloadProgress(videoId: String, progress: Float?) {
        val updated = _downloadProgress.value.toMutableMap()
        if (progress == null) updated.remove(videoId) else updated[videoId] = progress.coerceIn(0f, 1f)
        _downloadProgress.value = updated
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = emptyMap()
        _downloadErrors.value = emptyMap()
    }

    fun reportDownloadError(videoId: String, message: String) {
        if (videoId.isBlank()) return
        _downloadErrors.value = _downloadErrors.value.toMutableMap().apply {
            put(videoId, message)
        }
    }

    fun reportCurrentTrack(track: Track?) {
        _currentTrack.value = track
    }

    fun reportQueue(queue: List<Track>, index: Int) {
        _queue.value = queue
        _currentIndex.value = index
    }
}

/**
 * Compute the 0..1 progress fraction, falling back to the track's known
 * metadata duration when the stream itself reports none (proxied audio often
 * has no Content-Length, so ExoPlayer cannot derive a duration).
 */
fun progressFraction(positionMs: Long, durationMs: Long, trackDurationMs: Long): Float {
    val effectiveDuration = if (durationMs > 0L) durationMs else trackDurationMs
    if (effectiveDuration <= 0L) return 0f
    return (positionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
}
