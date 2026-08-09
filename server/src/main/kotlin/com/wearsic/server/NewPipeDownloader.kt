package com.wearsic.server

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URI

/**
 * Runtime YouTube session credentials. The watch app pushes its cookie here via
 * POST /api/config/youtube-cookie, and the value is persisted in SQLite so it
 * survives restarts. The environment variable is only a fallback default.
 */
object YoutubeSession {
    private val envCookie = System.getenv("WEARSIC_YOUTUBE_COOKIE").orEmpty().trim()

    /** Fallback cookie from the environment, never cleared by the app. */
    val envFallbackCookie: String get() = envCookie

    @Volatile
    var cookie: String = envCookie
}

class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URI(request.url()).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            // Preserve extractor headers, but keep runtime browser credentials and
            // the current UA authoritative. NewPipe can provide its own values.
            request.headers().forEach { (name, values) ->
                if (!name.equals("User-Agent", ignoreCase = true) &&
                    !name.equals("Cookie", ignoreCase = true)
                ) {
                    values.forEach { value -> setRequestProperty(name, value) }
                }
            }
            setRequestProperty("User-Agent", USER_AGENT)
            // YouTube may require a valid browser session when the server IP is
            // challenged. The app can supply one at runtime; the env var is only
            // a fallback default for operators who prefer file-based config.
            YoutubeSession.cookie
                .takeIf { it.isNotBlank() }
                ?.let { setRequestProperty("Cookie", it) }
            request.dataToSend()?.let { body ->
                doOutput = true
                if (getRequestProperty("Content-Type") == null) {
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                outputStream.use { it.write(body) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Response(code, connection.responseMessage.orEmpty(), connection.headerFields, body, connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // Use a current browser UA; the old product-specific UA was frequently
        // challenged by YouTube before the extractor could resolve audio formats.
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
