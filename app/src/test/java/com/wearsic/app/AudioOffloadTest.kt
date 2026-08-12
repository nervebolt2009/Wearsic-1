package com.wearsic.app

import android.content.Context
import android.media.AudioFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wearsic.app.service.AudioOffload
import com.wearsic.app.service.FetchWakeLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for the battery-efficiency building blocks: the audio-DSP offload
 * codec mapping, mono/stereo channel masks, and the fetch wake lock lifecycle.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AudioOffloadTest {

    // --- mimeToEncoding -----------------------------------------------------

    @Test
    fun aacMapsToAacLcEncoding() {
        assertEquals(AudioFormat.ENCODING_AAC_LC, AudioOffload.mimeToEncoding("audio/mp4a-latm"))
    }

    @Test
    fun mp3MapsToMp3Encoding() {
        assertEquals(AudioFormat.ENCODING_MP3, AudioOffload.mimeToEncoding("audio/mpeg"))
    }

    @Test
    fun opusMapsToOpusEncoding() {
        assertEquals(AudioFormat.ENCODING_OPUS, AudioOffload.mimeToEncoding("audio/opus"))
    }

    @Test
    fun unknownMimeIsNotOffloadable() {
        assertNull(AudioOffload.mimeToEncoding("audio/vorbis"))
        assertNull(AudioOffload.mimeToEncoding(null))
        assertNull(AudioOffload.mimeToEncoding("application/octet-stream"))
    }

    // --- channelMaskFor -----------------------------------------------------

    @Test
    fun monoAndStereoAreOffloadable() {
        assertEquals(AudioFormat.CHANNEL_OUT_MONO, AudioOffload.channelMaskFor(1))
        assertEquals(AudioFormat.CHANNEL_OUT_STEREO, AudioOffload.channelMaskFor(2))
    }

    @Test
    fun nonStereoLayoutsFallBackToSoftware() {
        assertNull(AudioOffload.channelMaskFor(0))
        assertNull(AudioOffload.channelMaskFor(5))
        assertNull(AudioOffload.channelMaskFor(-1))
    }

    // --- isOffloadable gating ----------------------------------------------

    @Test
    fun offloadRequiresKnownCodecAndLayout() {
        // Missing/unknown pieces must be rejected before any AudioManager call.
        assertFalse(AudioOffload.isOffloadable(null, 44_100, 2))
        assertFalse(AudioOffload.isOffloadable("audio/mpeg", 0, 2))
        assertFalse(AudioOffload.isOffloadable("audio/mpeg", 44_100, 0))
        assertFalse(AudioOffload.isOffloadable("audio/vorbis", 44_100, 2))
    }

    // --- FetchWakeLock ------------------------------------------------------

    @Test
    fun wakeLockIsReleasedWhenLastHolderLeaves() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wakeLock = FetchWakeLock(context)

        assertFalse(wakeLock.isHeld)
        wakeLock.acquire()
        assertTrue(wakeLock.isHeld)

        // Nested fetch while another is in flight: still held, single lock.
        wakeLock.acquire()
        assertTrue(wakeLock.isHeld)

        wakeLock.release()
        assertTrue(wakeLock.isHeld) // one holder remains
        wakeLock.release()
        assertFalse(wakeLock.isHeld)

        // Extra releases are safe no-ops.
        wakeLock.release()
        assertFalse(wakeLock.isHeld)
    }

    @Test
    fun wakeLockSurvivesRepeatedAcquireReleaseCycles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wakeLock = FetchWakeLock(context)
        repeat(10) {
            wakeLock.acquire()
            assertTrue(wakeLock.isHeld)
            wakeLock.release()
            assertFalse(wakeLock.isHeld)
        }
    }
}
