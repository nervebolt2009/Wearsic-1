package com.wearsic.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
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
        call.respond(SearchResponse(extractor.search(query)))
    }
    get("/suggestions") {
        call.respond(SuggestionResponse(extractor.suggestions(call.request.queryParameters["q"].orEmpty())))
    }
    get("/related/{videoId}") {
        val videoId = call.parameters["videoId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing videoId"))
        call.respond(SearchResponse(extractor.related(videoId)))
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
        call.respond(extractor.playlist(url))
    }
    get("/channel") {
        val url = call.request.queryParameters["url"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing channel URL"))
        call.respond(extractor.channel(url))
    }
}

private const val VERSION = "1.0.0"
private const val API_KEY_HEADER = "X-Wearsic-Key"
