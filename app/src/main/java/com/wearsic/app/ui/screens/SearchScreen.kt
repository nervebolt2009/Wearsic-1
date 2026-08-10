package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import com.wearsic.app.data.model.Album
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.components.BackButton
import com.wearsic.app.ui.components.ErrorBanner

/**
 * Search screen with text input, live suggestions, and results list
 */
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    suggestions: List<String>,
    searchResults: List<Track>,
    isLoading: Boolean,
    onTrackClick: (Track) -> Unit,
    onAddToFavorites: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onAddToQueue: (Track) -> Unit = {},
    onDownload: (Track) -> Unit = {},
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    downloadErrors: Map<String, String> = emptyMap(),
    albums: List<Album> = emptyList(),
    albumsMode: Boolean = false,
    onAlbumsModeChange: (Boolean) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onBack: () -> Unit = {}
) {
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
            // Search Header with back navigation
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack)
                    Text(
                        text = stringResource(R.string.search),
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
            
            // Error banner: search/connection failures are visible right here
            // instead of only on the Now Playing screen.
            if (!errorMessage.isNullOrBlank()) {
                item {
                    ErrorBanner(message = errorMessage, onDismiss = onDismissError)
                }
            }

            // Search Input Field
            item {
                Text(
                    text = "Find something to play",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(bottom = 6.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.56f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Tap to search...",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                    color = Color.White.copy(alpha = 0.48f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchModeButton(
                        label = "Tracks",
                        selected = !albumsMode,
                        onClick = { onAlbumsModeChange(false) },
                        modifier = Modifier.weight(1f)
                    )
                    SearchModeButton(
                        label = "Albums",
                        selected = albumsMode,
                        onClick = { onAlbumsModeChange(true) },
                        modifier = Modifier.weight(1f)
                    )
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
            
            // Suggestions List
            if (!albumsMode && suggestions.isNotEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                
                items(suggestions.take(5).size, key = { "${it}-${suggestions[it]}" }) { index ->
                    SuggestionItem(
                        suggestion = suggestions[index],
                        onClick = { onSearchQueryChange(suggestions[index]) },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            }
            
            // Album Results
            if (albumsMode && albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Albums / Playlists (${albums.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(0.9f).padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(albums.size, key = { "${it}-${albums[it].id}" }) { index ->
                    AlbumResultItem(
                        album = albums[index],
                        onClick = { onAlbumClick(albums[index]) },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            }

            // Search Results
            if (!albumsMode && searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Results (${searchResults.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                
                items(searchResults.size, key = { searchResults[it].videoId }) { index ->
                    SearchResultItem(
                        track = searchResults[index],
                        onClick = { onTrackClick(searchResults[index]) },
                        onAddToFavorites = { onAddToFavorites(searchResults[index]) },
                        onAddToQueue = { onAddToQueue(searchResults[index]) },
                        onDownload = { onDownload(searchResults[index]) },
                        isDownloaded = searchResults[index].videoId in downloadedIds,
                        downloadProgress = downloadProgress[searchResults[index].videoId],
                        downloadError = downloadErrors[searchResults[index].videoId],
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            }
            
            // No Results
            if (!isLoading && searchQuery.isNotEmpty() &&
                (if (albumsMode) albums.isEmpty() else searchResults.isEmpty()) && suggestions.isEmpty()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.background(
            if (selected) Color(0xFFB7F397).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f),
            RoundedCornerShape(18.dp)
        )
    ) {
        Text(label, color = if (selected) Color(0xFFB7F397) else Color.White.copy(alpha = 0.65f))
    }
}

@Composable
private fun AlbumResultItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 3.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
        ) {
            AsyncImage(
                model = album.thumbnailUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${album.uploader} • ${album.trackCount} tracks", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painter = painterResource(R.drawable.ic_skip_next), contentDescription = "Open album", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

/**
 * Suggestion item for autocomplete
 */
@Composable
private fun SuggestionItem(
    suggestion: String,
    onClick: () -> Unit,
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Search result item with track info and actions.
 * Two-line card so the title keeps full width and the action buttons
 * never starve it on round 44mm watches (174dp viewport).
 */
@Composable
private fun SearchResultItem(
    track: Track,
    onClick: () -> Unit,
    onAddToFavorites: () -> Unit,
    onAddToQueue: () -> Unit = {},
    onDownload: () -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    downloadError: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 3.dp)
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Line 1: thumbnail + title/artist (full available width)
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
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

            Column(modifier = Modifier.weight(1f)) {
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
        }

        // Actions are wrapped into two rows so every control keeps a 48dp
        // touch target without overflowing the 44mm round viewport.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Row {
                IconButton(
                    onClick = onAddToFavorites,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_favorite_outline),
                        contentDescription = stringResource(R.string.add_to_favorites),
                        tint = Color(0xFFE91E63).copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onAddToQueue,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_queue),
                        contentDescription = "Add to queue",
                        tint = Color(0xFFB7F397).copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row {
                if (downloadError != null) {
                    Text(
                        text = "Download failed",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color(0xFFE91E63),
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)
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
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = if (isDownloaded) "Available offline" else "Download for offline",
                            tint = if (isDownloaded) Color(0xFFB7F397) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview function for SearchScreen
 */
@Composable
fun SearchScreenPreview() {
    SearchScreen(
        searchQuery = "Rick Astley",
        onSearchQueryChange = {},
        suggestions = listOf("Rick Astley", "Rick Roll", "Never Gonna Give You Up"),
        searchResults = listOf(
            Track(
                videoId = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                uploader = "Rick Astley",
                durationMs = 212000,
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
            )
        ),
        isLoading = false,
        onTrackClick = {},
        onAddToFavorites = {}
    )
}
