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
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Keep the pill and its touch targets inside the curved bottom safe area
            // on round 44mm watches; the outer padding is transparent, not clipped.
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 18.dp)
            .background(
                color = Color(0xFF17191D).copy(alpha = 0.96f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
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
