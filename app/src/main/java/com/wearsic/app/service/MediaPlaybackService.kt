package com.wearsic.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import kotlin.math.abs
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wearsic.app.MainActivity
import com.wearsic.app.R
import com.wearsic.app.data.cache.OfflineDownloadStore
import com.wearsic.app.data.cache.PlaybackCache
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.preferences.SettingsManager
import com.wearsic.app.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log

/**
 * Foreground media playback service. Owns the playback queue so music keeps
 * playing (and auto-advancing) even when the app is cleared from recents or the
 * user switches to another app. Streams flow through a Media3 SimpleCache when
 * one is available for offline (re)playback.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private val repository = MusicRepository()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressPollJob: Job? = null
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    @Volatile private var clearingCache = false

    companion object {
        const val ACTION_PLAY = "com.wearsic.app.action.PLAY"
        const val ACTION_PLAY_TRACKS = "com.wearsic.app.action.PLAY_TRACKS"
        const val ACTION_TOGGLE_PLAYBACK = "com.wearsic.app.action.TOGGLE_PLAYBACK"
        const val ACTION_SKIP_NEXT = "com.wearsic.app.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.wearsic.app.action.SKIP_PREVIOUS"
        const val ACTION_ADD_TO_QUEUE = "com.wearsic.app.action.ADD_TO_QUEUE"
        const val ACTION_REMOVE_FROM_QUEUE = "com.wearsic.app.action.REMOVE_FROM_QUEUE"
        const val ACTION_CLEAR_QUEUE = "com.wearsic.app.action.CLEAR_QUEUE"
        const val ACTION_MOVE_QUEUE_ITEM = "com.wearsic.app.action.MOVE_QUEUE_ITEM"
        const val ACTION_CLEAR_CACHE = "com.wearsic.app.action.CLEAR_CACHE"
        const val ACTION_DOWNLOAD_TRACK = "com.wearsic.app.action.DOWNLOAD_TRACK"

        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_API_KEY = "extra_api_key"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_UPLOADER = "extra_uploader"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_THUMBNAIL_URL = "extra_thumbnail_url"
        const val EXTRA_QUEUE_JSON = "extra_queue_json"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_START_POSITION_MS = "extra_start_position_ms"
        const val EXTRA_ADD_TRACK_JSON = "extra_add_track_json"
        const val EXTRA_REMOVE_INDEX = "extra_remove_index"
        const val EXTRA_DOWNLOAD_TRACK_JSON = "extra_download_track_json"
        const val EXTRA_AUTO_DOWNLOAD = "extra_auto_download"
        const val EXTRA_MOVE_FROM_INDEX = "extra_move_from_index"
        const val EXTRA_MOVE_TO_INDEX = "extra_move_to_index"

        private const val TAG = "WearsicPlayback"
        private const val NOTIFICATION_CHANNEL_ID = "wearsic_playback"
        private const val NOTIFICATION_ID = 1001
    }

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        httpDataSourceFactory = httpDataSource

        // Offline audio cache: sized from the user setting, never fatal.
        val cache = PlaybackCache.configure(applicationContext, readCacheSizeBytes())

        val dataSourceFactory: androidx.media3.datasource.DataSource.Factory =
            if (cache != null) {
                try {
                    CacheDataSource.Factory()
                        .setCache(cache)
                        .setUpstreamDataSourceFactory(httpDataSource)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                        .also { cacheDataSourceFactory = it }
                } catch (error: Exception) {
                    Log.w(TAG, "CacheDataSource setup failed, streaming without cache", error)
                    DefaultDataSource.Factory(this, httpDataSource)
                }
            } else {
                DefaultDataSource.Factory(this, httpDataSource)
            }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback failed", error)
                val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                    ?: error.errorCodeName
                PlaybackEvents.reportError("Audio playback failed: $detail")
                PlaybackEvents.reportPlaying(false)
                stopProgressPolling()
                player.stop()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                PlaybackEvents.reportPlaying(playing)
                if (playing) startProgressPolling(player) else stopProgressPolling()
                updateNotification(player)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        // Only fired when the whole queue is exhausted (Media3
                        // auto-advances between items), so this means "fetch more".
                        PlaybackEvents.reportTrackEnded()
                        PlaybackEvents.reportPlaying(false)
                        stopProgressPolling()
                    }
                    Player.STATE_READY -> reportProgress(player)
                    Player.STATE_BUFFERING -> updateNotification(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                emitQueueState(player)
                reportProgress(player)
                updateNotification(player)
                mediaItem?.let(::trackFromMediaItem)?.let(::maybeAutoDownload)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED)
                ) {
                    emitQueueState(player)
                }
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    reportProgress(player)
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
        OfflineDownloadStore.reconcile(applicationContext, cache) { videoId -> cacheKeyFor(videoId) }
        PlaybackEvents.reportDownloadedIds(OfflineDownloadStore.readIds(applicationContext))
    }

    private fun readCacheSizeBytes(): Long {
        return try {
            val mb = runBlocking { SettingsManager(applicationContext).cacheSizeMb.first() }
            mb.coerceAtLeast(0).toLong() * 1024L * 1024L
        } catch (error: Exception) {
            Log.w(TAG, "Could not read cache size, using default", error)
            SettingsManager.DEFAULT_CACHE_SIZE_MB.toLong() * 1024L * 1024L
        }
    }

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote immediately, before resolving the signed stream URL. Android 14/Wear
        // enforces a short deadline after startForegroundService() and network extraction
        // can legitimately take several seconds.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                playbackNotification(
                    intent?.getStringExtra(EXTRA_TITLE),
                    intent?.getStringExtra(EXTRA_UPLOADER)
                ),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not promote playback service", error)
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(EXTRA_SERVER_URL)?.let(repository::setServerUrl)
        intent?.getStringExtra(EXTRA_API_KEY)?.let {
            repository.setApiKey(it)
            if (it.isNotBlank()) {
                httpDataSourceFactory?.setDefaultRequestProperties(mapOf("X-Wearsic-Key" to it))
            }
        }
        try {
            when (intent?.action) {
                ACTION_PLAY -> intent.toTrack()?.let { playTracks(listOf(it), 0) }
                ACTION_PLAY_TRACKS -> {
                    val tracks = intent.getStringExtra(EXTRA_QUEUE_JSON)
                        ?.let(::decodeTracks)
                        .orEmpty()
                    if (tracks.isNotEmpty()) {
                        // The app uses -1 as "no explicit position"; normalize any
                        // negative value to TIME_UNSET so setMediaItems stays safe.
                        val positionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, C.TIME_UNSET)
                        playTracks(
                            tracks,
                            intent.getIntExtra(EXTRA_START_INDEX, 0),
                            if (positionMs < 0L) C.TIME_UNSET else positionMs
                        )
                    }
                }
                ACTION_TOGGLE_PLAYBACK -> mediaSession?.player?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
                ACTION_SKIP_NEXT -> mediaSession?.player?.let { player ->
                    player.seekToNextMediaItem()
                    player.play()
                }
                ACTION_SKIP_PREVIOUS -> mediaSession?.player?.let { player ->
                    player.seekToPreviousMediaItem()
                    player.play()
                }
                ACTION_ADD_TO_QUEUE -> intent.getStringExtra(EXTRA_ADD_TRACK_JSON)
                    ?.let(::decodeTrack)
                    ?.let(::addToQueue)
                ACTION_REMOVE_FROM_QUEUE -> removeAt(intent.getIntExtra(EXTRA_REMOVE_INDEX, -1))
                ACTION_MOVE_QUEUE_ITEM -> moveItem(
                    intent.getIntExtra(EXTRA_MOVE_FROM_INDEX, -1),
                    intent.getIntExtra(EXTRA_MOVE_TO_INDEX, -1)
                )
                ACTION_CLEAR_QUEUE -> clearQueueInternal()
                ACTION_CLEAR_CACHE -> clearCache()
                ACTION_DOWNLOAD_TRACK -> intent.getStringExtra(EXTRA_DOWNLOAD_TRACK_JSON)
                    ?.let(::decodeTrack)
                    ?.let { track -> startDownload(track, intent.getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false)) }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Playback command failed", error)
            mediaSession?.player?.stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Replace the whole queue and start playing at [startIndex]. When
     * [startPositionMs] is not [C.TIME_UNSET], playback resumes at that
     * position (used by queue reorders like shuffle so the song does not
     * restart).
     */
    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        startPositionMs: Long = C.TIME_UNSET
    ) {
        val player = mediaSession?.player ?: return
        if (repository.getServerUrl().isBlank() || tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.size - 1)
        player.setMediaItems(tracks.map(::mediaItemFor), index, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        emitQueueState(player)
    }

    /** Play a single track (kept for compatibility with the simple action). */
    fun playTrack(track: Track) = playTracks(listOf(track), 0)

    fun addToQueue(track: Track) {
        val player = mediaSession?.player ?: return
        // Staging only: adding a track must never auto-start playback. Music
        // only starts through ACTION_PLAY_TRACKS or the user's play toggle.
        player.addMediaItem(mediaItemFor(track))
        emitQueueState(player)
    }

    fun removeAt(index: Int) {
        val player = mediaSession?.player ?: return
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            emitQueueState(player)
        }
    }

    /**
     * Move a queue item to a new position while playback stays pinned to the
     * same track (matched by media id) at the same position. Uses remove/add so
     * the currently prepared items are not re-resolved.
     */
    fun moveItem(from: Int, to: Int) {
        val player = mediaSession?.player ?: return
        val count = player.mediaItemCount
        if (from !in 0 until count || to !in 0 until count || from == to) return
        val currentId = player.currentMediaItem?.mediaId
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val item = player.getMediaItemAt(from)
        if (from < to) {
            player.addMediaItem(to + 1, item)
            player.removeMediaItem(from)
        } else {
            player.removeMediaItem(from)
            player.addMediaItem(to, item)
        }
        val newIndex = (0 until player.mediaItemCount)
            .indexOfFirst { player.getMediaItemAt(it).mediaId == currentId }
        if (newIndex >= 0 && newIndex != player.currentMediaItemIndex) {
            player.seekTo(newIndex, positionMs)
        }
        emitQueueState(player)
    }

    fun setServerUrl(url: String) {
        repository.setServerUrl(url)
    }

    private fun cacheKeyFor(videoId: String): String = repository.getStreamUrl(videoId)

    private fun mediaItemFor(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.videoId)
        .setUri(cacheKeyFor(track.videoId))
        .setCustomCacheKey(cacheKeyFor(track.videoId))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.uploader)
                .setArtworkUri(android.net.Uri.parse(track.thumbnailUrl))
                .setExtras(Bundle().apply { putLong(EXTRA_DURATION_MS, track.durationMs) })
                .build()
        )
        .build()

    private fun trackFromMediaItem(item: MediaItem): Track? {
        val id = item.mediaId.takeIf { it.isNotBlank() } ?: return null
        val metadata = item.mediaMetadata
        return Track(
            videoId = id,
            title = metadata.title?.toString().orEmpty(),
            uploader = metadata.artist?.toString().orEmpty(),
            durationMs = metadata.extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L,
            thumbnailUrl = metadata.artworkUri?.toString().orEmpty()
        )
    }

    private fun decodeTracks(encoded: String): List<Track> = try {
        json.decodeFromString<List<Track>>(encoded)
    } catch (error: Exception) {
        Log.w(TAG, "Could not decode queue", error)
        emptyList()
    }

    private fun decodeTrack(encoded: String): Track? = try {
        json.decodeFromString<Track>(encoded)
    } catch (error: Exception) {
        Log.w(TAG, "Could not decode track", error)
        null
    }

    private fun emitQueueState(player: Player) {
        val tracks = (0 until player.mediaItemCount).mapNotNull { position ->
            player.getMediaItemAt(position).let(::trackFromMediaItem)
        }
        val index = player.currentMediaItemIndex
        PlaybackEvents.reportQueue(tracks, index)
        PlaybackEvents.reportCurrentTrack(tracks.getOrNull(index))
    }

    /**
     * Clear the offline cache while the player may still hold the cache handle:
     * pause first, delete off the main thread, then resume if it was playing.
     */
    @OptIn(UnstableApi::class)
    private fun startDownload(track: Track, automatic: Boolean) {
        if (track.videoId.isBlank() || OfflineDownloadStore.readIds(applicationContext).contains(track.videoId)) return
        if (automatic && !isChargingOrWifi()) return
        if (downloadJobs[track.videoId]?.isActive == true) return
        val cacheFactory = cacheDataSourceFactory ?: return
        val streamUrl = cacheKeyFor(track.videoId)
        PlaybackEvents.reportDownloadProgress(track.videoId, 0f)
        downloadJobs[track.videoId] = serviceScope.launch(Dispatchers.IO) {
            try {
                // The cache reports progress on every buffer; throttle so an
                // active download cannot churn the UI at tens of frames per
                // second (each report rebuilds a StateFlow map).
                var lastReportMs = 0L
                var lastFraction = -1f
                val writer = CacheWriter(
                    cacheFactory.createDataSource(),
                    DataSpec.Builder()
                        .setUri(streamUrl)
                        .setKey(streamUrl)
                        .build(),
                    null,
                    object : CacheWriter.ProgressListener {
                        override fun onProgress(requestLength: Long, bytesCached: Long, newBytesCached: Long) {
                            if (automatic && !isChargingOrWifi()) {
                                throw CancellationException("Automatic download constraints lost")
                            }
                            if (requestLength <= 0L) return
                            val fraction = bytesCached.toFloat() / requestLength.toFloat()
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastReportMs >= 500L && abs(fraction - lastFraction) >= 0.01f) {
                                lastReportMs = now
                                lastFraction = fraction
                                PlaybackEvents.reportDownloadProgress(track.videoId, fraction)
                            }
                        }
                    }
                )
                writer.cache()
                val cachedLength = PlaybackCache.get()?.getContentMetadata(streamUrl)
                    ?.get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                    ?: -1L
                // Unknown-length responses cannot be proven complete after a
                // restart, so leave them as ordinary cache data rather than
                // falsely advertising offline availability.
                if (cachedLength > 0L) {
                    OfflineDownloadStore.markDownloaded(applicationContext, track.videoId, cachedLength)
                }
                PlaybackEvents.reportDownloadedIds(OfflineDownloadStore.readIds(applicationContext))
                PlaybackEvents.reportDownloadProgress(track.videoId, null)
            } catch (_: CancellationException) {
                PlaybackEvents.reportDownloadProgress(track.videoId, null)
            } catch (error: Exception) {
                Log.w(TAG, "Offline download failed for ${track.videoId}", error)
                PlaybackEvents.reportDownloadProgress(track.videoId, null)
                PlaybackEvents.reportDownloadError(track.videoId, error.message ?: "network error")
            } finally {
                downloadJobs.remove(track.videoId)
                if (!clearingCache) finishDownloadServiceIfIdle()
            }
        }
    }

    private fun finishDownloadServiceIfIdle() {
        serviceScope.launch(Dispatchers.Main.immediate) {
            val player = mediaSession?.player
            if (player == null || player.mediaItemCount == 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun maybeAutoDownload(track: Track) {
        serviceScope.launch {
            val enabled = runCatching {
                SettingsManager(applicationContext).autoCacheEnabled.first()
            }.getOrDefault(false)
            if (enabled) startDownload(track, automatic = true)
        }
    }

    private fun isChargingOrWifi(): Boolean {
        val battery = getSystemService(BatteryManager::class.java)
        val charging = battery?.isCharging == true
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        val capabilities = connectivity?.getNetworkCapabilities(network)
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return charging || wifi
    }

    private fun clearCache() {
        val player = mediaSession?.player
        val wasPlaying = player?.isPlaying == true
        clearingCache = true
        player?.pause()
        serviceScope.launch(Dispatchers.IO) {
            try {
                downloadJobs.values.toList().forEach { it.cancel(); it.join() }
                downloadJobs.clear()
                PlaybackCache.clearContents(applicationContext)
                OfflineDownloadStore.clear(applicationContext)
                PlaybackEvents.reportDownloadedIds(emptySet())
                PlaybackEvents.clearDownloadProgress()
                PlaybackEvents.reportCacheCleared()
                if (wasPlaying) {
                    withContext(Dispatchers.Main.immediate) { player?.play() }
                }
            } finally {
                clearingCache = false
                finishDownloadServiceIfIdle()
            }
        }
    }

    private fun clearQueueInternal() {
        val player = mediaSession?.player ?: return
        player.stop()
        player.clearMediaItems()
        PlaybackEvents.reportCurrentTrack(null)
        PlaybackEvents.reportQueue(emptyList(), -1)
        PlaybackEvents.reportPlaying(false)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || player.mediaItemCount == 0) {
            // Nothing loaded: shut down cleanly.
            stopSelf()
            return
        }
        // A queue is loaded: stay alive as a foreground service so music keeps
        // playing in the background after the app is cleared from recents.
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wearsic audio playback"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun playbackNotification(title: String?, artist: String?): android.app.Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: "Wearsic")
            .setContentText(artist?.takeIf { it.isNotBlank() } ?: "Preparing audio…")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    1,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(player: Player) {
        // Media3 shows its own media notification (with controls) while playing;
        // this manual update is only a nicety, so skip it when the user has not
        // granted POST_NOTIFICATIONS on Android 13+.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString()
        val artist = item?.mediaMetadata?.artist?.toString()
        try {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                playbackNotification(title, artist)
            )
        } catch (error: Exception) {
            Log.w(TAG, "Notification update failed", error)
        }
    }

    private fun reportProgress(player: Player) {
        PlaybackEvents.reportProgress(
            player.currentPosition.coerceAtLeast(0L),
            player.duration.coerceAtLeast(0L)
        )
    }

    private fun startProgressPolling(player: ExoPlayer) {
        progressPollJob?.cancel()
        progressPollJob = serviceScope.launch {
            while (isActive) {
                reportProgress(player)
                // The Now Playing UI runs its own 1Hz tick while playing, so the
                // service only needs to resync the true position occasionally.
                // Seeks/track changes still report instantly via Player.Events;
                // this slow poll just corrects drift without waking the main
                // thread every second (a real battery win on a watch).
                delay(5_000)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    override fun onDestroy() {
        stopProgressPolling()
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        repository.close()
        super.onDestroy()
    }

    private fun Intent.toTrack(): Track? {
        val id = getStringExtra(EXTRA_VIDEO_ID) ?: return null
        return Track(
            videoId = id,
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            uploader = getStringExtra(EXTRA_UPLOADER).orEmpty(),
            durationMs = getLongExtra(EXTRA_DURATION_MS, 0L),
            thumbnailUrl = getStringExtra(EXTRA_THUMBNAIL_URL).orEmpty()
        )
    }
}
