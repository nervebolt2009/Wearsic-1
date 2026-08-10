package com.wearsic.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.wearsic.app.data.model.Playlist
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.screens.*
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RobolectricScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val track = Track(
        videoId = "screenshot-track",
        title = "A Very Long Coverage Song Title",
        uploader = "Coverage Artist",
        durationMs = 212_000,
        thumbnailUrl = ""
    )

    @Composable
    private fun WatchRoot(content: @Composable BoxScope.() -> Unit) {
        Box(modifier = Modifier.size(width = 174.dp, height = 400.dp), content = content)
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun nowPlaying_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    NowPlayingScreen(
                        currentTrack = track,
                        isPlaying = true,
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
            }
        }
        capture("now-playing-watch44mm")
    }

    @Test
    fun search_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    SearchScreen(
                        searchQuery = "",
                        onSearchQueryChange = {},
                        suggestions = emptyList(),
                        searchResults = listOf(track),
                        isLoading = false,
                        onTrackClick = {},
                        onAddToFavorites = {}
                    )
                }
            }
        }
        capture("search-watch44mm")
    }

    @Test
    fun favorites_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    FavoritesPlaylistsScreen(
                        favorites = listOf(track),
                        playlists = listOf(Playlist("playlist", "Coverage Mix", 4, null)),
                        isLoading = false,
                        onTrackClick = {},
                        onRemoveFromFavorites = {},
                        onPlaylistClick = {}
                    )
                }
            }
        }
        capture("favorites-watch44mm")
    }

    @Test
    fun playlists_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    FavoritesPlaylistsScreen(
                        favorites = emptyList(),
                        playlists = listOf(Playlist("playlist", "Coverage Mix", 4, null)),
                        isLoading = false,
                        onTrackClick = {},
                        onRemoveFromFavorites = {},
                        onPlaylistClick = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Playlists").performClick()
        capture("playlists-watch44mm")
    }

    @Test
    fun appShell_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.wearsic.app.WearsicApp()
                    }
                }
            }
        }
        capture("app-shell-watch44mm")
    }

    @Test
    fun queue_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    QueueScreen(
                        currentTrack = track,
                        queue = listOf(track),
                        currentIndex = 0,
                        onTrackClick = {},
                        onRemoveFromQueue = {},
                        onClearQueue = {}
                    )
                }
            }
        }
        capture("queue-watch44mm")
    }

    @Test
    fun settings_watch44mm() {
        composeTestRule.setContent {
            WearsicTheme {
                WatchRoot {
                    SettingsScreen(
                        serverUrl = "https://example.com/wearsic",
                        onServerUrlChange = {},
                        onTestConnection = {},
                        isConnected = true,
                        isLoading = false
                    )
                }
            }
        }
        capture("settings-watch44mm")
    }
}
