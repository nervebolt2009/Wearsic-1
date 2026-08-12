package com.wearsic.app.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.wearsic.app.data.model.Track
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tiny persistent index of tracks whose complete audio has been written to
 * PlaybackCache. Audio bytes stay in Media3's cache; this file stores the
 * video ID and expected byte length so an evicted/partial entry is not reported
 * as available offline after a process restart.
 *
 * A second file keeps the full [Track] metadata (title, artist, thumbnail,
 * duration) for every completed download, which is what the Downloads section
 * renders. Entries without metadata (upgraded from an older release) fall back
 * to a placeholder row so nothing silently disappears.
 */
object OfflineDownloadStore {
    private const val FILE_NAME = "offline_downloads.txt"
    private const val METADATA_FILE = "offline_tracks.json"
    private const val SEPARATOR = "|"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    fun readIds(context: Context): Set<String> = readEntries(context).keys

    /**
     * Record a completed download. When [track] is provided, its metadata is
     * persisted so the Downloads section can show a proper row.
     */
    @Synchronized
    fun markDownloaded(
        context: Context,
        videoId: String,
        lengthBytes: Long = -1L,
        track: Track? = null
    ) {
        if (videoId.isBlank()) return
        val entries = readEntries(context).toMutableMap()
        entries[videoId] = lengthBytes
        writeEntries(context, entries)
        if (track != null && track.videoId == videoId) {
            saveTrackMetadata(context, track)
        }
    }

    /**
     * All currently-downloaded tracks with metadata, newest first. Legacy
     * entries (id only) get a placeholder row so they stay visible; tapping it
     * still plays the cached audio.
     */
    @Synchronized
    fun readTracks(context: Context): List<Track> {
        val validIds = readIds(context)
        val metadata = readTrackMetadata(context)
        val ordered = LinkedHashMap<String, Track>()
        // Newest first, matching the write order of the marker file.
        readEntries(context).keys.toList().asReversed().forEach { id ->
            ordered[id] = metadata[id] ?: placeholderTrack(id)
        }
        return ordered.values.toList()
    }

    private fun placeholderTrack(videoId: String): Track = Track(
        videoId = videoId,
        title = "Downloaded track",
        uploader = "Wearsic",
        durationMs = 0L,
        thumbnailUrl = ""
    )

    @Synchronized
    fun remove(context: Context, videoId: String) {
        val entries = readEntries(context).toMutableMap()
        if (entries.remove(videoId) != null) writeEntries(context, entries)
        val metadata = readTrackMetadata(context).toMutableMap()
        if (metadata.remove(videoId) != null) writeTrackMetadata(context, metadata)
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        File(context.filesDir, METADATA_FILE).delete()
    }

    /**
     * Remove markers whose complete cached byte range no longer exists.
     * Older entries without a recorded length are retained when they have a
     * cached span; they are upgraded the next time the track is downloaded.
     * Metadata for pruned entries is dropped too, so the Downloads list can
     * never show a track whose audio was evicted from the cache.
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
        val metadata = readTrackMetadata(context).filterKeys { it in valid }
        writeTrackMetadata(context, metadata)
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

    private fun saveTrackMetadata(context: Context, track: Track) {
        val metadata = readTrackMetadata(context).toMutableMap()
        metadata[track.videoId] = track
        writeTrackMetadata(context, metadata)
    }

    @Synchronized
    private fun readTrackMetadata(context: Context): Map<String, Track> {
        val file = File(context.filesDir, METADATA_FILE)
        if (!file.exists()) return emptyMap()
        return runCatching {
            json.decodeFromString<List<Track>>(file.readText())
                .filter { it.videoId.isNotBlank() }
                .associateBy { it.videoId }
        }.getOrDefault(emptyMap())
    }

    private fun writeTrackMetadata(context: Context, metadata: Map<String, Track>) {
        val file = File(context.filesDir, METADATA_FILE)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(metadata.values.toList()))
    }
}
