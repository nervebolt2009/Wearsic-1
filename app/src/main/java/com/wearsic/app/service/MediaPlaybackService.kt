package com.wearsic.app.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wearsic.app.MainActivity
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.repository.MusicRepository
import android.util.Log

class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private val repository = MusicRepository()

    companion object {
        const val ACTION_PLAY = "com.wearsic.app.action.PLAY"
        const val ACTION_TOGGLE_PLAYBACK = "com.wearsic.app.action.TOGGLE_PLAYBACK"
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_API_KEY = "extra_api_key"
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_UPLOADER = "extra_uploader"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_THUMBNAIL_URL = "extra_thumbnail_url"
        private const val TAG = "WearsicPlayback"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        httpDataSourceFactory = httpDataSource
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSource)
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
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Playback failed", error)
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
    }

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_SERVER_URL)?.let(repository::setServerUrl)
        intent?.getStringExtra(EXTRA_API_KEY)?.let {
            repository.setApiKey(it)
            if (it.isNotBlank()) {
                httpDataSourceFactory?.setDefaultRequestProperties(mapOf("X-Wearsic-Key" to it))
            }
        }
        try {
            when (intent?.action) {
                ACTION_PLAY -> intent.toTrack()?.let(::playTrack)
                ACTION_TOGGLE_PLAYBACK -> mediaSession?.player?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Playback command failed", error)
            mediaSession?.player?.stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun playTrack(track: Track) {
        val player = mediaSession?.player ?: return
        if (repository.getServerUrl().isBlank()) {
            Log.w(TAG, "Ignoring playback without a configured server URL")
            return
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.videoId)
            .setUri(repository.getStreamUrl(track.videoId))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.uploader)
                    .setArtworkUri(android.net.Uri.parse(track.thumbnailUrl))
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun addToQueue(track: Track) {
        mediaSession?.player?.addMediaItem(mediaItemFor(track))
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val player = mediaSession?.player ?: return
        player.setMediaItems(tracks.map(::mediaItemFor), startIndex, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    fun setServerUrl(url: String) {
        repository.setServerUrl(url)
    }

    private fun mediaItemFor(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.videoId)
        .setUri(repository.getStreamUrl(track.videoId))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.uploader)
                .setArtworkUri(android.net.Uri.parse(track.thumbnailUrl))
                .build()
        )
        .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
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
