package com.wearsic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.material3.Icon
import com.wearsic.app.R
import com.wearsic.app.ui.navigation.Screen

/**
 * Bottom navigation bar for Wear OS
 * Shows icons for main navigation destinations
 */
@Composable
fun BottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    // Real-device geometry (Galaxy Watch 7 44mm): 480x480 px @ 327 ppi is a
    // ~235dp ROUND display, so every dimension here is tuned for that, not for
    // a 480dp square. The bottom arc is narrow, so the pill must be both
    // narrower than the screen and lifted high enough that its corners stay
    // inside the circle:
    //   pill 172dp wide -> half 86dp; at 42dp above the bottom edge the
    //   distance from the circle center is sqrt(86^2 + (117-42)^2) ~= 109dp,
    //   comfortably inside the 117.5dp radius.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 42.dp)
    ) {
        val pillWidth = (maxWidth * 0.72f).coerceAtMost(172.dp)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Five targets share the pill; on the real ~235dp screen each
                // gets ~33dp of width (48dp tall). That is the widest that fits
                // the round bezel without clipping the bar's ends.
                .width(pillWidth)
                .height(48.dp)
                .testTag("bottom-navigation-pill")
                .background(
                    color = Color(0xFF17191D).copy(alpha = 0.96f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Now Playing
        NavigationItem(
            icon = R.drawable.ic_play,
            label = "Now Playing",
            isSelected = currentScreen is Screen.NowPlaying,
            onClick = { onNavigate(Screen.NowPlaying) }
        )
        
        // Search
        NavigationItem(
            icon = R.drawable.ic_search,
            label = "Search",
            isSelected = currentScreen is Screen.Search,
            onClick = { onNavigate(Screen.Search) }
        )
        
        // Favorites
        NavigationItem(
            icon = R.drawable.ic_favorite_outline,
            label = "Favorites",
            isSelected = currentScreen is Screen.Favorites,
            onClick = { onNavigate(Screen.Favorites) }
        )
        
        // Queue
        NavigationItem(
            icon = R.drawable.ic_queue,
            label = "Queue",
            isSelected = currentScreen is Screen.Queue,
            onClick = { onNavigate(Screen.Queue) }
        )
        
        // Settings
        NavigationItem(
            icon = R.drawable.ic_settings,
            label = "Settings",
            isSelected = currentScreen is Screen.Settings,
            onClick = { onNavigate(Screen.Settings) }
        )
        }
    }
}

/**
 * Individual navigation item
 */
@Composable
private fun RowScope.NavigationItem(
    icon: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .weight(1f)
            .height(48.dp)
            .padding(horizontal = 1.dp)
            .background(
                color = if (isSelected) Color(0xFFB7F397).copy(alpha = 0.22f) else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            tint = if (isSelected) Color(0xFFB7F397) else Color.White.copy(alpha = 0.56f),
            modifier = Modifier.size(20.dp)
        )
    }
}
