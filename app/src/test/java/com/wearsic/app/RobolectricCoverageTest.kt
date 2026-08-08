package com.wearsic.app

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.data.model.Playlist
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.components.BottomNavigation
import com.wearsic.app.ui.navigation.NavigationManager
import com.wearsic.app.ui.navigation.Screen
import com.wearsic.app.ui.screens.*
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// Robolectric 4.14.1 supports API 35 in this project; the app itself targets API 36.
@Config(sdk = [35])
class RobolectricCoverageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun WatchRoot(content: @Composable BoxScope.() -> Unit) {
        Box(modifier = Modifier.size(width = 174.dp, height = 400.dp), content = content)
    }

    private val track = Track(
        videoId = "coverage-track",
        title = "Coverage Song",
        uploader = "Coverage Artist",
        durationMs = 180_000,
        thumbnailUrl = ""
    )

    private fun assertInteractiveNodesFitInsideRoot() {
        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val nodes = composeTestRule
            .onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }

        assertTrue("Expected at least one interactive node", nodes.isNotEmpty())
        nodes.forEach { node ->
            val bounds = node.boundsInRoot
            assertTrue("left=${bounds.left}", bounds.left >= rootBounds.left - 1f)
            assertTrue("top=${bounds.top}", bounds.top >= rootBounds.top - 1f)
            assertTrue("right=${bounds.right}", bounds.right <= rootBounds.right + 1f)
            assertTrue("bottom=${bounds.bottom}", bounds.bottom <= rootBounds.bottom + 1f)
        }
    }

    @Test
    fun navigationManagerDoesNotCreateDuplicateHistoryEntries() {
        val navigation = NavigationManager()

        navigation.navigateTo(Screen.Search)
        navigation.navigateTo(Screen.Search)
        navigation.navigateTo(Screen.Settings)

        assertEquals(Screen.Settings, navigation.getCurrentScreen())
        assertEquals(2, navigation.navigationHistory.size)
        assertTrue(navigation.navigateBack())
        assertEquals(Screen.Search, navigation.getCurrentScreen())
        assertTrue(navigation.navigateBack())
        assertEquals(Screen.NowPlaying, navigation.getCurrentScreen())
        assertFalse(navigation.navigateBack())
    }

    @Test
    fun bottomNavigationExposesAllDestinationsAndRoutesClicks() {
        var selected: Screen? = null
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    BottomNavigation(
                        currentScreen = Screen.NowPlaying,
                        onNavigate = { selected = it }
                    )
                }
            }
        }

        listOf("Now Playing", "Search", "Favorites", "Queue", "Settings")
            .forEach { composeTestRule.onNodeWithContentDescription(it).assertExists() }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(Screen.Settings, selected)
        assertInteractiveNodesFitInsideRoot()
    }

    @Test
    fun bottomNavigationFitsRoundWatchWidth() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    BottomNavigation(
                        currentScreen = Screen.NowPlaying,
                        onNavigate = {}
                    )
                }
            }
        }

        composeTestRule.onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                assertTrue("bottom nav item clipped: ${node.boundsInRoot}", node.boundsInRoot.left >= 0f)
                assertTrue("bottom nav item clipped: ${node.boundsInRoot}", node.boundsInRoot.top >= 0f)
                assertTrue("bottom nav item clipped: ${node.boundsInRoot}", node.boundsInRoot.right <= 174.dp.value)
                assertTrue("bottom nav item clipped: ${node.boundsInRoot}", node.boundsInRoot.bottom <= 400.dp.value)
                assertTrue("navigation touch target too small: ${node.boundsInRoot}", node.boundsInRoot.height >= 48.dp.value)
            }
    }

    @Test
    fun nowPlayingInteractiveControlsStayInsideTheRoot() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    NowPlayingScreen(
                    currentTrack = track,
                    isPlaying = false,
                    progress = 0.35f,
                    shuffleEnabled = false,
                    repeatEnabled = false,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onShuffleToggle = {},
                    onRepeatToggle = {},
                    onFavoriteToggle = {},
                    isFavorite = false
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Coverage Song").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").performScrollTo().assertIsDisplayed()
        assertInteractiveNodesFitInsideRoot()
    }

    @Test
    fun searchTextEntryAndResultActionWorkInsideSafeBounds() {
        var changedQuery = ""
        var selectedTrack: Track? = null
        var favoriteTrack: Track? = null
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    SearchScreen(
                    searchQuery = "",
                    onSearchQueryChange = { changedQuery = it },
                    suggestions = listOf("Coverage mix"),
                    searchResults = listOf(track),
                    isLoading = false,
                    onTrackClick = { selectedTrack = it },
                    onAddToFavorites = { favoriteTrack = it }
                    )
                }
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("lofi")
        assertTrue(changedQuery.contains("lofi"))
        composeTestRule.onNodeWithText("Coverage Song").performScrollTo().performClick()
        assertEquals(track, selectedTrack)
        composeTestRule.onNodeWithContentDescription("Add to favorites").performScrollTo().performClick()
        assertEquals(track, favoriteTrack)
        assertInteractiveNodesFitInsideRoot()
    }

    @Test
    fun favoritesPlaylistTabAndRemovalActionWorkInsideSafeBounds() {
        var removed: Track? = null
        var selectedPlaylist: Playlist? = null
        val playlist = Playlist("playlist-1", "Coverage Mix", 3, null)
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    FavoritesPlaylistsScreen(
                    favorites = listOf(track),
                    playlists = listOf(playlist),
                    isLoading = false,
                    onTrackClick = {},
                    onRemoveFromFavorites = { removed = it },
                    onPlaylistClick = { selectedPlaylist = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove from favorites").performClick()
        assertEquals(track, removed)
        composeTestRule.onNodeWithText("Playlists").performClick()
        composeTestRule.onNodeWithText("Coverage Mix").performClick()
        assertEquals(playlist, selectedPlaylist)
        assertInteractiveNodesFitInsideRoot()
    }

    @Test
    fun queueClearAndTrackActionsWorkInsideSafeBounds() {
        var clickedIndex = -1
        var removedIndex = -1
        var cleared = false
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    QueueScreen(
                    currentTrack = null,
                    queue = listOf(track),
                    currentIndex = 0,
                    onTrackClick = { clickedIndex = it },
                    onRemoveFromQueue = { removedIndex = it },
                    onClearQueue = { cleared = true }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Coverage Song").performScrollTo().performClick()
        assertEquals(0, clickedIndex)
        composeTestRule.onNodeWithContentDescription("Remove from queue").performScrollTo().performClick()
        assertEquals(0, removedIndex)
        composeTestRule.onNodeWithText("Clear Queue").performScrollTo().performClick()
        assertTrue(cleared)
        assertInteractiveNodesFitInsideRoot()
    }

    @Test
    fun settingsTextEntryAndConnectionActionWorkInsideSafeBounds() {
        var changedUrl = ""
        var tested = false
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    SettingsScreen(
                    serverUrl = "https://example.com",
                    onServerUrlChange = { changedUrl = it },
                    onTestConnection = { tested = true },
                    isConnected = false,
                    isLoading = false,
                    apiKey = "",
                    onApiKeyChange = {}
                    )
                }
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("/wearsic")
        assertTrue(changedUrl.contains("/wearsic"))
        composeTestRule.onNodeWithText("Test Connection").performScrollTo().performClick()
        assertTrue(tested)
        assertInteractiveNodesFitInsideRoot()
    }

    @Test
    fun completeShellCanRenderWithRobolectricApplicationState() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = com.wearsic.app.ui.viewmodel.MainViewModel(application)
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    com.wearsic.app.WearsicApp(viewModel = viewModel)
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Now Playing").assertExists()
        composeTestRule.onNodeWithText("No tracks found").performScrollTo().assertIsDisplayed()
        viewModel.clearError()
    }
}
