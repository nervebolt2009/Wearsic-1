package com.wearsic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.*
import coil.compose.AsyncImage
import com.wearsic.app.R
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.components.BackButton
import com.wearsic.app.ui.components.ListSectionHeader
import com.wearsic.app.ui.components.TrackThumbnail
import com.wearsic.app.ui.components.WearsicListCard
import com.wearsic.app.ui.components.WearsicScreenScaffold
import kotlinx.coroutines.delay

/**
 * Queue screen with up-next list and controls
 */
@Composable
fun QueueScreen(
    currentTrack: Track?,
    queue: List<Track>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveUp: (Int) -> Unit = {},
    onMoveDown: (Int) -> Unit = {},
    onClearQueue: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Clearing the whole queue is destructive: first tap arms the button, a
    // second tap within 3s executes (watch-friendly, no dialog).
    var confirmClearQueue by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClearQueue) {
        if (confirmClearQueue) {
            delay(3_000)
            confirmClearQueue = false
        }
    }
    val listState = rememberScalingLazyListState()
    val rotaryBehavior = RotaryScrollableDefaults.behavior(listState)
    WearsicScreenScaffold(
        modifier = modifier,
        scrollState = listState
    ) {
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
                        text = stringResource(R.string.queue),
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
            
            // Current Track
            if (currentTrack != null) {
                item { ListSectionHeader("Now Playing") }
                
                item {
                    CurrentTrackItem(
                        track = currentTrack,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            }
            
            // Up Next
            if (queue.isNotEmpty()) {
                item { ListSectionHeader("Up Next (${queue.size} tracks)") }
                
                items(queue.size, key = { "$it-${queue[it].videoId}" }) { index ->
                    QueueItem(
                        track = queue[index],
                        index = index,
                        isCurrentTrack = index == currentIndex,
                        onClick = { onTrackClick(index) },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemoveFromQueue(index) },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            } else {
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
                            text = "Queue is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Clear Queue Button
            if (queue.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            if (confirmClearQueue) {
                                confirmClearQueue = false
                                onClearQueue()
                            } else {
                                confirmClearQueue = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (confirmClearQueue) {
                                Color(0xFFE91E63).copy(alpha = 0.35f)
                            } else {
                                Color(0xFFE91E63).copy(alpha = 0.2f)
                            },
                            contentColor = Color(0xFFE91E63)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (confirmClearQueue) {
                                stringResource(R.string.tap_again_to_confirm)
                            } else {
                                "Clear Queue"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Current track item (highlighted)
 */
@Composable
private fun CurrentTrackItem(
    track: Track,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color(0xFF1DB954).copy(alpha = 0.15f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track thumbnail
        TrackThumbnail(url = track.thumbnailUrl)
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Track info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
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
        
        // Duration
        Text(
            text = track.formatDuration(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

/**
 * Queue item with track info and remove action
 */
@Composable
private fun QueueItem(
    track: Track,
    index: Int,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Spotify-style: swipe the row away to remove it from the queue.
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        onDismissed = { onRemove() },
        state = dismissState,
        modifier = modifier
            .testTag("queue_item_$index")
            // Screen readers cannot swipe; expose removal as an accessibility
            // action (also used by UI tests to exercise the callback).
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Remove from queue") {
                        onRemove()
                        true
                    }
                )
            },
        backgroundScrimColor = Color(0xFFE91E63).copy(alpha = 0.25f),
        content = { isBackground ->
        if (isBackground) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = null,
                    tint = Color(0xFFE91E63),
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .size(20.dp)
                )
            }
        } else {
    WearsicListCard(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        containerColor = if (isCurrentTrack) Color(0xFF1DB954).copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Track thumbnail (no number badge: the row also carries 48dp reorder
        // buttons, and every fixed element steals width from the title on a
        // 44mm round face).
        TrackThumbnail(url = track.thumbnailUrl)
        
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
            Text(
                text = track.formatDuration(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp
                ),
                color = Color.White.copy(alpha = 0.45f)
            )
        }
        
        // Move up/down reorder controls (swipe away still removes the row).
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onMoveUp,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = stringResource(R.string.move_up),
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp).rotate(90f)
                )
            }
            IconButton(
                onClick = onMoveDown,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = stringResource(R.string.move_down),
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp).rotate(-90f)
                )
            }
        }
        }
    }
        }
        }
    )
}

/**
 * Preview function for QueueScreen
 */
@Composable
fun QueueScreenPreview() {
    QueueScreen(
        currentTrack = Track(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            uploader = "Rick Astley",
            durationMs = 212000,
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        ),
        queue = listOf(
            Track(
                videoId = "abc123",
                title = "Another Song",
                uploader = "Some Artist",
                durationMs = 180000,
                thumbnailUrl = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
            )
        ),
        currentIndex = 0,
        onTrackClick = {},
        onRemoveFromQueue = {},
        onClearQueue = {}
    )
}
