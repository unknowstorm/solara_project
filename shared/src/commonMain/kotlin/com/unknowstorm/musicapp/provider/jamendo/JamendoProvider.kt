package com.unknowstorm.musicapp.provider.jamendo

import com.unknowstorm.musicapp.i18n.S
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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class JamendoProvider(
    private val clientId: String,
    private val client: HttpClient = createClient()
) : MusicProvider {

    override val id = ProviderId.JAMENDO

    override suspend fun search(query: String): List<Track> {
        // Jamendo иногда отвечает пустым списком при успешном статусе, поэтому повторяем
        repeat(MAX_ATTEMPTS) { attempt ->
            val tracks = fetchTracks {
                parameter("limit", 30)
                if (query.isBlank()) {
                    parameter("order", "popularity_week")
                } else {
                    parameter("namesearch", query)
                }
            }.filter { !it.audio.isNullOrBlank() }.map { it.toTrack() }
            if (tracks.isNotEmpty()) return tracks
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        return emptyList()
    }

    override suspend fun resolveStream(track: Track): String {
        track.streamUrl?.takeIf { it.isNotBlank() }?.let { return it }
        repeat(MAX_ATTEMPTS) { attempt ->
            val url = fetchTracks { parameter("id", track.id) }.firstOrNull()?.audio
            if (!url.isNullOrBlank()) return url.secure()
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        error(S.jamendoNoAudioLinkFor(track.id))
    }

    private suspend fun fetchTracks(block: HttpRequestBuilder.() -> Unit): List<JamendoTrackDto> {
        val response: JamendoResponse = client.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            block()
        }.body()
        return response.results
    }

    private fun String.secure() =
        if (startsWith("http://")) "https://" + removePrefix("http://") else this

    private fun JamendoTrackDto.toTrack() = Track(
        id = id,
        provider = ProviderId.JAMENDO,
        title = name,
        artist = artistName,
        artworkUrl = image?.secure()?.replace("width=300", "width=600"),
        durationMs = (duration.toLongOrNull() ?: 0L) * 1000,
        licenseUrl = licenseUrl,
        streamUrl = audio?.secure()
    )

    private companion object {
        const val BASE_URL = "https://api.jamendo.com/v3.0"
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 300L

        fun createClient() = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}
