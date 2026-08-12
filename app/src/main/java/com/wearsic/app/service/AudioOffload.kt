package com.wearsic.app.service

import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.DefaultAudioSink

// Media3 1.5.1 compiles against SDK 36 stubs, where these AudioFormat constants
// were removed from the public API. The numeric values are the stable AOSP
// definitions and are safe to use directly.
private const val ENCODING_AMRNB = 3
private const val ENCODING_AMRWB = 4
private const val ENCODING_PCM_ALAW = 5
private const val ENCODING_PCM_MULAW = 6
private const val ENCODING_FLAC = 12

/**
 * Hardware audio offload on Wear OS.
 *
 * Offload moves decoding from the CPU to the watch's audio DSP. While a stream
 * is offloaded the CPU can enter deep sleep (Media3 then reports it via
 * `onSleepingForOffloadChanged`), which is the single biggest battery win
 * available to an audio app. Decoding AAC/MP3/Opus on the watch CPU is a
 * continuous, significant load; the DSP does it for near-zero power.
 *
 * Two things must never happen, or offload silently drops back to software
 * decoding and the battery win disappears:
 *  - playback speed changes (`player.playbackParameters` != 1.0), and
 *  - custom audio processors (equalizers, loudness enhancers, visualizers).
 * Wearsic uses neither, and [WearsicAudioOffloadSupportProvider] explicitly
 * reports `isSpeedChangeSupported = false` so Media3 never attempts offload
 * under a non-1.0 speed.
 */
@OptIn(UnstableApi::class)
object AudioOffload {
    private const val TAG = "WearsicOffload"

    // Music content/usage attributes for the HAL offload check. Offload
    // support for media (as opposed to e.g. voice call) is what a music
    // streaming app wants.
    private val MUSIC_ATTRIBUTES: android.media.AudioAttributes =
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    /**
     * Verify that the platform's audio DSP can offload the given format.
     * `AudioManager.isOffloadedPlaybackSupported` is the authoritative check
     * and is what the audio HAL answers for the current output device (e.g.
     * the watch's own speaker, or a connected Bluetooth headset).
     *
     * The static `(AudioFormat, AudioAttributes)` overload only exists from
     * API 34 (Wear OS 5, e.g. Galaxy Watch 7). On API 30–33 the two-argument
     * form does not exist, so we fall back to an optimistic "yes" for known
     * offloadable codecs: if the actual offload attempt is rejected at
     * runtime, Media3's DefaultAudioSink falls back to software decoding
     * transparently (it tracks `offloadDisabledUntilNextConfiguration`).
     *
     * Returns false for anything we cannot map to an offloadable encoding or
     * any format with missing/unsupported sample-rate or channel layout, so an
     * unknown stream always falls back to software decoding instead of
     * mis-reporting DSP support.
     */
    fun isOffloadable(
        mimeType: String?,
        sampleRate: Int,
        channelCount: Int
    ): Boolean {
        val encoding = mimeToEncoding(mimeType) ?: return false
        val channelMask = channelMaskFor(channelCount) ?: return false
        if (sampleRate <= 0) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 30–33: static overload unavailable; codec+layout gate passed,
            // so let Media3 attempt offload and fall back if the HAL refuses.
            return true
        }
        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()
        return runCatching {
            AudioManager.isOffloadedPlaybackSupported(format, MUSIC_ATTRIBUTES)
        }.getOrDefault(false)
    }

    /**
     * Map a Media3 sample MIME type to the matching [AudioFormat] encoding.
     * Only codecs the DSP commonly offloads are mapped; everything else
     * (e.g. Vorbis) returns null and plays through the software decoder.
     */
    fun mimeToEncoding(mimeType: String?): Int? = when (mimeType) {
        MimeTypes.AUDIO_AAC -> AudioFormat.ENCODING_AAC_LC
        MimeTypes.AUDIO_MPEG -> AudioFormat.ENCODING_MP3
        MimeTypes.AUDIO_OPUS -> AudioFormat.ENCODING_OPUS
        MimeTypes.AUDIO_AMR_NB -> ENCODING_AMRNB
        MimeTypes.AUDIO_AMR_WB -> ENCODING_AMRWB
        MimeTypes.AUDIO_FLAC -> ENCODING_FLAC
        MimeTypes.AUDIO_ALAW -> ENCODING_PCM_ALAW
        MimeTypes.AUDIO_MLAW -> ENCODING_PCM_MULAW
        else -> null
    }

    /** Offload paths are mono/stereo; anything else is handled in software. */
    fun channelMaskFor(channelCount: Int): Int? = when (channelCount) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        else -> null
    }
}

/**
 * Media3's offload decision hook. Called per format before playback starts;
 * returning [AudioOffloadSupport.DEFAULT_UNSUPPORTED] keeps software decoding.
 *
 * The [AudioOffloadSupport] we return deliberately claims:
 *  - format supported: DSP can decode this stream (verified against the HAL,
 *    or optimistically on API < 34 with runtime fallback),
 *  - gapless NOT supported: gapless hand-off is not needed for streaming, and
 *    claiming it would only narrow the set of offloadable streams,
 *  - speed changes NOT supported: Wearsic never changes speed, and this flag
 *    guarantees Media3 never lets a speed change silently kill offload.
 */
@OptIn(UnstableApi::class)
class WearsicAudioOffloadSupportProvider : DefaultAudioSink.AudioOffloadSupportProvider {

    override fun getAudioOffloadSupport(
        format: Format,
        audioAttributes: AudioAttributes
    ): AudioOffloadSupport {
        val offloadable = AudioOffload.isOffloadable(
            mimeType = format.sampleMimeType,
            sampleRate = format.sampleRate,
            channelCount = format.channelCount
        )
        if (!offloadable) return AudioOffloadSupport.DEFAULT_UNSUPPORTED
        return AudioOffloadSupport.Builder()
            .setIsFormatSupported(true)
            .setIsGaplessSupported(false)
            .setIsSpeedChangeSupported(false)
            .build()
    }
}
