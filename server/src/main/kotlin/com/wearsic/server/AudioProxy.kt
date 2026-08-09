package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header as clientHeader
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header as requestHeader
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class AudioProxy(private val extractor: ExtractorService) {
    private val logger = LoggerFactory.getLogger(AudioProxy::class.java)
    private val client = HttpClient(CIO) {
        engine {
            maxConnectionsCount = 8
            requestTimeout = 30_000
        }
    }

    fun register(route: Route) {
        route.get("/stream/{videoId}") {
            val videoId = call.parameters["videoId"]
                ?: return@get call.safeRespondError(HttpStatusCode.BadRequest, "Missing videoId")
            try {
                call.streamWithRetry(videoId, call.request.requestHeader(HttpHeaders.Range))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Never leave ExoPlayer with an empty 500 response. Map the
                // specific NewPipe failure to a clean, actionable JSON error.
                val mapped = mapExtractionError(error)
                logger.warn("Audio extraction failed for video {}: {}", videoId, error.message)
                call.safeRespondError(mapped.status, mapped.message)
            }
        }
    }

    /**
     * Keep the upstream response open for the entire downstream copy. Using
     * respondBytesWriter inside execute is important: execute's block closes
     * the upstream response only after ExoPlayer has received the body.
     */
    private suspend fun io.ktor.server.application.ApplicationCall.streamWithRetry(
        videoId: String,
        range: String?
    ) {
        var target = extractor.streamTarget(videoId)
        openUpstream(target.url, range).execute { upstream ->
            if (upstream.status == HttpStatusCode.Forbidden || upstream.status == HttpStatusCode.Gone) {
                upstream.bodyAsChannel().cancel(
                    CancellationException("Discarding expired upstream stream")
                )
                extractor.invalidateStreamTarget(videoId)
                target = extractor.streamTarget(videoId)
                openUpstream(target.url, range).execute { retry ->
                    streamResponse(retry, target.contentType)
                }
            } else {
                streamResponse(upstream, target.contentType)
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.streamResponse(
        upstream: HttpResponse,
        contentType: String
    ) {
        this.response.status(upstream.status)
        upstream.headers[HttpHeaders.ContentRange]?.let {
            response.headers.append(HttpHeaders.ContentRange, it)
        }
        response.headers.append(HttpHeaders.AcceptRanges, "bytes")
        response.headers.append(HttpHeaders.CacheControl, "private, max-age=60")
        respondBytesWriter(
            contentType = ContentType.parse(contentType),
            status = upstream.status
        ) {
            upstream.bodyAsChannel().copyTo(this)
        }
    }

    private suspend fun openUpstream(url: String, range: String?) =
        client.prepareGet(url) {
            range?.let { clientHeader(HttpHeaders.Range, it) }
            clientHeader(HttpHeaders.Accept, "*/*")
            clientHeader(HttpHeaders.AcceptEncoding, "identity")
            clientHeader(HttpHeaders.UserAgent, "WearsicServer/1.0")
        }

    /**
     * Send a JSON error only when the response has not started yet. If the
     * failure happened mid-stream, the response is already committed and a
     * second respond() would raise another server error; log it instead.
     */
    private suspend fun io.ktor.server.application.ApplicationCall.safeRespondError(
        status: HttpStatusCode,
        message: String
    ) {
        try {
            respond(status, ErrorResponse(message))
        } catch (secondary: Exception) {
            logger.warn("Could not send audio error response: {}", secondary.message)
        }
    }
}
