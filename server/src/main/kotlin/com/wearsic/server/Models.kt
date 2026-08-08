package com.wearsic.server

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val version: String,
    val uptimeSeconds: Long
)

@Serializable
data class TrackDto(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationMs: Long,
    val thumbnailUrl: String
)

@Serializable
data class SearchResponse(val results: List<TrackDto>)

@Serializable
data class SuggestionResponse(val suggestions: List<String>)

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val trackCount: Int,
    val thumbnailUrl: String? = null
)

@Serializable
data class PlaylistWithTracksDto(
    val id: String,
    val name: String,
    val tracks: List<TrackDto>
)

@Serializable
data class CreatePlaylistRequest(val name: String)

@Serializable
data class ErrorResponse(val error: String)
