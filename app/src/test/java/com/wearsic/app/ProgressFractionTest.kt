package com.wearsic.app

import com.wearsic.app.service.progressFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the progress-bar fix: the 0..1 fraction must keep working even when
 * the proxied stream reports no duration (ExoPlayer duration == 0) by falling
 * back to the track's metadata duration.
 */
class ProgressFractionTest {

    @Test
    fun usesPlayerDurationWhenKnown() {
        assertEquals(0.5f, progressFraction(60_000L, 120_000L, 300_000L), 0.0001f)
    }

    @Test
    fun fallsBackToTrackDurationWhenPlayerReportsNone() {
        // Player duration 0 => use the track metadata duration.
        assertEquals(0.5f, progressFraction(60_000L, 0L, 120_000L), 0.0001f)
    }

    @Test
    fun fallsBackWhenDurationIsTimeUnsetCoercedToZero() {
        assertEquals(0.25f, progressFraction(30_000L, 0L, 120_000L), 0.0001f)
    }

    @Test
    fun zeroWhenNoDurationKnownAtAll() {
        assertEquals(0f, progressFraction(45_000L, 0L, 0L), 0.0001f)
    }

    @Test
    fun clampsAboveOne() {
        assertEquals(1f, progressFraction(200_000L, 120_000L, 0L), 0.0001f)
    }

    @Test
    fun zeroWhenPositionIsZero() {
        assertEquals(0f, progressFraction(0L, 120_000L, 120_000L), 0.0001f)
    }
}
