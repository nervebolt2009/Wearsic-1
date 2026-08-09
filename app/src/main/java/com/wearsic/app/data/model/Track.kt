package com.wearsic.app.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Data class representing a music track from the server
 */
@Serializable
data class Track(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationMs: Long,
    val thumbnailUrl: String
) {
    /**
     * Format duration from milliseconds to mm:ss or h:mm:ss
     */
    fun formatDuration(): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }
}

/**
 * Search response from server
 */
@Serializable
data class SearchResponse(
    val results: List<Track>
)

/**
 * Suggestions response from server
 */
@Serializable
data class SuggestionResponse(
    val suggestions: List<String>
)

/**
 * Health check response from server
 */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null,
    val uptimeSeconds: Long? = null
)

/**
 * Playlist data class
 */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val thumbnailUrl: String? = null
)

/**
 * Playlist with tracks
 */
@Serializable
data class PlaylistWithTracks(
    val id: String,
    val name: String,
    val tracks: List<Track>
)
