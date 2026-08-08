package com.wearsic.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.preferences.SettingsManager
import com.wearsic.app.data.repository.MusicRepository
import com.wearsic.app.service.MediaPlaybackService
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
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()
    
    // Server URL
    val serverUrl: StateFlow<String> = settingsManager.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey: StateFlow<String> = settingsManager.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    
    init {
        // Configure the repository before any server-backed request is started.
        viewModelScope.launch {
            settingsManager.serverUrl.collect { url ->
                if (url.isNotBlank()) {
                    repository.setServerUrl(url)
                    testConnection()
                    loadFavorites()
                    loadPlaylists()
                } else {
                    _isConnected.value = false
                    _favorites.value = emptyList()
                    _playlists.value = emptyList()
                }
            }
        }
        
        viewModelScope.launch {
            settingsManager.apiKey.collect { key -> repository.setApiKey(key) }
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
                        _isSearching.value = false
                    }
                }
        }
    }
    
    /**
     * Test connection to server
     */
    fun testConnection() {
        if (_isTestingConnection.value) return
        viewModelScope.launch {
            _isTestingConnection.value = true
            val result = repository.testConnection()
            _isTestingConnection.value = false
            _isConnected.value = result.isSuccess
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }
    
    /**
     * Save server URL
     */
    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            settingsManager.saveServerUrl(url)
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveApiKey(key)
        }
    }
    
    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
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
    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        val result = repository.search(query)
        _isSearching.value = false
        result.onSuccess { response ->
            _searchResults.value = response.results
        }.onFailure { e ->
            _error.value = e.message
        }
    }
    
    /**
     * Play a track
     */
    fun playTrack(track: Track) {
        if (serverUrl.value.isBlank()) {
            _error.value = "Configure your server URL in Settings first."
            _isPlaying.value = false
            return
        }
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
     * Play a list of tracks starting from index
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        _queue.value = tracks
        _currentIndex.value = startIndex
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
     * Toggle favorite for current track
     */
    fun toggleFavorite() {
        val track = _currentTrack.value ?: return
        
        viewModelScope.launch {
            if (_favorites.value.any { it.videoId == track.videoId }) {
                repository.removeFromFavorites(track.videoId)
                _favorites.value = _favorites.value.filter { it.videoId != track.videoId }
            } else {
                repository.addToFavorites(track)
                _favorites.value = _favorites.value + track
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
            if (action == MediaPlaybackService.ACTION_TOGGLE_PLAYBACK) {
                getApplication<Application>().startService(intent)
            } else {
                ContextCompat.startForegroundService(getApplication(), intent)
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
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
