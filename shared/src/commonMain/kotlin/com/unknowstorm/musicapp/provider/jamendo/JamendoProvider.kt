package com.unknowstorm.musicapp.provider.jamendo

import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.MusicProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class JamendoProvider(
    private val clientId: String,
    private val client: HttpClient = createClient()
) : MusicProvider {

    override val id = ProviderId.JAMENDO

    override suspend fun search(query: String): List<Track> {
        val response: JamendoResponse = client.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("limit", 30)
            if (query.isBlank()) {
                parameter("order", "popularity_week")
            } else {
                parameter("namesearch", query)
            }
        }.body()
        return response.results.filter { !it.audio.isNullOrBlank() }.map { it.toTrack() }
    }

    override suspend fun resolveStream(track: Track): String {
        val response: JamendoResponse = client.get("$BASE_URL/tracks/") {
            parameter("client_id", clientId)
            parameter("format", "json")
            parameter("id", track.id)
        }.body()
        return response.results.firstOrNull()?.audio
            ?: error("Jamendo: нет ссылки на аудио для трека ${track.id}")
    }

    private fun JamendoTrackDto.toTrack() = Track(
        id = id,
        provider = ProviderId.JAMENDO,
        title = name,
        artist = artistName,
        artworkUrl = image,
        durationMs = (duration.toLongOrNull() ?: 0L) * 1000,
        licenseUrl = licenseUrl
    )

    private companion object {
        const val BASE_URL = "https://api.jamendo.com/v3.0"

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
