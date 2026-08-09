package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.wearsic.app.R
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.navigation.Screen

private val Accent = Color(0xFFB7F397)

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

@Composable
fun NowPlayingScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    playbackError: String? = null,
    progress: Float,
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavorite: Boolean,
    onNavigate: (Screen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ScreenScaffold(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Keep the watch GPU cool: use a static gradient instead of a
            // full-screen blur. Artwork is decoded only once below.

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (currentTrack != null) {
                                listOf(Color(0xFF202A32), Color(0xFF0C0D10), Color(0xFF080909))
                            } else {
                                listOf(Color(0xFF15171B), Color(0xFF080909))
                            }
                        )
                    )
            )
        val listState = rememberScalingLazyListState()
        val rotaryBehavior = RotaryScrollableDefaults.behavior(listState)
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = rotaryBehavior,
            contentPadding = PaddingValues(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2A2E36), Color(0xFF15171B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentTrack != null) {
                            AsyncImage(
                                model = currentTrack.thumbnailUrl,
                                contentDescription = currentTrack.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(28.dp))
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_play),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (!playbackError.isNullOrBlank()) {
                        Text(
                            text = playbackError,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB4AB),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = currentTrack?.title ?: stringResource(R.string.no_tracks),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = currentTrack?.uploader.orEmpty().ifBlank { "Choose a song to start listening" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                    )

                    // In-screen navigation: reach the other screens from here
                    // instead of a permanent bottom bar that eats the screen.
                    Spacer(modifier = Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(0.82f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniNavButton(R.drawable.ic_search, "Search", { onNavigate(Screen.Search) })
                        MiniNavButton(R.drawable.ic_favorite_outline, "Favorites", { onNavigate(Screen.Favorites) })
                        MiniNavButton(R.drawable.ic_queue, "Queue", { onNavigate(Screen.Queue) })
                        MiniNavButton(R.drawable.ic_settings, "Settings", { onNavigate(Screen.Settings) })
                    }

                    Spacer(modifier = Modifier.height(9.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        if (currentTrack != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime((progress * currentTrack.durationMs).toLong()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                                Text(
                                    text = formatTime(currentTrack.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    val playDescription = if (isPlaying) {
                        stringResource(R.string.pause)
                    } else {
                        stringResource(R.string.play)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallControl(R.drawable.ic_skip_previous, stringResource(R.string.skip_previous), false, onPrevious)
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White, CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF121417)
                            )
                        ) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = playDescription,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        SmallControl(R.drawable.ic_skip_next, stringResource(R.string.skip_next), false, onNext)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallControl(R.drawable.ic_shuffle, stringResource(R.string.shuffle), shuffleEnabled, onShuffleToggle)
                        SmallControl(R.drawable.ic_repeat, stringResource(R.string.repeat), repeatEnabled, onRepeatToggle)
                        SmallControl(
                            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline,
                            if (isFavorite) stringResource(R.string.remove_from_favorites)
                            else stringResource(R.string.add_to_favorites),
                            isFavorite,
                            onFavoriteToggle
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MiniNavButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White.copy(alpha = 0.9f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SmallControl(
    icon: Int,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) Accent else Color.White.copy(alpha = 0.62f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
fun NowPlayingScreenPreview() {
    NowPlayingScreen(
        currentTrack = Track(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            uploader = "Rick Astley",
            durationMs = 212000,
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        ),
        isPlaying = true,
        playbackError = null,
        progress = 0.45f,
        shuffleEnabled = false,
        repeatEnabled = true,
        onPlayPause = {},
        onNext = {},
        onPrevious = {},
        onShuffleToggle = {},
        onRepeatToggle = {},
        onFavoriteToggle = {},
        isFavorite = false
    )
}
