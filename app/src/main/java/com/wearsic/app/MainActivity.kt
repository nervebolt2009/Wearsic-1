package com.wearsic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wearsic.app.ui.components.BottomNavigation
import com.wearsic.app.ui.navigation.NavigationManager
import com.wearsic.app.ui.navigation.Screen
import com.wearsic.app.ui.screens.*
import com.wearsic.app.ui.theme.WearsicTheme
import com.wearsic.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearsicTheme {
                WearsicApp()
            }
        }
    }
}

@Composable
fun WearsicApp(
    viewModel: MainViewModel = viewModel()
) {
    val navigationManager = remember { NavigationManager() }
    val currentScreen by navigationManager.currentScreen

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackError by viewModel.error.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatEnabled by viewModel.repeatEnabled.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val albumSearchResults by viewModel.albumSearchResults.collectAsState()
    val albumsMode by viewModel.albumsMode.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val youtubeCookie by viewModel.youtubeCookie.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val albumTracks by viewModel.albumTracks.collectAsState()
    val isLoadingAlbum by viewModel.isLoadingAlbum.collectAsState()
    val albumError by viewModel.albumError.collectAsState()

    // Keep a clear safe area above the pill-shaped navigation. The pill is
    // 48dp tall and sits 42dp above the bottom edge (real 235dp round display),
    // so 96dp of bottom padding keeps screen content clear of it.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
        ) {
            when (currentScreen) {
                is Screen.NowPlaying -> NowPlayingScreen(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    playbackError = playbackError,
                    progress = progress,
                    shuffleEnabled = shuffleEnabled,
                    repeatEnabled = repeatEnabled,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipToNext() },
                    onPrevious = { viewModel.skipToPrevious() },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.toggleRepeat() },
                    onFavoriteToggle = { viewModel.toggleFavorite() },
                    isFavorite = viewModel.isCurrentTrackFavorite()
                )

                is Screen.Search -> SearchScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    suggestions = suggestions,
                    searchResults = searchResults,
                    isLoading = isSearching,
                    albums = albumSearchResults,
                    albumsMode = albumsMode,
                    onAlbumsModeChange = viewModel::setAlbumsMode,
                    onAlbumClick = { album ->
                        viewModel.loadAlbum(album)
                        navigationManager.navigateTo(Screen.AlbumDetail)
                    },
                    onTrackClick = { track ->
                        // Play from the tapped track so the whole result list
                        // becomes the queue (Spotify-style), never a 1-song queue.
                        val index = searchResults.indexOfFirst { it.videoId == track.videoId }
                            .coerceAtLeast(0)
                        viewModel.playTracks(searchResults, index)
                        navigationManager.navigateTo(Screen.NowPlaying)
                    },
                    onAddToFavorites = viewModel::addToFavorites,
                    onAddToQueue = viewModel::addToQueue
                )

                is Screen.AlbumDetail -> selectedAlbum?.let { album ->
                    AlbumDetailScreen(
                        album = album,
                        tracks = albumTracks,
                        isLoading = isLoadingAlbum,
                        errorMessage = albumError,
                        onRetry = { viewModel.loadAlbum(album) },
                        onTrackClick = { track ->
                            // Queue the whole album, starting from the tapped track.
                            val index = albumTracks.indexOfFirst { it.videoId == track.videoId }
                                .coerceAtLeast(0)
                            viewModel.playTracks(albumTracks, index)
                            navigationManager.navigateTo(Screen.NowPlaying)
                        }
                    )
                }

                is Screen.Favorites -> FavoritesPlaylistsScreen(
                    favorites = favorites,
                    playlists = playlists,
                    isLoading = isLoading,
                    onTrackClick = { track ->
                        val index = favorites.indexOfFirst { it.videoId == track.videoId }
                            .coerceAtLeast(0)
                        viewModel.playTracks(favorites, index)
                        navigationManager.navigateTo(Screen.NowPlaying)
                    },
                    onRemoveFromFavorites = viewModel::removeFromFavorites,
                    onPlaylistClick = { playlist ->
                        viewModel.playPlaylist(playlist)
                        navigationManager.navigateTo(Screen.NowPlaying)
                    }
                )

                is Screen.Queue -> QueueScreen(
                    currentTrack = currentTrack,
                    queue = queue,
                    currentIndex = currentIndex,
                    onTrackClick = { index ->
                        queue.getOrNull(index)?.let {
                            viewModel.playTrack(it)
                            navigationManager.navigateTo(Screen.NowPlaying)
                        }
                    },
                    onRemoveFromQueue = viewModel::removeFromQueue,
                    onClearQueue = viewModel::clearQueue
                )

                is Screen.Settings -> SettingsScreen(
                    serverUrl = serverUrl,
                    onServerUrlChange = viewModel::saveServerUrl,
                    onTestConnection = viewModel::testConnection,
                    isConnected = isConnected,
                    isLoading = isTestingConnection,
                    apiKey = apiKey,
                    onApiKeyChange = viewModel::saveApiKey,
                    youtubeCookie = youtubeCookie,
                    onYoutubeCookieChange = viewModel::saveYoutubeCookie
                )
            }
        }

        BottomNavigation(
            currentScreen = currentScreen,
            onNavigate = navigationManager::navigateTo,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
