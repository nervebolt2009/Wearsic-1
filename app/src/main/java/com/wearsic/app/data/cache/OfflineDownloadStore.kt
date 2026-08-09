package com.wearsic.app.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * Tiny persistent index of tracks whose complete audio has been written to
 * PlaybackCache. Audio bytes stay in Media3's cache; this file stores only the
 * video ID and expected byte length so an evicted/partial entry is not reported
 * as available offline after a process restart.
 */
object OfflineDownloadStore {
    private const val FILE_NAME = "offline_downloads.txt"
    private const val SEPARATOR = "|"

    @Synchronized
    fun readIds(context: Context): Set<String> = readEntries(context).keys

    @Synchronized
    fun markDownloaded(context: Context, videoId: String, lengthBytes: Long = -1L) {
        if (videoId.isBlank()) return
        val entries = readEntries(context).toMutableMap()
        entries[videoId] = lengthBytes
        writeEntries(context, entries)
    }

    @Synchronized
    fun remove(context: Context, videoId: String) {
        val entries = readEntries(context).toMutableMap()
        if (entries.remove(videoId) != null) writeEntries(context, entries)
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /**
     * Remove markers whose complete cached byte range no longer exists.
     * Older entries without a recorded length are retained when they have a
     * cached span; they are upgraded the next time the track is downloaded.
     */
    @OptIn(UnstableApi::class)
    @Synchronized
    fun reconcile(
        context: Context,
        cache: androidx.media3.datasource.cache.Cache?,
        cacheKey: (String) -> String
    ) {
        if (cache == null) return
        val valid = readEntries(context).filter { (videoId, lengthBytes) ->
            val key = cacheKey(videoId)
            if (lengthBytes > 0L) {
                cache.isCached(key, 0L, lengthBytes)
            } else {
                cache.getCachedSpans(key).isNotEmpty()
            }
        }
        writeEntries(context, valid)
    }

    @Synchronized
    private fun readEntries(context: Context): Map<String, Long> {
        val file = File(context.filesDir, FILE_NAME)
        return runCatching {
            file.readLines()
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .mapNotNull { line ->
                    val parts = line.split(SEPARATOR, limit = 2)
                    val id = parts.firstOrNull()?.trim().orEmpty()
                    if (id.isBlank()) return@mapNotNull null
                    val length = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: -1L
                    id to length
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writeEntries(context: Context, entries: Map<String, Long>) {
        val file = File(context.filesDir, FILE_NAME)
        file.parentFile?.mkdirs()
        file.writeText(
            entries.toSortedMap().entries.joinToString("\n") { (id, length) ->
                "$id$SEPARATOR$length"
            }
        )
    }
}
