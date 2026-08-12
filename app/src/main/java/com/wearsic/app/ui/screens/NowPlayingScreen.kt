package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material.ToggleButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.media.ui.components.MediaControlButtons
import com.google.android.horologist.media.ui.components.controls.MediaButtonDefaults
import com.google.android.horologist.media.ui.components.controls.ShuffleToggleButton
import com.google.android.horologist.media.ui.screens.player.PlayerScreen
import com.google.android.horologist.media.ui.state.model.TrackPositionUiModel
import com.wearsic.app.R
import com.wearsic.app.data.model.Track
import com.wearsic.app.service.PlaybackEvents
import com.wearsic.app.service.progressFraction
import com.wearsic.app.ui.components.WearsicScreenScaffold
import com.wearsic.app.ui.navigation.Screen
import com.wearsic.app.ui.theme.WearsicAccent
import kotlin.time.Duration.Companion.milliseconds

private val Accent = Color(0xFFB7F397)

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/**
 * Now Playing, structured as the Horologist [PlayerScreen]: a full-bleed album
 * artwork background, track info in the top section, the media controls in the
 * middle and the toggles + navigation in the bottom section — the standard Wear
 * OS media player layout used by Google's own media apps.
 */
@OptIn(ExperimentalHorologistApi::class)
@Composable
fun NowPlayingScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    playbackError: String? = null,
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    isFavorite: Boolean,
    onRetry: () -> Unit = {},
    onNavigate: (Screen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Ambient (always-on) mode is handled app-wide in the shell: every screen
    // is replaced by a single low-power monochrome overlay.
    WearsicScreenScaffold(modifier = modifier) {
        // Scrollable so small displays (and screen-reader navigation) can still
        // reach every control; on a full watch face the player fills it.
        val listState = rememberScalingLazyListState()
        val rotaryBehavior = RotaryScrollableDefaults.behavior(listState)
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = rotaryBehavior,
            // No content padding: the player item fills the viewport via
            // fillParentMaxHeight, so the full face goes to the artwork.
            contentPadding = PaddingValues(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // fillParentMaxHeight gives the ConstraintLayout-based PlayerScreen a
            // bounded height inside the lazy column; otherwise its fillToConstraints
            // sections (title + bottom buttons) collapse to zero height.
            item {
                PlayerScreen(
                    modifier = Modifier.fillParentMaxHeight(),
                    mediaDisplay = {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!playbackError.isNullOrBlank()) {
                                Text(
                                    text = playbackError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFB4AB),
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = onRetry) {
                                    Text(
                                        text = stringResource(R.string.retry),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Accent
                                    )
                                }
                            } else {
                                Text(
                                    text = currentTrack?.title ?: stringResource(R.string.no_tracks),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        shadow = Shadow(Color.Black.copy(alpha = 0.85f), blurRadius = 8f)
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTrack?.uploader.orEmpty()
                                        .ifBlank { "Choose a song to start listening" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    },
                    controlButtons = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Linear progress + times, isolated from the rest of
                            // the player (only this subtree ticks every second).
                            PlaybackProgressSection(currentTrack = currentTrack, isPlaying = isPlaying)
                            Spacer(modifier = Modifier.height(4.dp))
                            HorologistMediaControls(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onPlayPause = onPlayPause,
                                onPrevious = onPrevious,
                                onNext = onNext
                            )
                        }
                    },
                    buttons = {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.width(160.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShuffleToggleButton(
                                    shuffleOn = shuffleEnabled,
                                    onToggle = { onShuffleToggle() },
                                    modifier = Modifier.size(40.dp),
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        checkedBackgroundColor = Color.Transparent,
                                        checkedContentColor = WearsicAccent,
                                        uncheckedBackgroundColor = Color.Transparent,
                                        uncheckedContentColor = Color.White.copy(alpha = 0.62f)
                                    )
                                )
                                SmallControl(
                                    R.drawable.ic_repeat,
                                    stringResource(R.string.repeat),
                                    repeatEnabled,
                                    onRepeatToggle
                                )
                                SmallControl(
                                    if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline,
                                    if (isFavorite) stringResource(R.string.remove_from_favorites)
                                    else stringResource(R.string.add_to_favorites),
                                    isFavorite,
                                    onFavoriteToggle
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.width(160.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiniNavButton(R.drawable.ic_search, "Search", { onNavigate(Screen.Search) })
                                MiniNavButton(R.drawable.ic_favorite_outline, "Favorites", { onNavigate(Screen.Favorites) })
                                MiniNavButton(R.drawable.ic_queue, "Queue", { onNavigate(Screen.Queue) })
                                MiniNavButton(R.drawable.ic_settings, "Settings", { onNavigate(Screen.Settings) })
                            }
                        }
                    },
                    background = {
                        if (currentTrack != null) {
                            AsyncImage(
                                model = currentTrack.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Scrim keeps the text and controls readable on any
                        // artwork, and doubles as the empty-state background.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color(0x99080A0C),
                                        0.45f to Color(0x66080909),
                                        1f to Color(0xE6080909)
                                    )
                                )
                        )
                    }
                )
            }
        }
    }
}

/**
 * Single 1Hz playback clock shared by the linear progress bar and the
 * Horologist progress ring. Advances a local position every second while
 * playing and resyncs to the service position whenever the service reports one
 * (initial load, seek, pause, track change — including back to 0 for a
 * repeated track, where the audio restarts but the id does not change). Both
 * callers run it inside their own isolated subtree, so a tick only recomposes
 * the small progress UI.
 */
@Composable
private fun rememberTickingPositionMs(
    videoId: String?,
    isPlaying: Boolean,
    fallbackDurationMs: Long = 0L
): State<Long> {
    val servicePositionMs by PlaybackEvents.positionMs.collectAsState()
    val serviceDurationMs by PlaybackEvents.durationMs.collectAsState()

    var displayedPositionMs by remember(videoId) { mutableStateOf(0L) }

    // Resync to the real stream position whenever the service reports one.
    LaunchedEffect(servicePositionMs) {
        displayedPositionMs = servicePositionMs
    }

    // Self-driving 1Hz tick while playing; freezes the moment playback stops.
    LaunchedEffect(isPlaying, videoId) {
        if (isPlaying) {
            while (true) {
                delay(1_000)
                val duration = if (serviceDurationMs > 0L) serviceDurationMs else fallbackDurationMs
                displayedPositionMs = if (duration > 0L) {
                    (displayedPositionMs + 1_000L).coerceAtMost(duration)
                } else {
                    displayedPositionMs + 1_000L
                }
            }
        }
    }

    return derivedStateOf { displayedPositionMs }
}

/**
 * Horologist [MediaControlButtons] with the play/pause button wrapped in a
 * circular progress ring driven by the shared playback clock. Only this small
 * subtree recomposes on each tick.
 */
@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun HorologistMediaControls(
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val durationMs by PlaybackEvents.durationMs.collectAsState()
    val displayedPositionMs by rememberTickingPositionMs(
        videoId = currentTrack?.videoId,
        isPlaying = isPlaying,
        fallbackDurationMs = currentTrack?.durationMs ?: 0L
    )

    val effectiveDuration = if (durationMs > 0L) durationMs else currentTrack?.durationMs ?: 0L
    val percent = if (effectiveDuration > 0L) {
        (displayedPositionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val positionModel = if (currentTrack != null) {
        TrackPositionUiModel.Actual(
            percent = percent,
            duration = effectiveDuration.milliseconds,
            position = displayedPositionMs.milliseconds
        )
    } else {
        TrackPositionUiModel.Hidden
    }

    MediaControlButtons(
        onPlayButtonClick = onPlayPause,
        onPauseButtonClick = onPlayPause,
        playPauseButtonEnabled = true,
        playing = isPlaying,
        onSeekToPreviousButtonClick = onPrevious,
        seekToPreviousButtonEnabled = currentTrack != null,
        onSeekToNextButtonClick = onNext,
        seekToNextButtonEnabled = currentTrack != null,
        modifier = Modifier.fillMaxWidth(0.94f),
        trackPositionUiModel = positionModel,
        colors = MediaButtonDefaults.mediaButtonDefaultColors
    )
}

@Composable
private fun PlaybackProgressSection(currentTrack: Track?, isPlaying: Boolean) {
    val durationMs by PlaybackEvents.durationMs.collectAsState()
    // The bar runs on the same 1Hz clock as the Horologist progress ring (kept
    // in sync by construction), resynced to the service position whenever it
    // reports one. Even if a service poll is delayed or the stream reports no
    // duration, the indicator still moves smoothly.
    val displayedPositionMs by rememberTickingPositionMs(
        videoId = currentTrack?.videoId,
        isPlaying = isPlaying,
        fallbackDurationMs = currentTrack?.durationMs ?: 0L
    )

    val fraction = progressFraction(displayedPositionMs, durationMs, currentTrack?.durationMs ?: 0L)
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        if (currentTrack != null) {
            // Left label shows time remaining (Spotify-style "-M:SS"); the
            // total duration stays on the right for context.
            val effectiveDuration = if (durationMs > 0L) durationMs else currentTrack.durationMs
            val effectivePosition = if (durationMs > 0L) {
                displayedPositionMs
            } else {
                (fraction * currentTrack.durationMs).toLong()
            }
            val remainingMs = (effectiveDuration - effectivePosition).coerceAtLeast(0L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-${formatTime(remainingMs)}",
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
}

@Composable
private fun MiniNavButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White.copy(alpha = 0.92f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(19.dp)
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
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
            contentColor = if (selected) Accent else Color.White.copy(alpha = 0.72f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(20.dp)
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
