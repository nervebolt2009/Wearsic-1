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
import kotlin.test.assertTrue

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
