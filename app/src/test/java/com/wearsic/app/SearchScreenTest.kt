package com.wearsic.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.data.model.Album
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.screens.SearchScreen
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric coverage for track and real remote-album search modes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testTrack = Track("test123", "Test Song", "Test Artist", 180000, "")

    @Test
    fun testSearchScreenDisplaysHeader() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("", {}, emptyList(), emptyList(), false, {}, {})
            }
        }
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test
    fun testSearchScreenDisplaysSuggestions() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("Rick", {}, listOf("Rick Astley", "Rick Roll"), emptyList(), false, {}, {})
            }
        }
        composeTestRule.onNodeWithText("Rick Astley").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Rick Roll").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testSearchScreenDisplaysResults() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("Test", {}, emptyList(), listOf(testTrack), false, {}, {})
            }
        }
        composeTestRule.onNodeWithText("Test Song").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Artist").assertIsDisplayed()
    }

    @Test
    fun testSearchScreenDisplaysEmptyAndLoadingStates() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("Test", {}, emptyList(), emptyList(), true, {}, {})
            }
        }
        composeTestRule.onRoot().assertExists()
    }

    @Test
    fun testSearchScreenDisplaysEmptyQuery() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("", {}, emptyList(), emptyList(), false, {}, {})
            }
        }
        composeTestRule.onNodeWithText("Tap to search...").assertIsDisplayed()
    }

    @Test
    fun testSearchScreenDisplaysNoResults() {
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("xyz", {}, emptyList(), emptyList(), false, {}, {})
            }
        }
        composeTestRule.onNodeWithText("No results found").assertIsDisplayed()
    }

    @Test
    fun testSearchResultClickable() {
        var clickedTrack: Track? = null
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("Test", {}, emptyList(), listOf(testTrack), false, { clickedTrack = it }, {})
            }
        }
        composeTestRule.onNodeWithText("Test Song").performClick()
        assertEquals(testTrack, clickedTrack)
    }

    @Test
    fun testSuggestionClickable() {
        var selectedSuggestion = ""
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen("Rick", { selectedSuggestion = it }, listOf("Rick Astley"), emptyList(), false, {}, {})
            }
        }
        composeTestRule.onNodeWithText("Rick Astley").performClick()
        assertEquals("Rick Astley", selectedSuggestion)
    }

    @Test
    fun albumsModeShowsRemoteAlbumsAndOpensUrl() {
        val album = Album(
            id = "https://www.youtube.com/playlist?list=PL123",
            name = "Real Album",
            uploader = "Artist",
            trackCount = 10,
            thumbnailUrl = "",
            url = "https://www.youtube.com/playlist?list=PL123"
        )
        var selected: Album? = null
        var mode: Boolean? = null
        composeTestRule.setContent {
            WearsicTheme {
                SearchScreen(
                    searchQuery = "Real Album",
                    onSearchQueryChange = {},
                    suggestions = emptyList(),
                    searchResults = emptyList(),
                    isLoading = false,
                    onTrackClick = {},
                    onAddToFavorites = {},
                    albums = listOf(album),
                    albumsMode = true,
                    onAlbumsModeChange = { mode = it },
                    onAlbumClick = { selected = it }
                )
            }
        }
        composeTestRule.onNodeWithText("Albums / Playlists (1)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open album").performScrollTo().performClick()
        assertEquals(album, selected)
        composeTestRule.onNodeWithText("Tracks").performClick()
        assertEquals(false, mode)
    }
}
