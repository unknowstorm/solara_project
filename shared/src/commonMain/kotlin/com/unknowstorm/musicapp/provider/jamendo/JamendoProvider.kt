package com.unknowstorm.musicapp.provider.jamendo

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.DownloadSource
import com.unknowstorm.musicapp.provider.LyricsRegistry
import com.unknowstorm.musicapp.provider.LyricsSource
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.provider.OfflineLookup
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
) : MusicProvider, DownloadSource, LyricsSource {

    override val id = ProviderId.JAMENDO

    init {
        LyricsRegistry.jamendo = this
    }

    override suspend fun search(query: String): List<Track> {
        // Jamendo sometimes answers with an empty list on a successful status, so we retry
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
        // A track saved on the phone plays from the file: no internet needed
        OfflineLookup.fileUri(track)?.let { return it }
        track.streamUrl?.takeIf { it.isNotBlank() }?.let { return it }
        repeat(MAX_ATTEMPTS) { attempt ->
            val url = fetchTracks { parameter("id", track.id) }.firstOrNull()?.audio
            if (!url.isNullOrBlank()) return url.secure()
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        error(S.jamendoNoAudioLinkFor(track.id))
    }

    /** The address for saving the track, only if the author allowed it. */
    override suspend fun resolveDownload(track: Track): String? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val dto = fetchTracks { parameter("id", track.id) }.firstOrNull()
            if (dto != null) {
                return if (dto.audiodownloadAllowed == true) dto.audiodownload?.takeIf { it.isNotBlank() }?.secure() else null
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        return null
    }

    /** Lyrics from Jamendo, only if the author published them; null if there are none. Never stored. */
    override suspend fun fetchLyrics(track: Track): String? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val dto = fetchTracks {
                parameter("id", track.id)
                parameter("include", "lyrics")
            }.firstOrNull()
            if (dto != null) return dto.lyrics?.replace("\r\n", "\n")?.trim()?.takeIf { it.isNotBlank() }
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
        }
        return null
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
        streamUrl = audio?.secure(),
        downloadable = audiodownloadAllowed
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
