package com.wearsic.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wearsic.app.R
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.ambient.AmbientState
import com.wearsic.app.ui.navigation.NavigationManager
import com.wearsic.app.ui.navigation.Screen
import com.wearsic.app.ui.screens.*
import com.wearsic.app.ui.theme.WearsicTheme
import com.wearsic.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // MediaPlaybackService checks the grant itself before showing its
            // notification, so nothing else needs to happen here.
        }
    private var notificationPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt into ambient (always-on display) mode: the system keeps this
        // activity's UI visible and dimmed when the watch screen times out,
        // and the observer flips the app into its low-power rendering.
        lifecycle.addObserver(
            AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    AmbientState.enterAmbient(
                        burnInProtectionRequired = ambientDetails.burnInProtectionRequired,
                        lowBitAmbient = ambientDetails.deviceHasLowBitAmbient
                    )
                }

                override fun onUpdateAmbient() {
                    AmbientState.updateAmbient()
                }

                override fun onExitAmbient() {
                    AmbientState.exitAmbient()
                }
            })
        )
        setContent {
            WearsicTheme {
                WearsicApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Android 13+ requires a runtime grant before a media notification can
        // be shown. Request it once; the service degrades gracefully without it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionRequested
        ) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * App shell. Each screen subscribes to only the ViewModel state it renders,
 * inside its own branch — so e.g. search-suggestion updates never recompose the
 * Now Playing screen, and download progress ticks only affect screens that
 * show download buttons. This keeps the watch UI smooth.
 */
@Composable
fun WearsicApp(
    viewModel: MainViewModel = viewModel()
) {
    val navigationManager = remember { NavigationManager() }
    val currentScreen by navigationManager.currentScreen

    // Ambient (always-on display): every screen drops to a single low-power,
    // monochrome overlay. Full-color UI (artwork, images, gradient fills)
    // would otherwise stay lit on the OLED for the whole dimmed period — the
    // biggest battery drain in ambient mode.
    val isAmbient by AmbientState.isAmbient.collectAsState()
    if (isAmbient) {
        val ambientTrack by viewModel.currentTrack.collectAsState()
        val ambientPlaying by viewModel.isPlaying.collectAsState()
        AmbientOverlay(currentTrack = ambientTrack, isPlaying = ambientPlaying)
        return
    }

    // System back gesture navigates back through the in-app history.
    BackHandler(enabled = navigationManager.canNavigateBack()) {
        navigationManager.navigateBack()
    }

    // No permanent bottom bar: the screens navigate on their own. Now Playing
    // exposes Search/Favorites/Queue/Settings icons, and every other screen has
    // a back button, so the whole 235dp round display stays usable.
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            is Screen.NowPlaying -> {
                val currentTrack by viewModel.currentTrack.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val playbackError by viewModel.error.collectAsState()
                val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
                val repeatEnabled by viewModel.repeatEnabled.collectAsState()
                val favorites by viewModel.favorites.collectAsState()
                NowPlayingScreen(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    playbackError = playbackError,
                    shuffleEnabled = shuffleEnabled,
                    repeatEnabled = repeatEnabled,
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.skipToNext() },
                    onPrevious = { viewModel.skipToPrevious() },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.toggleRepeat() },
                    onFavoriteToggle = { viewModel.toggleFavorite() },
                    isFavorite = favorites.any { it.videoId == currentTrack?.videoId },
                    onRetry = { viewModel.retryPlayback() },
                    onNavigate = navigationManager::navigateTo
                )
            }

            is Screen.Search -> {
                val searchQuery by viewModel.searchQuery.collectAsState()
                val suggestions by viewModel.suggestions.collectAsState()
                val searchResults by viewModel.searchResults.collectAsState()
                val albumSearchResults by viewModel.albumSearchResults.collectAsState()
                val albumsMode by viewModel.albumsMode.collectAsState()
                val isSearching by viewModel.isSearching.collectAsState()
                val downloadedIds by viewModel.downloadedIds.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val downloadErrors by viewModel.downloadErrors.collectAsState()
                val playbackError by viewModel.error.collectAsState()
                SearchScreen(
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
                        // Queue is seeded from the song's related/album tracks instead
                        // of the search page (which can be the same song mirrored by
                        // many channels).
                        viewModel.playSearchResult(track)
                        navigationManager.navigateTo(Screen.NowPlaying)
                    },
                    onAddToFavorites = viewModel::addToFavorites,
                    onAddToQueue = viewModel::addToQueue,
                    onDownload = viewModel::downloadTrack,
                    downloadedIds = downloadedIds,
                    downloadProgress = downloadProgress,
                    downloadErrors = downloadErrors,
                    errorMessage = playbackError,
                    onDismissError = viewModel::clearError,
                    onBack = { navigationManager.navigateBack() }
                )
            }

            is Screen.AlbumDetail -> {
                val selectedAlbum by viewModel.selectedAlbum.collectAsState()
                val albumTracks by viewModel.albumTracks.collectAsState()
                val isLoadingAlbum by viewModel.isLoadingAlbum.collectAsState()
                val albumError by viewModel.albumError.collectAsState()
                val albumNextPage by viewModel.albumNextPage.collectAsState()
                val isLoadingMoreAlbum by viewModel.isLoadingMoreAlbum.collectAsState()
                val downloadedIds by viewModel.downloadedIds.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val downloadErrors by viewModel.downloadErrors.collectAsState()
                selectedAlbum?.let { album ->
                    AlbumDetailScreen(
                        album = album,
                        tracks = albumTracks,
                        isLoading = isLoadingAlbum,
                        errorMessage = albumError,
                        onRetry = { viewModel.loadAlbum(album) },
                        hasMore = albumNextPage != null,
                        isLoadingMore = isLoadingMoreAlbum,
                        onLoadMore = viewModel::loadMoreAlbumTracks,
                        onTrackClick = { track ->
                            // Queue the whole album, starting from the tapped track.
                            val index = albumTracks.indexOfFirst { it.videoId == track.videoId }
                                .coerceAtLeast(0)
                            viewModel.playTracks(albumTracks, index)
                            navigationManager.navigateTo(Screen.NowPlaying)
                        },
                        onDownload = viewModel::downloadTrack,
                        downloadedIds = downloadedIds,
                        downloadProgress = downloadProgress,
                        downloadErrors = downloadErrors,
                        onBack = { navigationManager.navigateBack() }
                    )
                }
            }

            is Screen.Favorites -> {
                val favorites by viewModel.favorites.collectAsState()
                val playlists by viewModel.playlists.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val downloadedIds by viewModel.downloadedIds.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val downloadErrors by viewModel.downloadErrors.collectAsState()
                val playbackError by viewModel.error.collectAsState()
                FavoritesPlaylistsScreen(
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
                    },
                    onTogglePlaylistLiked = viewModel::togglePlaylistLiked,
                    onDownload = viewModel::downloadTrack,
                    downloadedIds = downloadedIds,
                    downloadProgress = downloadProgress,
                    downloadErrors = downloadErrors,
                    errorMessage = playbackError,
                    onDismissError = viewModel::clearError,
                    onBack = { navigationManager.navigateBack() }
                )
            }

            is Screen.Queue -> {
                val currentTrack by viewModel.currentTrack.collectAsState()
                val queue by viewModel.queue.collectAsState()
                val currentIndex by viewModel.currentIndex.collectAsState()
                QueueScreen(
                    currentTrack = currentTrack,
                    queue = queue,
                    currentIndex = currentIndex,
                    onTrackClick = { index ->
                        queue.getOrNull(index)?.let {
                            viewModel.playTrack(it)
                            navigationManager.navigateTo(Screen.NowPlaying)
                        }
                    },
                    onMoveUp = { index -> viewModel.moveQueueItem(index, index - 1) },
                    onMoveDown = { index -> viewModel.moveQueueItem(index, index + 1) },
                    onRemoveFromQueue = viewModel::removeFromQueue,
                    onClearQueue = viewModel::clearQueue,
                    onBack = { navigationManager.navigateBack() }
                )
            }

            is Screen.Settings -> {
                val serverUrl by viewModel.serverUrl.collectAsState()
                val isConnected by viewModel.isConnected.collectAsState()
                val isTestingConnection by viewModel.isTestingConnection.collectAsState()
                val apiKey by viewModel.apiKey.collectAsState()
                val youtubeCookie by viewModel.youtubeCookie.collectAsState()
                val cacheSizeMb by viewModel.cacheSizeMb.collectAsState()
                val cacheUsageBytes by viewModel.cacheUsageBytes.collectAsState()
                val autoCacheEnabled by viewModel.autoCacheEnabled.collectAsState()
                val playbackError by viewModel.error.collectAsState()
                SettingsScreen(
                    serverUrl = serverUrl,
                    onServerUrlChange = viewModel::saveServerUrl,
                    onTestConnection = viewModel::testConnection,
                    isConnected = isConnected,
                    isLoading = isTestingConnection,
                    apiKey = apiKey,
                    onApiKeyChange = viewModel::saveApiKey,
                    youtubeCookie = youtubeCookie,
                    onYoutubeCookieChange = viewModel::saveYoutubeCookie,
                    onBack = { navigationManager.navigateBack() },
                    cacheSizeMb = cacheSizeMb,
                    onCacheSizeMbChange = viewModel::setCacheSizeMb,
                    cacheUsageBytes = cacheUsageBytes,
                    onClearCache = viewModel::clearPlaybackCache,
                    autoCacheEnabled = autoCacheEnabled,
                    onAutoCacheEnabledChange = viewModel::setAutoCacheEnabled,
                    errorMessage = playbackError,
                    onDismissError = viewModel::clearError
                )
            }
        }
    }
}

/**
 * The only thing drawn while the watch is in ambient (always-on) mode: a
 * black, monochrome screen with the current track and play state. No images,
 * no gradients, no animation. When the system requests burn-in protection the
 * static content shifts a few pixels on each periodic ambient update.
 */
@Composable
private fun AmbientOverlay(currentTrack: Track?, isPlaying: Boolean) {
    val burnInProtection by AmbientState.burnInProtectionRequired.collectAsState()
    val tick by AmbientState.ambientTick.collectAsState()
    val shiftX = if (burnInProtection) {
        when (tick % 3) {
            0 -> 0.dp
            1 -> (-3).dp
            else -> 3.dp
        }
    } else {
        0.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .offset(x = shiftX),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = currentTrack?.title ?: stringResource(R.string.no_tracks),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!currentTrack?.uploader.isNullOrBlank()) {
                Text(
                    text = currentTrack?.uploader.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
