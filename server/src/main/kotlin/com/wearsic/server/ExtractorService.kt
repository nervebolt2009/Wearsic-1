package com.wearsic.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

class ExtractorService {
    private val searchCache = BoundedCache<String, List<TrackDto>>(64)
    private val streamCache = BoundedCache<String, CachedStreamTarget>(64)
    private val locks = ConcurrentHashMap<String, Any>()

    suspend fun search(query: String): List<TrackDto> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.length >= 2) { "Query must contain at least two characters" }
        val key = normalized.lowercase()
        searchCache[key] ?: synchronized(lockFor("search:$key")) {
            searchCache[key] ?: run {
                val extractor = ServiceList.YouTube.getSearchExtractor(normalized)
                extractor.fetchPage()
                extractor.getInitialPage().getItems()
                    .asSequence()
                    .mapNotNull(::toTrack)
                    .take(MAX_RESULTS)
                    .toList()
                    .also { searchCache[key] = it }
            }
        }
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList()
        else ServiceList.YouTube.getSuggestionExtractor().suggestionList(query.trim()).take(MAX_SUGGESTIONS)
    }

    suspend fun related(videoId: String): List<TrackDto> = withContext(Dispatchers.IO) {
        val collector = streamExtractor(videoId).getRelatedItems()
        collector?.getItems().orEmpty().asSequence().mapNotNull(::toTrack).take(MAX_RESULTS).toList()
    }

    suspend fun streamTarget(videoId: String): StreamTarget = withContext(Dispatchers.IO) {
        streamCache[videoId]
            ?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }
            ?.target
            ?: synchronized(lockFor("stream:$videoId")) {
                streamCache[videoId]
                    ?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }
                    ?.target
                    ?: run {
                val audio = streamExtractor(videoId).getAudioStreams()
                    .asSequence()
                    .filter { it.isUrl() }
                    .sortedWith(compareBy<AudioStream> {
                        if (it.getFormat()?.getMimeType() == "audio/mp4") 0 else 1
                    }.thenBy { bitrateDistance(it.getAverageBitrate()) })
                    .firstOrNull() ?: error("No playable audio stream found")
                StreamTarget(
                    url = audio.getContent(),
                    contentType = audio.getFormat()?.getMimeType() ?: "audio/mp4"
                ).also {
                    streamCache[videoId] = CachedStreamTarget(
                        target = it,
                        expiresAtMillis = System.currentTimeMillis() + STREAM_CACHE_TTL_MILLIS
                    )
                }
            }
        }
    }

    suspend fun playlist(url: String): PlaylistWithTracksDto = withContext(Dispatchers.IO) {
        val extractor: PlaylistExtractor = ServiceList.YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()
        PlaylistWithTracksDto(
            id = extractor.getId(),
            name = extractor.getName(),
            tracks = extractor.getInitialPage().getItems().asSequence()
                .mapNotNull(::toTrack).take(MAX_RESULTS).toList()
        )
    }

    suspend fun channel(url: String): PlaylistWithTracksDto = withContext(Dispatchers.IO) {
        val extractor: ChannelExtractor = ServiceList.YouTube.getChannelExtractor(url)
        extractor.fetchPage()
        PlaylistWithTracksDto(
            id = extractor.getId(),
            name = extractor.getName(),
            tracks = extractor.getTabs().firstOrNull()?.let { tab ->
                val tabExtractor = ServiceList.YouTube.getChannelTabExtractor(tab)
                tabExtractor.fetchPage()
                tabExtractor.getInitialPage().getItems().asSequence()
                    .mapNotNull(::toTrack).take(MAX_RESULTS).toList()
            }.orEmpty()
        )
    }

    private fun streamExtractor(videoId: String): StreamExtractor =
        ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId").also { it.fetchPage() }

    private fun lockFor(key: String): Any = locks.computeIfAbsent(key) { Any() }

    private fun toTrack(item: InfoItem): TrackDto? {
        if (item !is StreamInfoItem) return null
        val url = item.getUrl()
        return TrackDto(
            videoId = videoIdFromUrl(url) ?: return null,
            title = item.getName(),
            uploader = item.getUploaderName().orEmpty().ifBlank { "Unknown artist" },
            durationMs = item.getDuration().coerceAtLeast(0) * 1000,
            thumbnailUrl = item.getThumbnails().firstOrNull()?.getUrl().orEmpty()
        )
    }

    private fun videoIdFromUrl(url: String): String? = when {
        "v=" in url -> url.substringAfter("v=").substringBefore('&')
        "youtu.be/" in url -> url.substringAfter("youtu.be/").substringBefore('?')
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun bitrateDistance(value: Int): Int = if (value < 0) 10_000 else kotlin.math.abs(value - 128)

    companion object {
        const val MAX_RESULTS = 10
        const val MAX_SUGGESTIONS = 5
        private const val STREAM_CACHE_TTL_MILLIS = 15 * 60 * 1000L
    }
}

data class CachedStreamTarget(val target: StreamTarget, val expiresAtMillis: Long)

data class StreamTarget(val url: String, val contentType: String)

private class BoundedCache<K, V>(private val maxSize: Int) {
    private val values = Collections.synchronizedMap(object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    })

    operator fun get(key: K): V? = values[key]
    operator fun set(key: K, value: V) { values[key] = value }
}
