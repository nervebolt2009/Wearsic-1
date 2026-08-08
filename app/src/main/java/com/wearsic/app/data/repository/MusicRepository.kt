package com.wearsic.app.data.repository

import com.wearsic.app.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Repository for music API calls to the Ktor backend
 * Screens should never call the network client directly - use this repository
 */
class MusicRepository {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            if (apiKey.isNotBlank()) header("X-Wearsic-Key", apiKey)
        }
    }
    
    private var baseUrl: String = ""
    private var apiKey: String = ""
    
    /**
     * Set the server base URL
     */
    fun setServerUrl(url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        baseUrl = when {
            cleanUrl.isBlank() -> ""
            cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> cleanUrl
            else -> "https://$cleanUrl"
        }
    }

    fun setApiKey(key: String) {
        apiKey = key.trim()
    }

    fun getApiKey(): String = apiKey

    /**
     * The current backend exposes application routes below /api while keeping
     * /health at the server root.
     */
    private fun apiUrl(path: String): String = "$baseUrl/api$path"

    private suspend inline fun <reified T> getApiOrRoot(
        path: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {}
    ): T {
        return try {
            client.get(apiUrl(path), configure).body()
        } catch (error: ClientRequestException) {
            if (error.response.status.value != 404) throw error
            client.get("$baseUrl$path", configure).body()
        }
    }

    private suspend fun postApiOrRoot(
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {}
    ) {
        try {
            client.post(apiUrl(path), configure)
        } catch (error: ClientRequestException) {
            if (error.response.status.value != 404) throw error
            client.post("$baseUrl$path", configure)
        }
    }

    private suspend fun deleteApiOrRoot(path: String) {
        try {
            client.delete(apiUrl(path))
        } catch (error: ClientRequestException) {
            if (error.response.status.value != 404) throw error
            client.delete("$baseUrl$path")
        }
    }

    private fun JsonObject.toSearchResponse(): SearchResponse {
        val tracks = (this["results"]?.jsonArray ?: this["items"]?.jsonArray)
            ?.mapNotNull { it.jsonObject.toTrack() }
            .orEmpty()
        return SearchResponse(tracks)
    }

    private fun JsonObject.toTrack(): Track? {
        val videoId = this["videoId"]?.jsonPrimitive?.contentOrNull
            ?: this["id"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val title = this["title"]?.jsonPrimitive?.contentOrNull ?: return null
        val uploader = this["uploader"]?.jsonPrimitive?.contentOrNull
            ?: this["artist"]?.jsonPrimitive?.contentOrNull
            ?: "Unknown artist"
        val durationMs = this["durationMs"]?.jsonPrimitive?.longOrNull
            ?: parseDuration(this["duration"]?.jsonPrimitive?.contentOrNull)
        val thumbnailUrl = this["thumbnailUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return Track(videoId, title, uploader, durationMs, thumbnailUrl)
    }

    private fun parseDuration(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val parts = value.split(':').mapNotNull { it.toLongOrNull() }
        return when {
            parts.size == 2 -> (parts[0] * 60 + parts[1]) * 1000
            parts.size == 3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
            else -> value.toLongOrNull()?.let { if (it < 100_000L) it * 1000 else it } ?: 0L
        }
    }

    private fun JsonElement.toSuggestions(): List<String> {
        return when (this) {
            is JsonArray -> mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonObject -> this["suggestions"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.contentOrNull
            }.orEmpty()
            else -> emptyList()
        }
    }

    private fun requireServerUrl(): Result<String> {
        return if (baseUrl.isBlank()) {
            Result.failure(IllegalStateException("Configure your server URL in Settings first."))
        } else {
            Result.success(baseUrl)
        }
    }
    
    /**
     * Get the current server URL
     */
    fun getServerUrl(): String = baseUrl
    
    /**
     * Test connection to server
     */
    suspend fun testConnection(): Result<HealthResponse> {
        val url = requireServerUrl().getOrElse { return Result.failure(it) }
        return try {
            val response = client.get("$url/health").body<HealthResponse>()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Search for tracks
     */
    suspend fun search(query: String): Result<SearchResponse> {
        val url = requireServerUrl().getOrElse { return Result.failure(it) }
        return try {
            val payload = getApiOrRoot<JsonObject>("/search") {
                parameter("q", query)
            }
            Result.success(payload.toSearchResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get search suggestions (autocomplete)
     */
    suspend fun getSuggestions(query: String): Result<SuggestionResponse> {
        val url = requireServerUrl().getOrElse { return Result.failure(it) }
        return try {
            val payload = getApiOrRoot<JsonElement>("/suggestions") {
                parameter("q", query)
            }
            Result.success(SuggestionResponse(payload.toSuggestions()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get related tracks for autoplay
     */
    suspend fun getRelatedTracks(videoId: String): Result<SearchResponse> {
        return try {
            val payload = getApiOrRoot<JsonObject>("/related/$videoId")
            Result.success(payload.toSearchResponse())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get stream URL for a track
     */
    fun getStreamUrl(videoId: String): String {
        check(baseUrl.isNotBlank()) { "Configure your server URL in Settings first." }
        return apiUrl("/stream/$videoId")
    }
    
    /**
     * Get favorites
     */
    suspend fun getFavorites(): Result<List<Track>> {
        val url = requireServerUrl().getOrElse { return Result.failure(it) }
        return try {
            val response = getApiOrRoot<List<Track>>("/favorites")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add to favorites
     */
    suspend fun addToFavorites(track: Track): Result<Unit> {
        return try {
            postApiOrRoot("/favorites") {
                setBody(track)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove from favorites
     */
    suspend fun removeFromFavorites(videoId: String): Result<Unit> {
        return try {
            deleteApiOrRoot("/favorites/$videoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get playlists
     */
    suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            val response = getApiOrRoot<List<Playlist>>("/playlists")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get playlist with tracks
     */
    suspend fun getPlaylist(id: String): Result<PlaylistWithTracks> {
        return try {
            val response = getApiOrRoot<PlaylistWithTracks>("/playlists/$id")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get playlist from external URL (YouTube playlist)
     */
    suspend fun getExternalPlaylist(url: String): Result<PlaylistWithTracks> {
        return try {
            val response = getApiOrRoot<PlaylistWithTracks>("/playlist") {
                parameter("url", url)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get channel uploads
     */
    suspend fun getChannelUploads(url: String, page: Int = 1): Result<PlaylistWithTracks> {
        return try {
            val response = getApiOrRoot<PlaylistWithTracks>("/channel") {
                parameter("url", url)
                parameter("page", page)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create a new playlist
     */
    suspend fun createPlaylist(name: String): Result<Playlist> {
        return try {
            val response = try {
                client.post(apiUrl("/playlists")) {
                    setBody(mapOf("name" to name))
                }.body<Playlist>()
            } catch (error: ClientRequestException) {
                if (error.response.status.value != 404) throw error
                client.post("$baseUrl/playlists") {
                    setBody(mapOf("name" to name))
                }.body<Playlist>()
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add track to playlist
     */
    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> {
        return try {
            postApiOrRoot("/playlists/$playlistId/tracks") {
                setBody(track)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove track from playlist
     */
    suspend fun removeTrackFromPlaylist(playlistId: String, videoId: String): Result<Unit> {
        return try {
            deleteApiOrRoot("/playlists/$playlistId/tracks/$videoId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Close the HTTP client
     */
    fun close() {
        client.close()
    }
}
