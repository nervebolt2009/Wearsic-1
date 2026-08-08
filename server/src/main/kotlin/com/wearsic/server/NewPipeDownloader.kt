package com.wearsic.server

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URI

class NewPipeDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URI(request.url()).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                values.forEach { value -> setRequestProperty(name, value) }
            }
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
        private const val USER_AGENT = "WearsicServer/1.0 (Android Termux; NewPipe Extractor)"
    }
}
