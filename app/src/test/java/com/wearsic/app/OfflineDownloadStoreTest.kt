package com.wearsic.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wearsic.app.data.cache.OfflineDownloadStore
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
}
