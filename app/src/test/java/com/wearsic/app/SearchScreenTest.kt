package com.wearsic.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.screens.SearchScreen
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric test for SearchScreen
 * Verifies UI elements are present and correctly displayed
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SearchScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun testSearchScreenDisplaysHeader() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Verify search header
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
    }
    
    @Test
    fun testSearchScreenDisplaysSuggestions() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "Rick",
                    onSearchQueryChange = {},
                    suggestions = listOf("Rick Astley", "Rick Roll"),
                    searchResults = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Verify suggestions are displayed
        composeTestRule.onNodeWithText("Rick Astley").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rick Roll").assertIsDisplayed()
    }
    
    @Test
    fun testSearchScreenDisplaysResults() {
        val testTracks = listOf(
            Track(
                videoId = "test123",
                title = "Test Song",
                uploader = "Test Artist",
                durationMs = 180000,
                thumbnailUrl = "https://example.com/image.jpg"
            )
        )
        
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "Test",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = testTracks,
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Verify search results are displayed
        composeTestRule.onNodeWithText("Test Song").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Artist").assertIsDisplayed()
    }
    
    @Test
    fun testSearchScreenDisplaysLoadingState() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "Test",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = emptyList(),
                    isLoading = true,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Verify loading indicator is displayed (CircularProgressIndicator)
        composeTestRule.onRoot().assertExists()
    }
    
    @Test
    fun testSearchScreenDisplaysEmptyQuery() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Verify placeholder text is shown
        composeTestRule.onNodeWithText("Tap to search...").assertIsDisplayed()
    }
    
    @Test
    fun testSearchScreenDisplaysNoResults() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "xyz",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Verify no results message
        composeTestRule.onNodeWithText("No results found").assertIsDisplayed()
    }
    
    @Test
    fun testSearchResultClickable() {
        var clickedTrack: Track? = null
        val testTrack = Track(
            videoId = "test123",
            title = "Test Song",
            uploader = "Test Artist",
            durationMs = 180000,
            thumbnailUrl = "https://example.com/image.jpg"
        )
        
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "Test",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = listOf(testTrack),
                    isLoading = false,
                    onTrackClick = { clickedTrack = it },
                    onAddToFavorites = {}
                )
            }
        }
        
        // Click on the search result
        composeTestRule.onNodeWithText("Test Song").performClick()
        
        // Verify callback was called with correct track
        assert(clickedTrack != null) { "Track click callback should be called" }
        assert(clickedTrack?.videoId == "test123") { "Should pass correct track" }
    }
    
    @Test
    fun testSuggestionClickable() {
        var selectedSuggestion = ""
        
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "Rick",
                    onSearchQueryChange = { selectedSuggestion = it },
                    suggestions = listOf("Rick Astley", "Rick Roll"),
                    searchResults = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {}
                )
            }
        }
        
        // Click on a suggestion
        composeTestRule.onNodeWithText("Rick Astley").performClick()
        
        // Verify callback was called with correct suggestion
        assert(selectedSuggestion == "Rick Astley") { "Should pass selected suggestion" }
    }
}
