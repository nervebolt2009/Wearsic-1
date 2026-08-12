package com.wearsic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ResponsiveTimeText
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.wearsic.app.R

/**
 * Compact back button used at the top of secondary screens. Shared by every
 * screen so the style stays identical and the composable is defined once.
 */
@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onBack,
        modifier = modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.85f)
        )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back),
            contentDescription = "Back",
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Horologist-based screen scaffold used by every screen. It renders the app's
 * [ResponsiveTimeText] at the top and a scroll [PositionIndicator] on the right
 * edge whenever a scroll state is supplied, plus the Horologist scroll-away
 * behaviour (the time text glides out while the list scrolls). The shell wraps
 * the whole app in a Horologist [AppScaffold], so the time text is drawn once
 * and shared across screen transitions.
 */
@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearsicScreenScaffold(
    modifier: Modifier = Modifier,
    scrollState: ScrollableState? = null,
    timeText: @Composable () -> Unit = { ResponsiveTimeText() },
    content: @Composable BoxScope.() -> Unit
) {
    ScreenScaffold(
        modifier = modifier,
        timeText = timeText,
        scrollState = scrollState,
        content = content
    )
}

/**
 * A standard Wear OS 5 list row: a Material 3 [Card] with the app's subtle
 * glass surface. Used by every list row across the app so the whole UI shares
 * one look — rounded 18dp cards on the dark theme.
 *
 * When [onClick] is null the card is drawn without a clickable region, so a
 * nested row can own the tap target instead (keeps the merged semantics node
 * small enough that assistive/tap-target centre stays on the row's own text).
 */
@Composable
fun WearsicListCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White.copy(alpha = 0.06f),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = Color.White
            )
        ) {
            content()
        }
    } else {
        // Wear M3 1.5.0 has no non-clickable Card overload, so the visual-only
        // variant is drawn with a plain rounded surface. Callers use this when
        // a nested row owns the tap target (e.g. the search result title row).
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(containerColor)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * Rounded square/circular artwork thumbnail shared by all list rows.
 */
@Composable
fun TrackThumbnail(
    url: String,
    size: Dp = 40.dp,
    shape: Shape = CircleShape
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
        }
    }
}

/**
 * Standard section label (Wear OS 5 [ListHeader]) used above groups of rows.
 */
@Composable
fun ListSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    ListHeader(
        modifier = modifier.fillMaxWidth(0.9f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White.copy(alpha = 0.72f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dismissible inline error banner. Screens render it at the top of their list
 * so playback/search/connection failures are visible where they happen instead
 * of only on the Now Playing screen.
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .background(Color(0xFF8C1D18).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = Color(0xFFFFB4AB),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFFFFB4AB).copy(alpha = 0.8f)
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = stringResource(R.string.dismiss),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
