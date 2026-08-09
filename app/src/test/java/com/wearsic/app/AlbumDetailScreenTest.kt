package com.wearsic.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.data.model.Album
import com.wearsic.app.data.model.Track
import com.wearsic.app.ui.screens.AlbumDetailScreen
import com.wearsic.app.ui.theme.WearsicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AlbumDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val album = Album("id", "Real Album", "Artist", 1, "", "https://youtube.com/playlist?list=id")
    private val track = Track("track", "Album Track", "Artist", 120000, "")

    @Test
    fun displaysLoadedTracksAndRoutesSelection() {
        var selected: Track? = null
        composeTestRule.setContent {
            WearsicTheme {
                AlbumDetailScreen(album, listOf(track), false, onTrackClick = { selected = it })
            }
        }
        composeTestRule.onNodeWithText("Album Track").performScrollTo().performClick()
        assertEquals(track, selected)
    }

    @Test
    fun displaysLoadingState() {
        composeTestRule.setContent {
            WearsicTheme {
                AlbumDetailScreen(album, emptyList(), true, onTrackClick = {})
            }
        }
        composeTestRule.onNodeWithText("Loading album tracks...").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun displaysRetryOnError() {
        var retried = false
        composeTestRule.setContent {
            WearsicTheme {
                AlbumDetailScreen(album, emptyList(), false, "network failed", { retried = true }, {})
            }
        }
        composeTestRule.onNodeWithText("Album could not be loaded").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performScrollTo().performClick()
        assertEquals(true, retried)
    }
}
