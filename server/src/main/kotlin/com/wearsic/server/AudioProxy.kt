package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.copyTo

class AudioProxy(private val extractor: ExtractorService) {
    private val client = HttpClient(CIO) {
        engine {
            maxConnectionsCount = 8
            requestTimeout = 30_000
        }
    }

    fun register(route: Route) {
        route.get("/stream/{videoId}") {
            val videoId = call.parameters["videoId"] ?: return@get call.respondError(HttpStatusCode.BadRequest, "Missing videoId")
            val target = extractor.streamTarget(videoId)
            val range = call.request.header(HttpHeaders.Range)
            val upstream = client.prepareGet(target.url) {
                range?.let { header(HttpHeaders.Range, it) }
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.UserAgent, "WearsicServer/1.0")
            }.execute()
            call.respond(upstream, target.contentType)
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respond(upstream: HttpResponse, contentType: String) {
        response.header(HttpHeaders.AcceptRanges, "bytes")
        response.header(HttpHeaders.CacheControl, "private, max-age=60")
        upstream.headers[HttpHeaders.ContentLength]?.let { response.header(HttpHeaders.ContentLength, it) }
        upstream.headers[HttpHeaders.ContentRange]?.let { response.header(HttpHeaders.ContentRange, it) }
        val status = upstream.status
        respondBytesWriter(contentType = io.ktor.http.ContentType.parse(contentType), status = status) {
            upstream.bodyAsChannel().copyTo(this)
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String
) {
    respond(status, ErrorResponse(message))
}
