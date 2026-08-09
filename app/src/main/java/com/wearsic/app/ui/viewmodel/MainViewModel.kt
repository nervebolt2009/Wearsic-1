package com.wearsic.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.app.data.model.Album
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.preferences.SettingsManager
import com.wearsic.app.data.repository.MusicRepository
import com.wearsic.app.service.MediaPlaybackService
import com.wearsic.app.service.PlaybackEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Main ViewModel for managing app state
 * Handles playback state, queue management, search, and server communication
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = MusicRepository()
    private val settingsManager = SettingsManager(application)
    
    // Current track state
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()
    
    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // Progress (0.0 to 1.0)
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    // Queue
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    
    // Current index in queue
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    // Shuffle and repeat states
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    
    private val _repeatEnabled = MutableStateFlow(false)
    val repeatEnabled: StateFlow<Boolean> = _repeatEnabled.asStateFlow()
    
    // Favorites
    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow<List<com.wearsic.app.data.model.Playlist>>(emptyList())
    val playlists: StateFlow<List<com.wearsic.app.data.model.Playlist>> = _playlists.asStateFlow()
    
    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _albumSearchResults = MutableStateFlow<List<Album>>(emptyList())
    val albumSearchResults: StateFlow<List<Album>> = _albumSearchResults.asStateFlow()

    private val _albumsMode = MutableStateFlow(false)
    val albumsMode: StateFlow<Boolean> = _albumsMode.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()
    private val _albumTracks = MutableStateFlow<List<Track>>(emptyList())
    val albumTracks: StateFlow<List<Track>> = _albumTracks.asStateFlow()
    private val _isLoadingAlbum = MutableStateFlow(false)
    val isLoadingAlbum: StateFlow<Boolean> = _isLoadingAlbum.asStateFlow()
    private val _albumError = MutableStateFlow<String?>(null)
    val albumError: StateFlow<String?> = _albumError.asStateFlow()
    private var albumLoadJob: Job? = null
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    private var searchGeneration = 0L
    
    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Guards related-stream autoplay so a track is only auto-filled once per
    // manual play session instead of looping forever.
    private var autoPlayedVideoId: String? = null

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()
    private var connectionTestJob: Job? = null
    private var connectionTestGeneration = 0L
    
    // Server URL
    val serverUrl: StateFlow<String> = settingsManager.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey: StateFlow<String> = settingsManager.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val youtubeCookie: StateFlow<String> = settingsManager.youtubeCookie
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    
    init {
        // Configure the repository before any server-backed request is started.
        viewModelScope.launch {
            settingsManager.serverUrl.collectLatest { url ->
                repository.setServerUrl(url)
                if (url.isNotBlank()) {
                    testConnection()
                    loadFavorites()
                    loadPlaylists()
                } else {
                    connectionTestJob?.cancel()
                    connectionTestGeneration++
                    _isConnected.value = false
                    _isTestingConnection.value = false
                    _favorites.value = emptyList()
                    _playlists.value = emptyList()
                }
            }
        }
        
        viewModelScope.launch {
            settingsManager.apiKey.collect { key -> repository.setApiKey(key) }
        }

        // Push the YouTube cookie to the server whenever it or the server URL
        // changes. Debounced so typing in the Settings field does not hammer the
        // server, and harmless (silent) when the backend does not support it yet.
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            combine(
                settingsManager.youtubeCookie.distinctUntilChanged(),
                serverUrl
            ) { cookie, url -> cookie to url }
                .debounce(800)
                .collectLatest { (cookie, url) ->
                    if (url.isNotBlank()) {
                        repository.setYoutubeCookie(cookie)
                    }
                }
        }

        viewModelScope.launch {
            PlaybackEvents.error.filterNotNull().collect { message ->
                _isPlaying.value = false
                _error.value = message
            }
        }

        // Live playback state reported by the foreground service.
        viewModelScope.launch {
            PlaybackEvents.progress.collect { _progress.value = it }
        }
        viewModelScope.launch {
            PlaybackEvents.isPlaying.collect { _isPlaying.value = it }
        }
        viewModelScope.launch {
            PlaybackEvents.trackEnded.collect { onTrackEnded() }
        }

        // One debounced pipeline prevents stale responses from replacing newer searches.
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _searchQuery
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length >= 2) {
                        coroutineScope {
                            launch { loadSuggestions(query) }
                            performSearch(query)
                        }
                    } else {
                        _suggestions.value = emptyList()
                        _searchResults.value = emptyList()
                        _albumSearchResults.value = emptyList()
                        _isSearching.value = false
                    }
                }
        }
    }
    
    /**
     * Test connection to server
     */
    fun testConnection() {
        connectionTestJob?.cancel()
        val generation = ++connectionTestGeneration
        connectionTestJob = viewModelScope.launch {
            _isTestingConnection.value = true
            try {
                val result = repository.testConnection()
                if (generation == connectionTestGeneration) {
                    _isConnected.value = result.isSuccess
                    if (result.isFailure) {
                        _error.value = result.exceptionOrNull()?.message
                    }
                }
            } catch (_: CancellationException) {
                // A newer URL/test superseded this request.
            } finally {
                if (generation == connectionTestGeneration) {
                    _isTestingConnection.value = false
                }
            }
        }
    }
    
    /**
     * Save server URL
     */
    fun saveServerUrl(url: String) {
        // Update the repository immediately so a connection test triggered by
        // the same editing session never uses the previous URL.
        repository.setServerUrl(url)
        if (url.isBlank()) {
            connectionTestJob?.cancel()
            _isConnected.value = false
        }
        viewModelScope.launch {
            settingsManager.saveServerUrl(url)
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveApiKey(key)
        }
    }

    fun saveYoutubeCookie(cookie: String) {
        viewModelScope.launch {
            settingsManager.saveYoutubeCookie(cookie)
        }
    }
    
    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _albumSearchResults.value = emptyList()
            _isSearching.value = false
        }
    }
    
    /**
     * Load search suggestions
     */
    private suspend fun loadSuggestions(query: String) {
        val result = repository.getSuggestions(query)
        result.onSuccess { response ->
            _suggestions.value = response.suggestions
        }.onFailure {
            _suggestions.value = emptyList()
        }
    }
    
    /**
     * Search for tracks
     */
    fun setAlbumsMode(enabled: Boolean) {
        if (_albumsMode.value == enabled) return
        _albumsMode.value = enabled
        _suggestions.value = emptyList()
        if (!enabled) _albumSearchResults.value = emptyList()
        if (enabled) _searchResults.value = emptyList()
        val query = _searchQuery.value
        if (query.length >= 2) {
            viewModelScope.launch { performSearch(query) }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _albumSearchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        val requestedAlbumsMode = _albumsMode.value
        val generation = ++searchGeneration
        _isSearching.value = true
        try {
            if (requestedAlbumsMode) {
                val result = repository.searchAlbums(query)
                result.onSuccess { albums ->
                    if (generation == searchGeneration && _albumsMode.value == requestedAlbumsMode) {
                        _isConnected.value = true
                        _albumSearchResults.value = albums
                        _searchResults.value = emptyList()
                    }
                }.onFailure { e ->
                    if (generation == searchGeneration && _albumsMode.value == requestedAlbumsMode) {
                        _isConnected.value = false
                        _albumSearchResults.value = emptyList()
                        _error.value = e.message
                    }
                }
            } else {
                val result = repository.search(query)
                result.onSuccess { response ->
                    if (generation == searchGeneration && _albumsMode.value == requestedAlbumsMode) {
                        // A successful search is also a successful reachability signal.
                        _isConnected.value = true
                        _searchResults.value = response.results
                        _albumSearchResults.value = emptyList()
                    }
                }.onFailure { e ->
                    if (generation == searchGeneration && _albumsMode.value == requestedAlbumsMode) {
                        _isConnected.value = false
                        _error.value = e.message
                    }
                }
            }
        } finally {
            // Also runs when collectLatest cancels this search for a newer query,
            // so the loading indicator can never remain stuck.
            if (generation == searchGeneration) {
                _isSearching.value = false
            }
        }
    }
    
    /**
     * Play a track
     */
    fun playTrack(track: Track) {
        PlaybackEvents.clearError()
        if (serverUrl.value.isBlank()) {
            _error.value = "Configure your server URL in Settings first."
            _isPlaying.value = false
            return
        }
        autoPlayedVideoId = null
        _currentTrack.value = track
        _isPlaying.value = true
        _progress.value = 0f
        if (_queue.value.none { it.videoId == track.videoId }) {
            _queue.value = _queue.value + track
            _currentIndex.value = _queue.value.size - 1
        } else {
            _currentIndex.value = _queue.value.indexOfFirst { it.videoId == track.videoId }
        }
        startPlaybackService(MediaPlaybackService.ACTION_PLAY, track)
    }
    
    /**
     * Play a list of tracks starting from index. Used for albums, search results
     * and playlists so the whole list lands in the queue (Spotify-style), with
     * the tapped track first.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        PlaybackEvents.clearError()
        autoPlayedVideoId = null
        _queue.value = tracks
        _currentIndex.value = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        _currentTrack.value = tracks.getOrNull(startIndex)
        _isPlaying.value = tracks.getOrNull(startIndex) != null
        _progress.value = 0f
        tracks.getOrNull(startIndex)?.let {
            startPlaybackService(MediaPlaybackService.ACTION_PLAY, it)
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        if (_currentTrack.value == null) return
        _isPlaying.value = !_isPlaying.value
        startPlaybackService(MediaPlaybackService.ACTION_TOGGLE_PLAYBACK, _currentTrack.value)
    }
    
    /**
     * Skip to next track
     */
    fun skipToNext() {
        val currentIndex = _currentIndex.value
        val queue = _queue.value
        
        if (currentIndex < queue.size - 1) {
            _currentIndex.value = currentIndex + 1
            _currentTrack.value = queue[currentIndex + 1]
            _progress.value = 0f
            _isPlaying.value = true
            startPlaybackService(MediaPlaybackService.ACTION_PLAY, _currentTrack.value)
        } else if (_repeatEnabled.value && queue.isNotEmpty()) {
            // Repeat from beginning
            _currentIndex.value = 0
            _currentTrack.value = queue[0]
            _progress.value = 0f
            _isPlaying.value = true
            startPlaybackService(MediaPlaybackService.ACTION_PLAY, _currentTrack.value)
        }
    }
    
    /**
     * Skip to previous track
     */
    fun skipToPrevious() {
        val currentIndex = _currentIndex.value
        
        if (currentIndex > 0) {
            _currentIndex.value = currentIndex - 1
            _currentTrack.value = _queue.value[currentIndex - 1]
            _progress.value = 0f
            _isPlaying.value = true
            startPlaybackService(MediaPlaybackService.ACTION_PLAY, _currentTrack.value)
        } else if (_repeatEnabled.value && _queue.value.isNotEmpty()) {
            // Repeat from end
            _currentIndex.value = _queue.value.size - 1
            _currentTrack.value = _queue.value.last()
            _progress.value = 0f
            _isPlaying.value = true
            startPlaybackService(MediaPlaybackService.ACTION_PLAY, _currentTrack.value)
        }
    }
    
    /**
     * Toggle shuffle
     */
    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }
    
    /**
     * Toggle repeat
     */
    fun toggleRepeat() {
        _repeatEnabled.value = !_repeatEnabled.value
    }
    
    /**
     * Toggle favorite for current track. Reverts the local heart state if the
     * server call fails, so the button always reflects what is actually saved.
     */
    fun toggleFavorite() {
        val track = _currentTrack.value ?: return
        val wasFavorite = _favorites.value.any { it.videoId == track.videoId }

        viewModelScope.launch {
            val result = if (wasFavorite) {
                repository.removeFromFavorites(track.videoId)
            } else {
                repository.addToFavorites(track)
            }
            if (result.isSuccess) {
                if (wasFavorite) {
                    _favorites.value = _favorites.value.filter { it.videoId != track.videoId }
                } else if (_favorites.value.none { it.videoId == track.videoId }) {
                    _favorites.value = _favorites.value + track
                }
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Could not update favorites"
            }
        }
    }
    
    /**
     * Check if current track is favorite
     */
    fun isCurrentTrackFavorite(): Boolean {
        val track = _currentTrack.value ?: return false
        return _favorites.value.any { it.videoId == track.videoId }
    }
    
    /**
     * Add track to favorites
     */
    fun addToFavorites(track: Track) {
        viewModelScope.launch {
            if (_favorites.value.none { it.videoId == track.videoId }) {
                repository.addToFavorites(track)
                _favorites.value = _favorites.value + track
            }
        }
    }
    
    /**
     * Remove track from favorites
     */
    fun removeFromFavorites(track: Track) {
        viewModelScope.launch {
            repository.removeFromFavorites(track.videoId)
            _favorites.value = _favorites.value.filter { it.videoId != track.videoId }
        }
    }
    
    /**
     * Load favorites from server
     */
    fun loadFavorites() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getFavorites()
            _isLoading.value = false
            
            result.onSuccess { tracks ->
                _favorites.value = tracks
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }
    
    /**
     * Load playlists from the server.
     */
    fun loadPlaylists() {
        viewModelScope.launch {
            repository.getPlaylists().onSuccess { loaded ->
                _playlists.value = loaded
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    /**
     * Start the selected playlist from its first track.
     */
    fun loadAlbum(album: Album) {
        albumLoadJob?.cancel()
        _selectedAlbum.value = album
        _albumTracks.value = emptyList()
        _albumError.value = null
        albumLoadJob = viewModelScope.launch {
            _isLoadingAlbum.value = true
            repository.getExternalPlaylist(album.url)
                .onSuccess { loaded ->
                    if (_selectedAlbum.value?.url == album.url) _albumTracks.value = loaded.tracks
                }
                .onFailure { error ->
                    if (_selectedAlbum.value?.url == album.url) _albumError.value = error.message
                }
            if (_selectedAlbum.value?.url == album.url) _isLoadingAlbum.value = false
        }
    }

    fun playExternalAlbumTrack(track: Track) {
        playTrack(track)
    }

    fun playPlaylist(playlist: com.wearsic.app.data.model.Playlist) {
        viewModelScope.launch {
            repository.getPlaylist(playlist.id).onSuccess { loaded ->
                playTracks(loaded.tracks)
            }.onFailure { error ->
                _error.value = error.message
            }
        }
    }

    /**
     * Add track to queue
     */
    fun addToQueue(track: Track) {
        _queue.value = _queue.value + track
    }
    
    /**
     * Remove track from queue
     */
    fun removeFromQueue(index: Int) {
        if (index in _queue.value.indices) {
            _queue.value = _queue.value.toMutableList().apply { removeAt(index) }
            
            // Adjust current index if needed
            if (index < _currentIndex.value) {
                _currentIndex.value--
            } else if (index == _currentIndex.value) {
                // Current track was removed
                if (_queue.value.isNotEmpty()) {
                    val newIndex = _currentIndex.value.coerceAtMost(_queue.value.size - 1)
                    _currentIndex.value = newIndex
                    _currentTrack.value = _queue.value[newIndex]
                } else {
                    _currentIndex.value = -1
                    _currentTrack.value = null
                    _isPlaying.value = false
                }
            }
        }
    }
    
    /**
     * Clear queue
     */
    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentTrack.value = null
        _isPlaying.value = false
        _progress.value = 0f
    }

    /**
     * Called when the foreground player reaches the end of a track. Advances to
     * the next queued track, restarts on repeat, or auto-fills the queue from
     * related tracks so music never stops.
     */
    private fun onTrackEnded() {
        val queue = _queue.value
        val index = _currentIndex.value
        when {
            index in 0 until queue.size - 1 -> skipToNext()
            _repeatEnabled.value && queue.isNotEmpty() -> {
                _currentIndex.value = 0
                _currentTrack.value = queue[0]
                _progress.value = 0f
                _isPlaying.value = true
                startPlaybackService(MediaPlaybackService.ACTION_PLAY, _currentTrack.value)
            }
            else -> autoplayRelated()
        }
    }

    private fun autoplayRelated() {
        val track = _currentTrack.value ?: return
        if (autoPlayedVideoId == track.videoId) return
        viewModelScope.launch {
            repository.getRelatedTracks(track.videoId).onSuccess { response ->
                if (response.results.isNotEmpty() && autoPlayedVideoId != track.videoId) {
                    autoPlayedVideoId = track.videoId
                    playTracks(response.results, 0)
                }
            }.onFailure {
                // No related stream available; stop quietly.
            }
        }
    }
    
    /**
     * Update progress
     */
    fun updateProgress(progress: Float) {
        _progress.value = progress.coerceIn(0f, 1f)
    }
    
    private fun startPlaybackService(action: String, track: Track?) {
        if (serverUrl.value.isBlank()) {
            _isPlaying.value = false
            _error.value = "Configure your server URL in Settings first."
            return
        }
        val intent = Intent(getApplication(), MediaPlaybackService::class.java).apply {
            this.action = action
            putExtra(MediaPlaybackService.EXTRA_SERVER_URL, serverUrl.value)
            putExtra(MediaPlaybackService.EXTRA_API_KEY, apiKey.value)
            track?.let {
                putExtra(MediaPlaybackService.EXTRA_VIDEO_ID, it.videoId)
                putExtra(MediaPlaybackService.EXTRA_TITLE, it.title)
                putExtra(MediaPlaybackService.EXTRA_UPLOADER, it.uploader)
                putExtra(MediaPlaybackService.EXTRA_DURATION_MS, it.durationMs)
                putExtra(MediaPlaybackService.EXTRA_THUMBNAIL_URL, it.thumbnailUrl)
            }
        }
        try {
            if (action == MediaPlaybackService.ACTION_PLAY) {
                // Playback is initiated by a visible user tap. Start as a foreground
                // service so Android cannot kill it while the stream is buffering.
                ContextCompat.startForegroundService(getApplication(), intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (error: RuntimeException) {
            _isPlaying.value = false
            _error.value = "Playback service could not start: ${error.message.orEmpty()}"
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
        PlaybackEvents.clearError()
    }
    
    override fun onCleared() {
        connectionTestJob?.cancel()
        albumLoadJob?.cancel()
        super.onCleared()
        repository.close()
    }
}
