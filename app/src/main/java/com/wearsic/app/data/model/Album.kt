package com.wearsic.app.data.model

import kotlinx.serialization.Serializable

/** A YouTube playlist-style album that can be opened and streamed as a track list. */
@Serializable
data class Album(
    val id: String,
    val name: String,
    val uploader: String,
    val trackCount: Int,
    val thumbnailUrl: String = "",
    val url: String
)
