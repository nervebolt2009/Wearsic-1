package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.*
import coil.compose.AsyncImage
import com.wearsic.app.R
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.model.Playlist
import com.wearsic.app.ui.components.BackButton
import com.wearsic.app.ui.components.ErrorBanner

/**
 * Favorites/Playlists screen with tabs for favorites and playlists
 */
@Composable
fun FavoritesPlaylistsScreen(
    favorites: List<Track>,
    playlists: List<Playlist>,
    isLoading: Boolean,
    onTrackClick: (Track) -> Unit,
    onRemoveFromFavorites: (Track) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onTogglePlaylistLiked: (Playlist) -> Unit = {},
    onDownload: (Track) -> Unit = {},
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    downloadErrors: Map<String, String> = emptyMap(),
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    ScreenScaffold(
        modifier = modifier
    ) {
        val listState = rememberScalingLazyListState()
        val rotaryBehavior = RotaryScrollableDefaults.behavior(listState)
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = rotaryBehavior,
            contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with back navigation
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack)
                    Text(
                        text = stringResource(R.string.favorites),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 40.dp)
                    )
                }
            }
            
            // Error banner (e.g. favorites failed to load from the server).
            if (!errorMessage.isNullOrBlank()) {
                item {
                    ErrorBanner(message = errorMessage, onDismiss = onDismissError)
                }
            }

            // Tab Selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Favorites Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (selectedTab == 0) Color(0xFF1DB954).copy(alpha = 0.3f) 
                                       else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTab = 0 }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedTab == 0) Color.White 
                                   else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    
                    // Playlists Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (selectedTab == 1) Color(0xFF1DB954).copy(alpha = 0.3f) 
                                       else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTab = 1 }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Playlists",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedTab == 1) Color.White 
                                   else Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            // Loading Indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
            
            // Favorites Tab Content
            if (selectedTab == 0) {
                if (favorites.isEmpty() && !isLoading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_favorite_outline),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "No favorites yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(favorites.size) { index ->
                        FavoriteItem(
                            track = favorites[index],
                            onClick = { onTrackClick(favorites[index]) },
                            onRemove = { onRemoveFromFavorites(favorites[index]) },
                            onDownload = { onDownload(favorites[index]) },
                            isDownloaded = favorites[index].videoId in downloadedIds,
                            downloadProgress = downloadProgress[favorites[index].videoId],
                            downloadError = downloadErrors[favorites[index].videoId],
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                }
            }
            
            // Playlists Tab Content
            if (selectedTab == 1) {
                if (playlists.isEmpty() && !isLoading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_queue),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "No playlists yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(playlists.size) { index ->
                        PlaylistItem(
                            playlist = playlists[index],
                            onClick = { onPlaylistClick(playlists[index]) },
                            onToggleLiked = { onTogglePlaylistLiked(playlists[index]) },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Favorite item with track info and remove action
 */
@Composable
private fun FavoriteItem(
    track: Track,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDownload: () -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    downloadError: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 3.dp)
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track thumbnail
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Track info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = track.uploader,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Keep the compact metadata row separate from actions. This prevents
        // the two 48dp controls from pushing text beyond a round 44mm bezel.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = track.formatDuration(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp)
            )
            Row {
                if (downloadError != null) {
                    Text(
                        text = "Failed",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color(0xFFE91E63),
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp)
                    )
                }
                IconButton(
                    onClick = onDownload,
                    enabled = !isDownloaded && downloadProgress == null,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (downloadProgress != null) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = if (isDownloaded) "Available offline" else "Download for offline",
                            tint = if (isDownloaded) Color(0xFFB7F397) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_favorite_filled),
                        contentDescription = stringResource(R.string.remove_from_favorites),
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Playlist item with playlist info and a like (heart) action.
 */
@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onToggleLiked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 3.dp)
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playlist icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_queue),
                contentDescription = null,
                tint = Color(0xFF1DB954),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Playlist info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = "${playlist.trackCount} tracks",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        
        // Like/unlike the playlist. Kept as a separate 48dp control so the
        // whole row is still tappable for playback.
        IconButton(
            onClick = onToggleLiked,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(
                    id = if (playlist.liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
                ),
                contentDescription = stringResource(
                    if (playlist.liked) R.string.unlike_playlist else R.string.like_playlist
                ),
                tint = if (playlist.liked) Color(0xFFE91E63) else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Preview function for FavoritesPlaylistsScreen
 */
@Composable
fun FavoritesPlaylistsScreenPreview() {
    FavoritesPlaylistsScreen(
        favorites = listOf(
            Track(
                videoId = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                uploader = "Rick Astley",
                durationMs = 212000,
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            )
        ),
        playlists = listOf(
            Playlist(
                id = "1",
                name = "My Favorites",
                trackCount = 10,
                thumbnailUrl = null
            )
        ),
        isLoading = false,
        onTrackClick = {},
        onRemoveFromFavorites = {},
        onPlaylistClick = {}
    )
}
