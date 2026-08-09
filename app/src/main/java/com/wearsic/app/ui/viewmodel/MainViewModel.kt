package com.wearsic.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.app.data.cache.PlaybackCache
import com.wearsic.app.data.model.Album
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.preferences.SettingsManager
import com.wearsic.app.data.repository.MusicRepository
import com.wearsic.app.service.MediaPlaybackService
import com.wearsic.app.service.PlaybackEvents
import com.wearsic.app.service.progressFraction
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    // Original queue order kept while shuffle is on, so turning shuffle off
    // restores the order the user had before.
    private var preShuffleQueue: List<Track>? = null
    
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
    private val _isLoadingMoreAlbum = MutableStateFlow(false)
    val isLoadingMoreAlbum: StateFlow<Boolean> = _isLoadingMoreAlbum.asStateFlow()
    private val _albumNextPage = MutableStateFlow<Int?>(null)
    val albumNextPage: StateFlow<Int?> = _albumNextPage.asStateFlow()
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

    // Offline audio cache
    val cacheSizeMb: StateFlow<Int> = settingsManager.cacheSizeMb
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_CACHE_SIZE_MB)

    private val _cacheUsageBytes = MutableStateFlow(0L)
    val cacheUsageBytes: StateFlow<Long> = _cacheUsageBytes.asStateFlow()

    val autoCacheEnabled: StateFlow<Boolean> = settingsManager.autoCacheEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsManager.DEFAULT_AUTO_CACHE_ENABLED)
    val downloadedIds: StateFlow<Set<String>> = PlaybackEvents.downloadedIds
    val downloadProgress: StateFlow<Map<String, Float>> = PlaybackEvents.downloadProgress
    val downloadErrors: StateFlow<Map<String, String>> = PlaybackEvents.downloadErrors

    // Kotlinx JSON used to ship the queue to the foreground service.
    private val queueJson = Json { ignoreUnknownKeys = true }
    
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

        // Live playback state reported by the foreground service. The fraction
        // falls back to the track's metadata duration when the stream itself
        // reports none (proxied audio often has no Content-Length).
        viewModelScope.launch {
            combine(
                PlaybackEvents.positionMs,
                PlaybackEvents.durationMs,
                _currentTrack
            ) { position, duration, track ->
                progressFraction(position, duration, track?.durationMs ?: 0L)
            }.collect { _progress.value = it }
        }
        viewModelScope.launch {
            PlaybackEvents.isPlaying.collect { _isPlaying.value = it }
        }
        viewModelScope.launch {
            PlaybackEvents.trackEnded.collect { onTrackEnded() }
        }

        // Mirror the queue owned by the foreground service so the UI stays in
        // sync when playback advances in the background (or via notification
        // controls), and so state is restored when the app is reopened.
        viewModelScope.launch {
            PlaybackEvents.queue.collect { queue -> if (queue.isNotEmpty()) _queue.value = queue }
        }
        viewModelScope.launch {
            PlaybackEvents.currentIndex.collect { index -> if (index >= 0) _currentIndex.value = index }
        }
        viewModelScope.launch {
            PlaybackEvents.currentTrack.collect { track -> if (track != null) _currentTrack.value = track }
        }
        viewModelScope.launch {
            PlaybackEvents.cacheCleared.collect { refreshCacheUsage() }
        }

        refreshCacheUsage()

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
        preShuffleQueue = null
        _currentTrack.value = track
        _isPlaying.value = true
        _progress.value = 0f
        if (_queue.value.none { it.videoId == track.videoId }) {
            _queue.value = _queue.value + track
            _currentIndex.value = _queue.value.size - 1
        } else {
            _currentIndex.value = _queue.value.indexOfFirst { it.videoId == track.videoId }
        }
        pushQueueToService()
    }
    
    /**
     * Play a list of tracks starting from index. Used for albums, search results
     * and playlists so the whole list lands in the queue (Spotify-style), with
     * the tapped track first.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        PlaybackEvents.clearError()
        autoPlayedVideoId = null
        preShuffleQueue = null
        // The same song can appear several times in search/playlist results
        // (different channels mirroring it); keep only the first occurrence.
        val deduped = tracks.distinctBy { it.videoId }
        if (deduped.isEmpty()) return
        var order = deduped
        var start = tracks.getOrNull(startIndex)?.let { tapped ->
            deduped.indexOfFirst { it.videoId == tapped.videoId }.coerceAtLeast(0)
        } ?: 0
        // Real shuffle: keep the tapped track first, randomize the rest. The
        // foreground service plays the pushed order, so this actually shuffles.
        if (_shuffleEnabled.value && order.size > 1) {
            val tapped = order.getOrNull(start)
            val rest = order.toMutableList()
                .apply { removeAt(start) }
                .shuffled()
                .toMutableList()
            if (tapped != null) rest.add(0, tapped)
            order = rest
            start = 0
        }
        _queue.value = order
        _currentIndex.value = start
        _currentTrack.value = order.getOrNull(start)
        _isPlaying.value = true
        _progress.value = 0f
        pushQueueToService()
    }

    /**
     * Play a track picked from a search result. Instead of queueing the whole
     * search page (often the same song mirrored by many channels), the queue is
     * seeded from the song's related/album tracks — the Spotify-style "up next".
     */
    fun playSearchResult(track: Track) {
        preShuffleQueue = null
        playTrack(track)
        viewModelScope.launch {
            repository.getRelatedTracks(track.videoId).onSuccess { response ->
                // Only apply if the user hasn't already moved on to another song
                // while the related list was loading.
                if (_currentTrack.value?.videoId != track.videoId) return@onSuccess
                val related = response.results
                    .filter { it.videoId != track.videoId }
                    .distinctBy { it.videoId }
                if (related.isNotEmpty()) {
                    _queue.value = listOf(track) + related
                    _currentIndex.value = 0
                    pushQueueToService()
                }
            }.onFailure {
                // Related fetch failed; the single-track queue still plays.
            }
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        if (_currentTrack.value == null) return
        _isPlaying.value = !_isPlaying.value
        sendServiceIntent(MediaPlaybackService.ACTION_TOGGLE_PLAYBACK)
    }
    
    /**
     * Skip to next track
     */
    fun skipToNext() {
        val currentIndex = _currentIndex.value
        val queue = _queue.value

        when {
            currentIndex < queue.size - 1 -> {
                _currentIndex.value = currentIndex + 1
                _currentTrack.value = queue[currentIndex + 1]
                _progress.value = 0f
                _isPlaying.value = true
                pushQueueToService()
            }
            _repeatEnabled.value && queue.isNotEmpty() -> {
                // Repeat from beginning
                _currentIndex.value = 0
                _currentTrack.value = queue[0]
                _progress.value = 0f
                _isPlaying.value = true
                pushQueueToService()
            }
            else -> autoplayRelated()
        }
    }
    
    /**
     * Skip to previous track
     */
    fun skipToPrevious() {
        val currentIndex = _currentIndex.value

        when {
            currentIndex > 0 -> {
                _currentIndex.value = currentIndex - 1
                _currentTrack.value = _queue.value[currentIndex - 1]
                _progress.value = 0f
                _isPlaying.value = true
                pushQueueToService()
            }
            _repeatEnabled.value && _queue.value.isNotEmpty() -> {
                // Repeat from end
                _currentIndex.value = _queue.value.size - 1
                _currentTrack.value = _queue.value.last()
                _progress.value = 0f
                _isPlaying.value = true
                pushQueueToService()
            }
        }
    }
    
    /**
     * Toggle shuffle. When enabled, the queued tracks are reordered so the
     * current track keeps its position and the rest play in random order; the
     * previous order is remembered and restored when shuffle is turned off.
     */
    fun toggleShuffle() {
        if (_shuffleEnabled.value) {
            _shuffleEnabled.value = false
            val restore = preShuffleQueue
            preShuffleQueue = null
            if (restore != null && restore.isNotEmpty() && _queue.value.isNotEmpty()) {
                val current = _currentTrack.value
                val index = current?.let { c ->
                    restore.indexOfFirst { it.videoId == c.videoId }
                } ?: -1
                _queue.value = restore
                _currentIndex.value = index.coerceAtLeast(0)
                _currentTrack.value = if (index >= 0) restore[index] else restore.firstOrNull()
                _isPlaying.value = true
                _progress.value = 0f
                pushQueueToService()
            }
        } else {
            _shuffleEnabled.value = true
            val queue = _queue.value
            if (queue.size > 1) {
                preShuffleQueue = queue
                val currentIndex = _currentIndex.value.coerceIn(0, queue.lastIndex)
                val current = queue[currentIndex]
                val rest = queue.toMutableList()
                    .apply { removeAt(currentIndex) }
                    .shuffled()
                    .toMutableList()
                rest.add(currentIndex, current)
                _queue.value = rest
                _currentIndex.value = currentIndex
                _currentTrack.value = current
                _isPlaying.value = true
                _progress.value = 0f
                pushQueueToService()
            }
        }
    }

    /**
     * Move a queue item to a new position (up/down reorder in the Queue
     * screen). The foreground service mirrors the reorder in its own timeline
     * so the currently playing track keeps playing at the same position.
     */
    fun moveQueueItem(from: Int, to: Int) {
        val queue = _queue.value
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        val oldIndex = _currentIndex.value
        val newIndex = when {
            oldIndex == from -> to
            oldIndex > from && oldIndex <= to -> oldIndex - 1
            oldIndex >= to && oldIndex < from -> oldIndex + 1
            else -> oldIndex
        }
        _queue.value = mutable
        _currentIndex.value = newIndex
        if (_currentTrack.value?.videoId == item.videoId) {
            _currentTrack.value = item
        }
        preShuffleQueue = null
        sendServiceIntent(MediaPlaybackService.ACTION_MOVE_QUEUE_ITEM) {
            putExtra(MediaPlaybackService.EXTRA_MOVE_FROM_INDEX, from)
            putExtra(MediaPlaybackService.EXTRA_MOVE_TO_INDEX, to)
        }
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
        _albumNextPage.value = null
        albumLoadJob = viewModelScope.launch {
            _isLoadingAlbum.value = true
            repository.getExternalPlaylist(album.url)
                .onSuccess { loaded ->
                    if (_selectedAlbum.value?.url == album.url) {
                        _albumTracks.value = loaded.tracks
                        _albumNextPage.value = loaded.nextPage
                    }
                }
                .onFailure { error ->
                    if (_selectedAlbum.value?.url == album.url) _albumError.value = error.message
                }
            if (_selectedAlbum.value?.url == album.url) _isLoadingAlbum.value = false
        }
    }

    /**
     * Load the next page of a long album/playlist using the server's
     * continuation tokens. Appends the extra tracks to the current list.
     */
    fun loadMoreAlbumTracks() {
        val album = _selectedAlbum.value ?: return
        val page = _albumNextPage.value ?: return
        if (albumLoadJob?.isActive == true) return
        albumLoadJob = viewModelScope.launch {
            _isLoadingMoreAlbum.value = true
            repository.getExternalPlaylist(album.url, page = page)
                .onSuccess { loaded ->
                    if (_selectedAlbum.value?.url == album.url) {
                        _albumTracks.value = (_albumTracks.value + loaded.tracks).distinctBy { it.videoId }
                        _albumNextPage.value = loaded.nextPage
                    }
                }
                .onFailure { error ->
                    if (_selectedAlbum.value?.url == album.url) _albumError.value = error.message
                }
            _isLoadingMoreAlbum.value = false
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
        if (_queue.value.none { it.videoId == track.videoId }) {
            _queue.value = _queue.value + track
            sendServiceIntent(MediaPlaybackService.ACTION_ADD_TO_QUEUE) {
                putExtra(MediaPlaybackService.EXTRA_ADD_TRACK_JSON, queueJson.encodeToString(track))
            }
        }
    }
    
    /**
     * Remove track from queue
     */
    fun removeFromQueue(index: Int) {
        preShuffleQueue = null
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
            if (_queue.value.isEmpty()) {
                sendServiceIntent(MediaPlaybackService.ACTION_CLEAR_QUEUE)
            } else {
                sendServiceIntent(MediaPlaybackService.ACTION_REMOVE_FROM_QUEUE) {
                    putExtra(MediaPlaybackService.EXTRA_REMOVE_INDEX, index)
                }
            }
        }
    }
    
    /**
     * Clear queue
     */
    fun clearQueue() {
        preShuffleQueue = null
        _queue.value = emptyList()
        _currentIndex.value = -1
        _currentTrack.value = null
        _isPlaying.value = false
        _progress.value = 0f
        sendServiceIntent(MediaPlaybackService.ACTION_CLEAR_QUEUE)
    }

    /**
     * Called when the foreground player reaches the end of a track. Advances to
     * the next queued track, restarts on repeat, or auto-fills the queue from
     * related tracks so music never stops.
     */
    private fun onTrackEnded() {
        val queue = _queue.value
        when {
            // The service auto-advances between queue items; STATE_ENDED only
            // fires when the whole queue is exhausted.
            _repeatEnabled.value && queue.isNotEmpty() -> {
                _currentIndex.value = 0
                _currentTrack.value = queue[0]
                _progress.value = 0f
                _isPlaying.value = true
                pushQueueToService()
            }
            else -> autoplayRelated()
        }
    }

    private fun autoplayRelated() {
        val track = _currentTrack.value ?: return
        if (autoPlayedVideoId == track.videoId) return
        viewModelScope.launch {
            repository.getRelatedTracks(track.videoId).onSuccess { response ->
                val related = response.results
                    .filter { it.videoId != track.videoId }
                    .distinctBy { it.videoId }
                if (related.isNotEmpty() && autoPlayedVideoId != track.videoId) {
                    autoPlayedVideoId = track.videoId
                    preShuffleQueue = null
                    _queue.value = related
                    _currentIndex.value = 0
                    _currentTrack.value = related.first()
                    _isPlaying.value = true
                    _progress.value = 0f
                    pushQueueToService()
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
    
    /**
     * Ship the whole queue (with the current index) to the foreground service.
     * The service owns playback, so music keeps playing and auto-advancing even
     * after this activity is destroyed.
     */
    private fun pushQueueToService() {
        if (serverUrl.value.isBlank()) return
        val queue = _queue.value
        if (queue.isEmpty()) return
        val intent = Intent(getApplication(), MediaPlaybackService::class.java).apply {
            this.action = MediaPlaybackService.ACTION_PLAY_TRACKS
            putExtra(MediaPlaybackService.EXTRA_SERVER_URL, serverUrl.value)
            putExtra(MediaPlaybackService.EXTRA_API_KEY, apiKey.value)
            putExtra(MediaPlaybackService.EXTRA_QUEUE_JSON, queueJson.encodeToString(queue))
            putExtra(MediaPlaybackService.EXTRA_START_INDEX, _currentIndex.value.coerceAtLeast(0))
        }
        try {
            // Playback is initiated by a visible user tap. Start as a foreground
            // service so Android cannot kill it while the stream is buffering.
            ContextCompat.startForegroundService(getApplication(), intent)
        } catch (error: RuntimeException) {
            _isPlaying.value = false
            _error.value = "Playback service could not start: ${error.message.orEmpty()}"
        }
    }

    /**
     * Send a control command (toggle, remove, clear, add-to-queue) to the
     * already-running foreground service.
     */
    private fun sendServiceIntent(action: String, configure: Intent.() -> Unit = {}) {
        if (serverUrl.value.isBlank()) return
        val intent = Intent(getApplication(), MediaPlaybackService::class.java).apply {
            this.action = action
            putExtra(MediaPlaybackService.EXTRA_SERVER_URL, serverUrl.value)
            putExtra(MediaPlaybackService.EXTRA_API_KEY, apiKey.value)
            configure()
        }
        try {
            if (action == MediaPlaybackService.ACTION_DOWNLOAD_TRACK) {
                ContextCompat.startForegroundService(getApplication(), intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (error: RuntimeException) {
            _error.value = "Playback service could not start: ${error.message.orEmpty()}"
        }
    }

    /**
     * Refresh the stored offline-cache usage from disk.
     */
    fun refreshCacheUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheUsageBytes.value = PlaybackCache.sizeBytes(getApplication())
        }
    }

    /**
     * Change the offline audio cache size limit (megabytes). Applies to new
     * playback sessions.
     */
    fun setCacheSizeMb(mb: Int) {
        viewModelScope.launch {
            settingsManager.saveCacheSizeMb(mb)
        }
    }

    fun setAutoCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveAutoCacheEnabled(enabled)
        }
    }

    /**
     * Download a complete track into the existing Media3 cache. The service
     * performs the blocking CacheWriter work off the main thread.
     */
    fun downloadTrack(track: Track) {
        if (serverUrl.value.isBlank()) {
            _error.value = "Configure your server URL in Settings first."
            return
        }
        sendServiceIntent(MediaPlaybackService.ACTION_DOWNLOAD_TRACK) {
            putExtra(
                MediaPlaybackService.EXTRA_DOWNLOAD_TRACK_JSON,
                queueJson.encodeToString(track)
            )
        }
    }

    /**
     * Delete every cached audio file immediately. When the foreground service
     * is holding the cache handle, ask it to clear safely (pause, delete, and
     * resume playback); otherwise delete directly.
     */
    fun clearPlaybackCache() {
        // Always route through the service. A download can be active even when
        // no playback queue is loaded, and the service is the only owner that
        // can cancel CacheWriter before removing cache spans safely.
        val intent = Intent(getApplication(), MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_CLEAR_CACHE
            putExtra(MediaPlaybackService.EXTRA_SERVER_URL, serverUrl.value)
            putExtra(MediaPlaybackService.EXTRA_API_KEY, apiKey.value)
        }
        try {
            ContextCompat.startForegroundService(getApplication(), intent)
        } catch (error: RuntimeException) {
            _error.value = "Could not clear cache: ${error.message.orEmpty()}"
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
