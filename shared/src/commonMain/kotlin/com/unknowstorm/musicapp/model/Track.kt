package com.unknowstorm.musicapp.model

data class Track(
    val id: String,
    val provider: ProviderId,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val licenseUrl: String? = null,
    val streamUrl: String? = null,
    val downloadable: Boolean? = null,
)
