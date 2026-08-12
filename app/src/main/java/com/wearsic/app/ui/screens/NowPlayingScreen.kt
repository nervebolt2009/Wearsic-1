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
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.IconButtonShapes
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.media.ui.components.PlayPauseProgressButton
import com.google.android.horologist.media.ui.components.controls.MediaButtonDefaults
import com.google.android.horologist.media.ui.components.controls.SeekToNextButton
import com.google.android.horologist.media.ui.components.controls.SeekToPreviousButton
import com.google.android.horologist.media.ui.state.model.TrackPositionUiModel
import com.wearsic.app.R
import com.wearsic.app.data.model.Track
import com.wearsic.app.service.PlaybackEvents
import com.wearsic.app.ui.components.WearsicScreenScaffold
import com.wearsic.app.ui.navigation.Screen
import kotlin.time.Duration.Companion.milliseconds

private val Accent = Color(0xFFB7F397)

/**
 * Now Playing, modelled on Google's Wear OS media player: full-bleed album
 * artwork behind a scrim, the title and artist at the top, a dominant circular
 * play/pause button wrapped in a progress ring with next beside it in the
 * middle, and two pill-shaped actions (favorite + queue) at the bottom. A
 * compact secondary row keeps shuffle/repeat and the screen navigation
 * reachable without cluttering the player.
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
        // Scrollable so screen-reader navigation can still reach every control;
        // on a full watch face the player item fills the viewport.
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
            item {
                Box(modifier = Modifier.fillParentMaxHeight()) {
                    // Full-bleed album artwork.
                    if (currentTrack != null) {
                        AsyncImage(
                            model = currentTrack.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Scrim keeps the text and controls readable on any artwork,
                    // and doubles as the empty-state background.
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

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))

                        // Title + artist (or the error/empty state) up top.
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Text(
                                text = currentTrack?.uploader.orEmpty()
                                    .ifBlank { "Choose a song to start listening" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp, start = 20.dp, end = 20.dp)
                            )
                        }

                        // Controls sit mid-screen, like Google's player.
                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SeekToPreviousButton(
                                onClick = onPrevious,
                                enabled = currentTrack != null,
                                modifier = Modifier.size(34.dp),
                                iconSize = 20.dp,
                                colors = MediaButtonDefaults.mediaButtonDefaultColors
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            PlayPauseProgressButton(
                                onPlayClick = onPlayPause,
                                onPauseClick = onPlayPause,
                                playing = isPlaying,
                                trackPositionUiModel = rememberPositionModel(
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaying
                                ),
                                modifier = Modifier.size(62.dp),
                                iconSize = 30.dp,
                                progressStrokeWidth = 4.dp,
                                progressColor = Accent,
                                trackColor = Color.White.copy(alpha = 0.14f),
                                backgroundColor = Color.White.copy(alpha = 0.16f),
                                colors = ButtonDefaults.iconButtonColors(contentColor = Color.White)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            SeekToNextButton(
                                onClick = onNext,
                                enabled = currentTrack != null,
                                modifier = Modifier.size(46.dp),
                                iconSize = 26.dp,
                                colors = MediaButtonDefaults.mediaButtonDefaultColors
                            )
                        }

                        // The two pill actions at the bottom, like the reference.
                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillButton(
                                icon = if (isFavorite) R.drawable.ic_favorite_filled
                                else R.drawable.ic_favorite_outline,
                                description = if (isFavorite) stringResource(R.string.remove_from_favorites)
                                else stringResource(R.string.add_to_favorites),
                                selected = isFavorite,
                                onClick = onFavoriteToggle
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            PillButton(
                                icon = R.drawable.ic_queue,
                                description = stringResource(R.string.queue),
                                selected = false,
                                onClick = { onNavigate(Screen.Queue) }
                            )
                        }

                        // Compact secondary row: playback toggles + navigation.
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallToggle(
                                icon = R.drawable.ic_shuffle,
                                description = stringResource(R.string.shuffle),
                                selected = shuffleEnabled,
                                onClick = onShuffleToggle
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            SmallToggle(
                                icon = R.drawable.ic_repeat,
                                description = stringResource(R.string.repeat),
                                selected = repeatEnabled,
                                onClick = onRepeatToggle
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            SmallNav(
                                icon = R.drawable.ic_search,
                                label = stringResource(R.string.search),
                                onClick = { onNavigate(Screen.Search) }
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            SmallNav(
                                icon = R.drawable.ic_favorite_outline,
                                label = stringResource(R.string.favorites),
                                onClick = { onNavigate(Screen.Favorites) }
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            SmallNav(
                                icon = R.drawable.ic_settings,
                                label = stringResource(R.string.settings),
                                onClick = { onNavigate(Screen.Settings) }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Single 1Hz playback clock shared by the progress ring. Advances a local
 * position every second while playing and resyncs to the service position
 * whenever the service reports one (initial load, seek, pause, track change —
 * including back to 0 for a repeated track, where the audio restarts but the
 * id does not change). Only the small ring subtree recomposes on each tick.
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
 * Builds the Horologist ring model for the play button from the shared 1Hz
 * clock. When the stream reports a duration that wins; otherwise the track's
 * own duration is used as the fallback.
 */
@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun rememberPositionModel(
    currentTrack: Track?,
    isPlaying: Boolean
): TrackPositionUiModel {
    val durationMs by PlaybackEvents.durationMs.collectAsState()
    val displayedPositionMs by rememberTickingPositionMs(
        videoId = currentTrack?.videoId,
        isPlaying = isPlaying,
        fallbackDurationMs = currentTrack?.durationMs ?: 0L
    )

    val effectiveDuration = if (durationMs > 0L) durationMs else currentTrack?.durationMs ?: 0L
    if (currentTrack == null || effectiveDuration <= 0L) return TrackPositionUiModel.Hidden
    val percent = (displayedPositionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    return TrackPositionUiModel.Actual(
        percent = percent,
        duration = effectiveDuration.milliseconds,
        position = displayedPositionMs.milliseconds
    )
}

/**
 * The reference's bottom pill buttons: rounded-rectangle icon actions, the
 * favorite pill tinted when selected.
 */
@Composable
private fun PillButton(
    icon: Int,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(width = 46.dp, height = 42.dp),
        shapes = IconButtonShapes(
            shape = RoundedCornerShape(percent = 50),
            pressedShape = RoundedCornerShape(percent = 50)
        ),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) Accent.copy(alpha = 0.22f)
            else Color.White.copy(alpha = 0.14f),
            contentColor = if (selected) Accent else Color.White.copy(alpha = 0.92f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Compact shuffle/repeat toggle in the secondary row.
 */
@Composable
private fun SmallToggle(
    icon: Int,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            contentColor = if (selected) Accent else Color.White.copy(alpha = 0.6f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Compact navigation icon in the secondary row.
 */
@Composable
private fun SmallNav(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.6f)
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(16.dp)
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
