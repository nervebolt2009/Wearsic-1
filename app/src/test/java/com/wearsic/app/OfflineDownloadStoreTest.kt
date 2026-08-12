package com.wearsic.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wearsic.app.data.cache.OfflineDownloadStore
import com.wearsic.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineDownloadStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun marksAndRemovesDownloadedIds() {
        OfflineDownloadStore.clear(context)

        OfflineDownloadStore.markDownloaded(context, "track-a")
        OfflineDownloadStore.markDownloaded(context, "track-b")
        OfflineDownloadStore.markDownloaded(context, "track-a")

        assertEquals(setOf("track-a", "track-b"), OfflineDownloadStore.readIds(context))

        OfflineDownloadStore.remove(context, "track-a")
        assertEquals(setOf("track-b"), OfflineDownloadStore.readIds(context))

        OfflineDownloadStore.clear(context)
        assertTrue(OfflineDownloadStore.readIds(context).isEmpty())
    }

    @Test
    fun ignoresBlankIds() {
        OfflineDownloadStore.clear(context)
        OfflineDownloadStore.markDownloaded(context, " ")
        assertTrue(OfflineDownloadStore.readIds(context).isEmpty())
    }

    @Test
    fun persistsAndReadsTrackMetadata() {
        OfflineDownloadStore.clear(context)
        val track = Track(
            videoId = "meta-track",
            title = "Offline Song",
            uploader = "Offline Artist",
            durationMs = 200_000,
            thumbnailUrl = "https://example.com/art.jpg"
        )

        OfflineDownloadStore.markDownloaded(context, track.videoId, 1234L, track)

        val tracks = OfflineDownloadStore.readTracks(context)
        assertEquals(1, tracks.size)
        assertEquals("Offline Song", tracks.first().title)
        assertEquals("Offline Artist", tracks.first().uploader)
        assertEquals(200_000L, tracks.first().durationMs)

        // Removal drops metadata too, so the Downloads list cannot show ghosts.
        OfflineDownloadStore.remove(context, track.videoId)
        assertTrue(OfflineDownloadStore.readTracks(context).isEmpty())
    }

    @Test
    fun legacyIdOnlyEntriesStillAppearWithPlaceholder() {
        OfflineDownloadStore.clear(context)
        OfflineDownloadStore.markDownloaded(context, "legacy-track")

        val tracks = OfflineDownloadStore.readTracks(context)
        assertEquals(1, tracks.size)
        assertEquals("legacy-track", tracks.first().videoId)
        assertEquals("Downloaded track", tracks.first().title)
    }

    @Test
    fun metadataSurvivesClear() {
        OfflineDownloadStore.clear(context)
        val track = Track("x", "Song", "Artist", 1000, "")
        OfflineDownloadStore.markDownloaded(context, track.videoId, 100L, track)
        OfflineDownloadStore.clear(context)
        assertTrue(OfflineDownloadStore.readTracks(context).isEmpty())
        assertTrue(OfflineDownloadStore.readIds(context).isEmpty())
    }
}
