package com.wearsic.server

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.StreamType

class ExtractionErrorMappingTest {
    @Test
    fun mapsNewPipeFailuresToCleanStatusCodes() {
        assertEquals(HttpStatusCode.NotFound, mapExtractionError(ContentNotAvailableException("gone")).status)
        assertEquals(HttpStatusCode.ServiceUnavailable, mapExtractionError(SignInConfirmNotBotException("bot")).status)
        assertEquals(HttpStatusCode.BadGateway, mapExtractionError(ExtractionException("boom")).status)
        assertEquals(HttpStatusCode.BadGateway, mapExtractionError(RuntimeException("boom")).status)
    }
}

class MusicFilterTest {
    private val service = ExtractorService()

    @Test
    fun keepsRegularSongs() {
        assertTrue(service.isPlayableMusicCandidate(StreamType.VIDEO_STREAM, isShortForm = false, durationSeconds = 240))
        assertTrue(service.isPlayableMusicCandidate(StreamType.AUDIO_STREAM, isShortForm = false, durationSeconds = 240))
        assertTrue(service.isPlayableMusicCandidate(StreamType.NONE, isShortForm = false, durationSeconds = 180))
    }

    @Test
    fun rejectsLiveStreams() {
        assertFalse(service.isPlayableMusicCandidate(StreamType.LIVE_STREAM, false, 3600))
        assertFalse(service.isPlayableMusicCandidate(StreamType.AUDIO_LIVE_STREAM, false, 3600))
        assertFalse(service.isPlayableMusicCandidate(StreamType.POST_LIVE_STREAM, false, 3600))
        assertFalse(service.isPlayableMusicCandidate(StreamType.POST_LIVE_AUDIO_STREAM, false, 3600))
    }

    @Test
    fun rejectsShortsAndMicroClips() {
        assertFalse(service.isPlayableMusicCandidate(StreamType.VIDEO_STREAM, true, 45))
        assertFalse(service.isPlayableMusicCandidate(StreamType.VIDEO_STREAM, false, 20))
    }
}

class ServerTest {
    @Test
    fun healthIsPublicAndReportsOk() = testApplication {
        environment { config = MapApplicationConfig("ktor.deployment.port" to "0") }
        application { module(createTempDirectory().resolve("test.db").toString()) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun shortSearchIsRejectedBeforeExtraction() = testApplication {
        environment { config = MapApplicationConfig("ktor.deployment.port" to "0") }
        application { module(createTempDirectory().resolve("test.db").toString()) }
        val response = client.get("/api/search?q=a")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun albumsSearchRejectsShortQueries() = testApplication {
        environment { config = MapApplicationConfig("ktor.deployment.port" to "0") }
        application { module(createTempDirectory().resolve("test.db").toString()) }
        val response = client.get("/api/search/albums?q=a")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun youtubeCookieCanBeStoredAndCleared() = testApplication {
        environment { config = MapApplicationConfig("ktor.deployment.port" to "0") }
        application { module(createTempDirectory().resolve("test.db").toString()) }

        val store = client.post("/api/config/youtube-cookie") {
            contentType(ContentType.Application.Json)
            setBody("""{"cookie":"SID=test; HSID=abc"}""")
        }
        assertEquals(HttpStatusCode.NoContent, store.status)
        assertTrue(client.get("/api/config/youtube-cookie").bodyAsText().contains("\"configured\":true"))

        val clear = client.post("/api/config/youtube-cookie") {
            contentType(ContentType.Application.Json)
            setBody("""{"cookie":""}""")
        }
        assertEquals(HttpStatusCode.NoContent, clear.status)
        assertTrue(client.get("/api/config/youtube-cookie").bodyAsText().contains("\"configured\":false"))
    }

    @Test
    fun favoritesAndPlaylistsPersist() = testApplication {
        environment { config = MapApplicationConfig("ktor.deployment.port" to "0") }
        application { module(createTempDirectory().resolve("test.db").toString()) }
        val track = """{"videoId":"abc","title":"Song","uploader":"Artist","durationMs":120000,"thumbnailUrl":""}"""
        val save = client.post("/api/favorites") {
            contentType(ContentType.Application.Json)
            setBody(track)
        }
        assertEquals(HttpStatusCode.NoContent, save.status)
        assertTrue(client.get("/api/favorites").bodyAsText().contains("abc"))

        val create = client.post("/api/playlists") {
            contentType(ContentType.Application.Json)
            setBody("{\"name\":\"Watch Mix\"}")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertTrue(create.bodyAsText().contains("Watch Mix"))

        val playlistId = Regex("\\\"id\\\":\\\"([^\\\"]+)").find(create.bodyAsText())!!.groupValues[1]
        val add = client.post("/api/playlists/$playlistId/tracks") {
            contentType(ContentType.Application.Json)
            setBody(track)
        }
        assertEquals(HttpStatusCode.NoContent, add.status)
        assertTrue(client.get("/api/playlists/$playlistId").bodyAsText().contains("Song"))

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/favorites/abc").status)
    }
}
