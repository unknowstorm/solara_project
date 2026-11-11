package com.unknowstorm.musicapp.provider.jamendo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JamendoResponse(
    val results: List<JamendoTrackDto> = emptyList()
)

@Serializable
internal data class JamendoTrackDto(
    val id: String,
    val name: String = "",
    val duration: String = "0",
    @SerialName("artist_name") val artistName: String = "",
    val image: String? = null,
    val audio: String? = null,
    @SerialName("license_ccurl") val licenseUrl: String? = null,
    val audiodownload: String? = null,
    @SerialName("audiodownload_allowed") val audiodownloadAllowed: Boolean? = null
)
