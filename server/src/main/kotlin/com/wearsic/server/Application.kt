package com.wearsic.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.cio.CIO
import kotlinx.coroutines.CancellationException
import io.ktor.server.engine.embeddedServer
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.NewPipe
import java.time.Duration
import java.time.Instant

private val startedAt = Instant.now()

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull()?.coerceIn(1, 65_535) ?: 8080
    embeddedServer(
        factory = CIO,
        host = "0.0.0.0",
        port = port,
        module = Application::module
    ).start(wait = true)
}

fun Application.module(databasePath: String = System.getenv("WEARSIC_DB_PATH") ?: "wearsic.db") {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }
    val database = Database(databasePath)
    // A cookie saved from the watch app persists in SQLite. The env var remains
    // the fallback default for operators who prefer file-based configuration.
    val persistedCookie = database.getSetting(SETTING_YOUTUBE_COOKIE).orEmpty()
    if (YoutubeSession.cookie.isBlank() && persistedCookie.isNotBlank()) {
        YoutubeSession.cookie = persistedCookie
    }
    val extractor = ExtractorService()
    val audioProxy = AudioProxy(extractor)
    NewPipe.init(NewPipeDownloader())

    environment.monitor.subscribe(ApplicationStopped) {
        database.close()
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    uptimeSeconds = Duration.between(startedAt, Instant.now()).seconds,
                    version = VERSION
                )
            )
        }
        route("/api") {
            authenticateApiKey()
            registerApiRoutes(database, extractor)
            audioProxy.register(this)
        }
    }
}

private fun Route.authenticateApiKey() {
    intercept(ApplicationCallPipeline.Plugins) {
        val expected = System.getenv("WEARSIC_API_KEY").orEmpty()
        if (expected.isNotBlank() && call.request.headers[API_KEY_HEADER] != expected) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid API key"))
            finish()
        }
    }
}

private fun Route.registerApiRoutes(database: Database, extractor: ExtractorService) {
    get("/search") {
        val query = call.request.queryParameters["q"].orEmpty()
        if (query.length < 2) return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query must contain at least two characters"))
        call.respondExtracted { SearchResponse(extractor.search(query)) }
    }
    get("/suggestions") {
        call.respondExtracted { SuggestionResponse(extractor.suggestions(call.request.queryParameters["q"].orEmpty())) }
    }
    get("/search/albums") {
        val query = call.request.queryParameters["q"].orEmpty()
        if (query.length < 2) return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query must contain at least two characters"))
        call.respondExtracted { extractor.searchAlbums(query) }
    }
    get("/related/{videoId}") {
        val videoId = call.parameters["videoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        call.respondExtracted { SearchResponse(extractor.related(videoId)) }
    }
    get("/favorites") { call.respond(database.getFavorites()) }
    post("/favorites") {
        database.saveFavorite(call.receive())
        call.respond(HttpStatusCode.NoContent)
    }
    delete("/favorites/{videoId}") {
        val videoId = call.parameters["videoId"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        database.deleteFavorite(videoId)
        call.respond(HttpStatusCode.NoContent)
    }
    get("/playlists") { call.respond(database.getPlaylists()) }
    post("/playlists") {
        val request = call.receive<CreatePlaylistRequest>()
        if (request.name.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Playlist name is required"))
        call.respond(HttpStatusCode.Created, database.createPlaylist(request.name))
    }
    get("/playlists/{id}") {
        val playlist = database.getPlaylist(call.parameters["id"].orEmpty())
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Playlist not found"))
        call.respond(playlist)
    }
    post("/playlists/{id}/tracks") {
        val id = call.parameters["id"].orEmpty()
        if (!database.addPlaylistTrack(id, call.receive())) {
            return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Playlist not found"))
        }
        call.respond(HttpStatusCode.NoContent)
    }
    delete("/playlists/{id}/tracks/{videoId}") {
        database.deletePlaylistTrack(call.parameters["id"].orEmpty(), call.parameters["videoId"].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    get("/playlist") {
        val url = call.request.queryParameters["url"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing playlist URL"))
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        call.respondExtracted { extractor.playlist(url, page) }
    }
    get("/channel") {
        val url = call.request.queryParameters["url"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channel URL"))
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        call.respondExtracted { extractor.channel(url, page) }
    }
    get("/config/youtube-cookie") {
        call.respond(YoutubeCookieStatus(database.getSetting(SETTING_YOUTUBE_COOKIE).orEmpty().isNotBlank()))
    }
    post("/config/youtube-cookie") {
        val cookie = call.receive<YoutubeCookieRequest>().cookie.trim()
        database.saveSetting(SETTING_YOUTUBE_COOKIE, cookie)
        // An empty push from the app clears the saved cookie but must not clobber
        // an operator-configured WEARSIC_YOUTUBE_COOKIE fallback at runtime.
        YoutubeSession.cookie = cookie.ifBlank { YoutubeSession.envFallbackCookie }
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * Runs a NewPipe-backed producer and maps extraction failures to clean JSON
 * errors instead of unhandled HTTP 500 responses.
 */
private suspend fun ApplicationCall.respondExtracted(block: suspend () -> Any) {
    try {
        respond(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // Log the real failure server-side; clients only get the mapped message.
        application.environment.log.warn("Extraction failed", error)
        val mapped = mapExtractionError(error)
        respond(mapped.status, ErrorResponse(mapped.message))
    }
}

private const val VERSION = "1.0.0"
private const val API_KEY_HEADER = "X-Wearsic-Key"
private const val SETTING_YOUTUBE_COOKIE = "youtube_cookie"
