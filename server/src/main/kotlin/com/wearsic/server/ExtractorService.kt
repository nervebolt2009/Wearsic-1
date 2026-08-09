package com.wearsic.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

class ExtractorService {
    private val logger = LoggerFactory.getLogger(ExtractorService::class.java)
    private val searchCache = BoundedCache<String, List<TrackDto>>(64)
    private val streamCache = BoundedCache<String, CachedStreamTarget>(64)
    // Pagination sessions for /playlist and /channel: the extractor must stay
    // alive between requests so ?page=N can continue where page N-1 stopped.
    private val playlistSessions = BoundedCache<String, PlaylistSession>(8)
    private val channelSessions = BoundedCache<String, ChannelSession>(8)
    private val locks = ConcurrentHashMap<String, Any>()
    // Stream extraction is guarded by a single lock because the iOS-client
    // fallback toggles a static extractor flag; a global lock keeps concurrent
    // extractions from seeing each other's client state.
    private val streamExtractionLock = Any()

    suspend fun search(query: String): List<TrackDto> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.length >= 2) { "Query must contain at least two characters" }
        val key = normalized.lowercase()
        searchCache[key] ?: synchronized(lockFor("search:$key")) {
            searchCache[key] ?: run {
                searchMusic(normalized).also { searchCache[key] = it }
            }
        }
    }

    /**
     * Music-only search. Prefers YouTube's real music filter ("music_songs",
     * i.e. the YouTube Music / music tab) so results are songs, not random
     * videos, live streams or clips. If the filtered request fails or yields
     * nothing, falls back to a plain search filtered client-side.
     */
    private fun searchMusic(query: String): List<TrackDto> {
        val filtered = try {
            searchPage(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS))
        } catch (error: Exception) {
            logger.info(
                "Music-filtered search failed for '{}', falling back to plain search: {}",
                query,
                error.message
            )
            emptyList()
        }
        if (filtered.isNotEmpty()) return filtered
        return searchPage(query, contentFilters = null)
    }

    private fun searchPage(query: String, contentFilters: List<String>?): List<TrackDto> {
        val extractor = if (contentFilters == null) {
            ServiceList.YouTube.getSearchExtractor(query)
        } else {
            ServiceList.YouTube.getSearchExtractor(query, contentFilters, null)
        }
        extractor.fetchPage()
        return extractor.getInitialPage().getItems()
            .asSequence()
            .mapNotNull(::toTrack)
            .take(MAX_RESULTS)
            .toList()
    }

    suspend fun searchAlbums(query: String): List<AlbumDto> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        require(normalized.length >= 2) { "Query must contain at least two characters" }
        val extractor = ServiceList.YouTube.getSearchExtractor(normalized)
        extractor.fetchPage()
        extractor.getInitialPage().getItems()
            .asSequence()
            .filterIsInstance<PlaylistInfoItem>()
            .map { item ->
                AlbumDto(
                    id = item.getUrl(),
                    name = item.getName(),
                    uploader = item.getUploaderName().orEmpty().ifBlank { "YouTube" },
                    trackCount = item.getStreamCount().coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    thumbnailUrl = item.getThumbnails().firstOrNull()?.getUrl().orEmpty(),
                    url = item.getUrl()
                )
            }
            .take(MAX_RESULTS)
            .toList()
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList()
        else ServiceList.YouTube.getSuggestionExtractor().suggestionList(query.trim()).take(MAX_SUGGESTIONS)
    }

    suspend fun related(videoId: String): List<TrackDto> = withContext(Dispatchers.IO) {
        // Guarded by the same lock as streamTarget: related items build a stream
        // extractor, and the iOS-client fallback toggles a global extractor flag.
        synchronized(streamExtractionLock) {
            val collector = streamExtractor(videoId).getRelatedItems()
            collector?.getItems().orEmpty().asSequence().mapNotNull(::toTrack).take(MAX_RESULTS).toList()
        }
    }

    fun invalidateStreamTarget(videoId: String) {
        streamCache.remove(videoId)
    }

    suspend fun streamTarget(videoId: String): StreamTarget = withContext(Dispatchers.IO) {
        streamCache[videoId]
            ?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }
            ?.target
            ?: synchronized(streamExtractionLock) {
                streamCache[videoId]
                    ?.takeIf { it.expiresAtMillis > System.currentTimeMillis() }
                    ?.target
                    ?: run {
                        val audio = try {
                            resolveAudioStream(videoId, useIosClient = false)
                        } catch (error: ContentNotAvailableException) {
                            // The video itself is gone; a different client cannot help.
                            throw error
                        } catch (error: ExtractionException) {
                            // Client-agnostic extraction failures (bot checks, throttling,
                            // player/API changes) often succeed on the iOS Innertube client.
                            logger.info(
                                "Default YouTube client failed for video {}, retrying with iOS client: {}",
                                videoId,
                                error.message
                            )
                            resolveAudioStream(videoId, useIosClient = true)
                        }
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

    /**
     * Resolve a playable audio stream, optionally using the iOS Innertube client.
     * The extractor flag is global, so it is always restored in a finally block.
     */
    private fun resolveAudioStream(videoId: String, useIosClient: Boolean): AudioStream {
        if (useIosClient) YoutubeStreamExtractor.setFetchIosClient(true)
        try {
            return streamExtractor(videoId).getAudioStreams()
                .asSequence()
                .filter { it.isUrl() }
                .sortedWith(compareBy<AudioStream> {
                    if (it.getFormat()?.getMimeType() == "audio/mp4") 0 else 1
                }.thenBy { bitrateDistance(it.getAverageBitrate()) })
                .firstOrNull() ?: error("No playable audio stream found")
        } finally {
            if (useIosClient) YoutubeStreamExtractor.setFetchIosClient(false)
        }
    }

    /**
     * Extract an external YouTube playlist. [page] == 1 starts a fresh session;
     * higher pages continue from the previous page's continuation token. The
     * returned [PlaylistWithTracksDto.nextPage] tells the client when more
     * tracks exist.
     */
    suspend fun playlist(url: String, page: Int = 1): PlaylistWithTracksDto = withContext(Dispatchers.IO) {
        require(page >= 1) { "page must be >= 1" }
        if (page == 1) playlistSessions.remove(url)
        synchronized(lockFor("playlist:$url")) {
            var session = playlistSessions[url]
            if (page > 1 && session == null) {
                // Session was evicted/restarted; the client should re-fetch.
                return@withContext PlaylistWithTracksDto("", "", emptyList(), null)
            }
            if (session == null) {
                val extractor: PlaylistExtractor = ServiceList.YouTube.getPlaylistExtractor(url)
                extractor.fetchPage()
                session = PlaylistSession(extractor, null)
                playlistSessions[url] = session
            }
            val infoPage = if (page == 1 || session.cursor == null) {
                session.extractor.getInitialPage()
            } else {
                session.extractor.getPage(session.cursor)
            }
            session.cursor = infoPage.getNextPage()
            PlaylistWithTracksDto(
                id = session.extractor.getId(),
                name = session.extractor.getName(),
                tracks = infoPage.getItems().asSequence()
                    .mapNotNull(::toTrack).take(MAX_RESULTS).toList(),
                nextPage = if (session.cursor != null) page + 1 else null
            )
        }
    }

    /**
     * Extract a channel's uploads ("discography") with the same continuation
     * pagination as [playlist]. Uses the channel's first tab.
     */
    suspend fun channel(url: String, page: Int = 1): PlaylistWithTracksDto = withContext(Dispatchers.IO) {
        require(page >= 1) { "page must be >= 1" }
        if (page == 1) channelSessions.remove(url)
        synchronized(lockFor("channel:$url")) {
            var session = channelSessions[url]
            if (page > 1 && session == null) {
                return@withContext PlaylistWithTracksDto("", "", emptyList(), null)
            }
            if (session == null) {
                val extractor: ChannelExtractor = ServiceList.YouTube.getChannelExtractor(url)
                extractor.fetchPage()
                val tab = extractor.getTabs().firstOrNull()
                    ?: return@withContext PlaylistWithTracksDto(extractor.getId(), extractor.getName(), emptyList(), null)
                val tabExtractor: ChannelTabExtractor = ServiceList.YouTube.getChannelTabExtractor(tab)
                tabExtractor.fetchPage()
                session = ChannelSession(tabExtractor, null)
                channelSessions[url] = session
            }
            val infoPage = if (page == 1 || session.cursor == null) {
                session.extractor.getInitialPage()
            } else {
                session.extractor.getPage(session.cursor)
            }
            session.cursor = infoPage.getNextPage()
            PlaylistWithTracksDto(
                id = session.extractor.getId(),
                name = session.extractor.getName(),
                tracks = infoPage.getItems().asSequence()
                    .mapNotNull(::toTrack).take(MAX_RESULTS).toList(),
                nextPage = if (session.cursor != null) page + 1 else null
            )
        }
    }

    private fun streamExtractor(videoId: String): StreamExtractor =
        ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId").also { it.fetchPage() }

    private fun lockFor(key: String): Any = locks.computeIfAbsent(key) { Any() }

    private fun toTrack(item: InfoItem): TrackDto? {
        if (item !is StreamInfoItem) return null
        // Keep the queue/autoplay music-only: drop live streams, Shorts and
        // micro-clips before they reach the app.
        if (!isPlayableMusicItem(item)) return null
        val url = item.getUrl()
        return TrackDto(
            videoId = videoIdFromUrl(url) ?: return null,
            title = item.getName(),
            uploader = item.getUploaderName().orEmpty().ifBlank { "Unknown artist" },
            durationMs = item.getDuration().coerceAtLeast(0) * 1000,
            thumbnailUrl = item.getThumbnails().firstOrNull()?.getUrl().orEmpty()
        )
    }

    private fun isPlayableMusicItem(item: StreamInfoItem): Boolean =
        isPlayableMusicCandidate(item.getStreamType(), item.isShortFormContent(), item.getDuration().coerceAtLeast(0))

    /**
     * Pure music-candidate check used to keep search results and autoplay
     * music-only. Live streams, Shorts and micro-clips (< 30s) are excluded.
     */
    internal fun isPlayableMusicCandidate(
        streamType: StreamType,
        isShortForm: Boolean,
        durationSeconds: Long
    ): Boolean {
        if (isShortForm) return false
        when (streamType) {
            StreamType.LIVE_STREAM,
            StreamType.AUDIO_LIVE_STREAM,
            StreamType.POST_LIVE_STREAM,
            StreamType.POST_LIVE_AUDIO_STREAM -> return false
            else -> Unit
        }
        return durationSeconds >= MIN_MUSIC_SECONDS
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
        private const val MIN_MUSIC_SECONDS = 30
        private const val STREAM_CACHE_TTL_MILLIS = 15 * 60 * 1000L
    }
}

data class CachedStreamTarget(val target: StreamTarget, val expiresAtMillis: Long)

data class StreamTarget(val url: String, val contentType: String)

/** In-memory paging session for /playlist; keeps the extractor's next Page. */
private class PlaylistSession(val extractor: PlaylistExtractor, var cursor: Page?)

/** In-memory paging session for /channel; keeps the tab extractor's next Page. */
private class ChannelSession(val extractor: ChannelTabExtractor, var cursor: Page?)

private class BoundedCache<K, V>(private val maxSize: Int) {
    private val values = Collections.synchronizedMap(object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
    })

    operator fun get(key: K): V? = values[key]
    operator fun set(key: K, value: V) { values[key] = value }
    fun remove(key: K) { values.remove(key) }
}
