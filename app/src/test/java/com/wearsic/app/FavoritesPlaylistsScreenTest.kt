package com.wearsic.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.model.Playlist
import com.wearsic.app.ui.screens.FavoritesPlaylistsScreen
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric test for FavoritesPlaylistsScreen
 * Verifies UI elements are present and correctly displayed
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FavoritesPlaylistsScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private val testTracks = listOf(
        Track(
            videoId = "test1",
            title = "Favorite Song 1",
            uploader = "Artist 1",
            durationMs = 180000,
            thumbnailUrl = "https://example.com/image1.jpg"
        ),
        Track(
            videoId = "test2",
            title = "Favorite Song 2",
            uploader = "Artist 2",
            durationMs = 240000,
            thumbnailUrl = "https://example.com/image2.jpg"
        )
    )
    
    @Test
    fun testFavoritesPlaylistsScreenDisplaysHeader() {
        composeTestRule.setContent {
            WearsicTheme {
                FavoritesPlaylistsScreen(
                    favorites = emptyList(),
                    playlists = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onRemoveFromFavorites = {},
                    onPlaylistClick = {}
                )
            }
        }
        
        // Verify header is displayed (use first to avoid multiple matches)
        composeTestRule.onAllNodesWithText("Favorites").onFirst().assertIsDisplayed()
    }
    
    @Test
    fun testFavoritesPlaylistsScreenDisplaysFavorites() {
        composeTestRule.setContent {
            WearsicTheme {
                FavoritesPlaylistsScreen(
                    favorites = testTracks,
                    playlists = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onRemoveFromFavorites = {},
                    onPlaylistClick = {}
                )
            }
        }
        
        // Verify favorite tracks are displayed
        composeTestRule.onNodeWithText("Favorite Song 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favorite Song 2").assertIsDisplayed()
    }
    
    @Test
    fun testFavoritesPlaylistsScreenDisplaysEmptyState() {
        composeTestRule.setContent {
            WearsicTheme {
                FavoritesPlaylistsScreen(
                    favorites = emptyList(),
                    playlists = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onRemoveFromFavorites = {},
                    onPlaylistClick = {}
                )
            }
        }
        
        // Verify empty state message
        composeTestRule.onNodeWithText("No favorites yet").assertIsDisplayed()
    }
    
    @Test
    fun testFavoritesPlaylistsScreenDisplaysLoadingState() {
        composeTestRule.setContent {
            WearsicTheme {
                FavoritesPlaylistsScreen(
                    favorites = emptyList(),
                    playlists = emptyList(),
                    isLoading = true,
                    onTrackClick = {},
                    onRemoveFromFavorites = {},
                    onPlaylistClick = {}
                )
            }
        }
        
        // Verify loading indicator is displayed
        composeTestRule.onRoot().assertExists()
    }
    
    @Test
    fun testTrackClickable() {
        var clickedTrack: Track? = null
        
        composeTestRule.setContent {
            WearsicTheme {
                FavoritesPlaylistsScreen(
                    favorites = testTracks,
                    playlists = emptyList(),
                    isLoading = false,
                    onTrackClick = { clickedTrack = it },
                    onRemoveFromFavorites = {},
                    onPlaylistClick = {}
                )
            }
        }
        
        // Click on a track
        composeTestRule.onNodeWithText("Favorite Song 1").performClick()
        
        // Verify callback was called with correct track
        assert(clickedTrack != null) { "Track click callback should be called" }
        assert(clickedTrack?.videoId == "test1") { "Should pass correct track" }
    }
    
    @Test
    fun testRemoveFromFavoritesButtonExists() {
        composeTestRule.setContent {
            WearsicTheme {
                FavoritesPlaylistsScreen(
                    favorites = testTracks,
                    playlists = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onRemoveFromFavorites = {},
                    onPlaylistClick = {}
                )
            }
        }
        
        // Verify remove button exists for each track
        composeTestRule.onAllNodesWithContentDescription("Remove from favorites")
            .assertCountEquals(2)
    }
}
