package com.unknowstorm.musicapp.provider.audius

import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.MusicProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AudiusProvider(
    private val apiKey: String,
    private val client: HttpClient = createClient()
) : MusicProvider {

    override val id = ProviderId.AUDIUS

    override suspend fun search(query: String): List<Track> {
        val tracks = if (query.isBlank()) {
            fetch("/tracks/trending") { parameter("time", "week") }
        } else {
            fetch("/tracks/search") { parameter("query", query) }
        }
        // Private tracks and tracks unavailable for streaming are not shown
        return tracks
            .filter { it.isStreamable != false && it.access?.stream != false }
            .map { it.toTrack() }
    }

    override suspend fun resolveStream(track: Track): String =
        track.streamUrl?.takeIf { it.isNotBlank() } ?: streamUrl(track.id)

    private suspend fun fetch(path: String, block: HttpRequestBuilder.() -> Unit): List<AudiusTrackDto> {
        val response: AudiusResponse = client.get("$BASE_URL$path") {
            parameter("app_name", APP_NAME)
            parameter("api_key", apiKey)
            parameter("limit", 30)
            block()
        }.body()
        return response.data
    }

    // This address redirects to an mp3 on an Audius node; Media3 follows the redirect itself
    private fun streamUrl(id: String) = "$BASE_URL/tracks/$id/stream?app_name=$APP_NAME"

    private fun AudiusTrackDto.toTrack() = Track(
        id = id,
        provider = ProviderId.AUDIUS,
        title = title,
        artist = user?.name?.takeIf { it.isNotBlank() } ?: user?.handle.orEmpty(),
        artworkUrl = artwork?.medium ?: artwork?.large ?: artwork?.small,
        durationMs = duration * 1000L,
        streamUrl = streamUrl(id)
    )

    private companion object {
        const val BASE_URL = "https://api.audius.co/v1"
        const val APP_NAME = "Solara"

        fun createClient() = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
        }
    }
}

@Serializable
internal data class AudiusResponse(val data: List<AudiusTrackDto> = emptyList())

@Serializable
internal data class AudiusTrackDto(
    val id: String,
    val title: String = "",
    val duration: Int = 0,
    val artwork: AudiusArtworkDto? = null,
    val user: AudiusUserDto? = null,
    @SerialName("is_streamable") val isStreamable: Boolean? = null,
    val access: AudiusAccessDto? = null,
)

@Serializable
internal data class AudiusArtworkDto(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null,
)

@Serializable
internal data class AudiusUserDto(val name: String = "", val handle: String = "")

@Serializable
internal data class AudiusAccessDto(val stream: Boolean? = null, val download: Boolean? = null)
