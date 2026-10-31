package com.unknowstorm.musicapp.provider.deezer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DeezerResponse(
    val data: List<DeezerTrackDto> = emptyList(),
    val error: DeezerError? = null,
)

@Serializable
internal data class DeezerError(
    val message: String = "",
    val code: Int = 0,
)

@Serializable
internal data class DeezerTrackDto(
    val id: Long = 0,
    val title: String = "",
    val duration: Long = 0,
    val preview: String? = null,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumDto? = null,
)

@Serializable
internal data class DeezerArtistDto(val name: String = "")

@Serializable
internal data class DeezerAlbumDto(
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("cover_medium") val coverMedium: String? = null,
)
