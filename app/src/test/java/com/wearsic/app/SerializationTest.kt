package com.wearsic.app

import com.wearsic.app.data.model.HealthResponse
import com.wearsic.app.data.model.PlaylistWithTracks
import com.wearsic.app.data.model.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards against a missing org.jetbrains.kotlin.plugin.serialization plugin:
 * the reified encode/decode calls below require generated serializers and will
 * fail at COMPILE time if the plugin is not applied. Previously the app module
 * had no serializer codegen, so .body<HealthResponse>() and .body<List<Track>>()
 * failed at runtime with "Serializer for class ... not found".
 */
class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun healthResponseDeserializesFromServerShape() {
        val parsed = json.decodeFromString<HealthResponse>(
            """{"status":"ok","version":"1.0.0","uptimeSeconds":42}"""
        )
        assertEquals("ok", parsed.status)
        assertEquals("1.0.0", parsed.version)
        assertEquals(42L, parsed.uptimeSeconds)
    }

    @Test
    fun trackListRoundTrips() {
        val tracks = listOf(
            Track(videoId = "abc", title = "Song", uploader = "Artist", durationMs = 120_000, thumbnailUrl = "")
        )
        val encoded = json.encodeToString(tracks)
        val decoded = json.decodeFromString<List<Track>>(encoded)
        assertEquals(tracks, decoded)
    }

    @Test
    fun playlistWithTracksDeserializesFromServerShape() {
        val parsed = json.decodeFromString<PlaylistWithTracks>(
            """{"id":"p1","name":"Mix","tracks":""" +
                """[{"videoId":"abc","title":"Song","uploader":"Artist","durationMs":120000,"thumbnailUrl":""}]}"""
        )
        assertEquals("Mix", parsed.name)
        assertEquals(1, parsed.tracks.size)
        assertEquals("abc", parsed.tracks[0].videoId)
    }
}
