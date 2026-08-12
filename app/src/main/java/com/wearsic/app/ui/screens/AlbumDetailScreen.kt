package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wearsic.app.R
import com.wearsic.app.data.model.Album
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.components.BackButton
import com.wearsic.app.ui.components.WearsicScreenScaffold

@Composable
fun AlbumDetailScreen(
    album: Album,
    tracks: List<Track>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onTrackClick: (Track) -> Unit,
    onDownload: (Track) -> Unit = {},
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    downloadErrors: Map<String, String> = emptyMap(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    WearsicScreenScaffold(
        modifier = modifier,
        scrollState = listState
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(listState),
            contentPadding = PaddingValues(top = 26.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack)
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 40.dp)
                    )
                }
            }
            item {
                Text(
                    text = "${album.uploader} • ${album.trackCount} tracks",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 6.dp)
                )
            }

            if (isLoading) {
                item {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Loading album tracks...", color = Color.White.copy(alpha = 0.65f))
                    }
                }
            } else if (errorMessage != null) {
                item {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Album could not be loaded",
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = errorMessage.take(90),
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            } else if (tracks.isEmpty()) {
                item {
                    Text(
                        text = "No album tracks found",
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(28.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = "Tracks (${tracks.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(0.9f).padding(top = 8.dp, bottom = 6.dp)
                    )
                }
                items(tracks, key = { it.videoId }) { track ->
                    AlbumTrackRow(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onDownload = { onDownload(track) },
                        isDownloaded = track.videoId in downloadedIds,
                        downloadProgress = downloadProgress[track.videoId],
                        downloadError = downloadErrors[track.videoId],
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
                // Continuation-token pagination: long playlists/channels load
                // more tracks on demand from the server.
                if (hasMore) {
                    item {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !isLoadingMore,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(top = 10.dp)
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(R.string.load_more),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    track: Track,
    onClick: () -> Unit,
    onDownload: () -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    downloadError: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(vertical = 3.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.uploader,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = track.formatDuration(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 8.dp)
            )
            if (downloadError != null) {
                Text(
                    text = "Failed",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(0xFFE91E63)
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
        }
    }
}
