package com.wearsic.app

import com.wearsic.app.data.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regression guard for the "Fail to prepare request body for sending ...
 * (Kotlin reflection is not available), with Content-Type: null" error.
 *
 * Ktor 2.3.x ContentNegotiation will NOT convert a @Serializable request body
 * unless an explicit Content-Type header is set on the request. Every body in
 * MusicRepository (Track favorites/playlist adds, create-playlist Map, and the
 * YouTube cookie request) therefore MUST set `contentType(ContentType.Application.Json)`.
 *
 * The client below uses the exact Json config + ContentNegotiation setup that
 * MusicRepository uses.
 */
class KtorBodySerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val track = Track(
        videoId = "video-1",
        title = "Starboy",
        uploader = "The Weeknd",
        durationMs = 234_000,
        thumbnailUrl = "https://example.com/art.jpg"
    )

    /**
     * Captures the outgoing body. Note: MockEngine's request.headers never
     * contains Content-Type here — after ContentNegotiation the content type
     * lives on the TextContent object, which is what real engines (OkHttp) use.
     */
    private fun mockClient(capture: (body: TextContent?) -> Unit): HttpClient =
        HttpClient(MockEngine { request ->
            capture(request.body as? TextContent)
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) {
                json(json)
            }
        }

    @Test
    fun trackBodySerializesToJsonWithExplicitContentType() {
        var body: TextContent? = null
        val client = mockClient { body = it }

        runBlocking {
            client.post("http://test.local/favorites") {
                contentType(ContentType.Application.Json)
                setBody(track)
            }
        }

        val text = body?.text
        assertNotNull("Track body was not serialized by ContentNegotiation", text)
        assertEquals("application/json", body?.contentType?.withoutParameters()?.toString())
        assertTrue("videoId missing in body: $text", text!!.contains("\"videoId\":\"video-1\""))
        assertTrue("title missing in body: $text", text.contains("\"title\":\"Starboy\""))
        assertTrue("uploader missing in body: $text", text.contains("\"uploader\":\"The Weeknd\""))
        assertTrue("durationMs missing in body: $text", text.contains("\"durationMs\":234000"))
        assertTrue("thumbnailUrl missing in body: $text", text.contains("\"thumbnailUrl\":\"https://example.com/art.jpg\""))
    }

    @Test
    fun missingContentTypeReproducesOriginalErrorAndIsRejected() {
        // Documents WHY every repo call must set contentType explicitly:
        // without it, Ktor 2.3.x throws the exact error the user saw.
        val client = mockClient { }

        try {
            runBlocking {
                client.post("http://test.local/favorites") {
                    setBody(track)
                }
            }
            fail("Expected IllegalStateException for missing Content-Type")
        } catch (e: IllegalStateException) {
            val message = e.message.orEmpty()
            assertTrue("Unexpected error: $message", message.contains("Fail to prepare request body for sending"))
            assertTrue("Unexpected error: $message", message.contains("Content-Type: null"))
        }
    }

    @Test
    fun mapBodySerializesToJsonWithExplicitContentType() {
        var body: TextContent? = null
        val client = mockClient { body = it }

        runBlocking {
            client.post("http://test.local/playlists") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("name" to "Watch Mix"))
            }
        }

        val text = body?.text
        assertNotNull("Map body was not serialized by ContentNegotiation", text)
        assertEquals("application/json", body?.contentType?.withoutParameters()?.toString())
        assertTrue("name missing in body: $text", text!!.contains("\"name\":\"Watch Mix\""))
    }

    @Test
    fun deserializationRoundTripOfTrackStillWorks() {
        // The inverse direction: the same converter must also parse Track JSON
        // from responses (used by GET /favorites and GET /playlists/{id}).
        val client = HttpClient(MockEngine { request ->
            respond(
                """{"videoId":"video-1","title":"Starboy","uploader":"The Weeknd","durationMs":234000,"thumbnailUrl":"https://example.com/art.jpg"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val received = runBlocking {
            client.post("http://test.local/echo") {
                contentType(ContentType.Application.Json)
                setBody(track)
            }.body<Track>()
        }

        assertEquals(track, received)
    }
}
