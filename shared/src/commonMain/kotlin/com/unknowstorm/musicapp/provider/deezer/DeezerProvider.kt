package com.unknowstorm.musicapp.provider.deezer

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.MusicProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/** Deezer's open API gives only 30-second previews; full audio is available only through their SDK. */
class DeezerProvider(
    private val client: HttpClient = HttpClient(),
) : MusicProvider {

    override val id = ProviderId.DEEZER

    override suspend fun search(query: String): List<Track> {
        val text = if (query.isBlank()) {
            client.get("$BASE_URL/chart/0/tracks") { parameter("limit", LIMIT) }.bodyAsText()
        } else {
            client.get("$BASE_URL/search") {
                parameter("q", query)
                parameter("limit", LIMIT)
            }.bodyAsText()
        }
        val response = json.decodeFromString<DeezerResponse>(text)
        response.error?.let { error(S.deezer(it.message.ifBlank { S.error2(it.code) })) }
        return response.data.filter { !it.preview.isNullOrBlank() }.map { it.toTrack() }
    }

    /** A fresh preview link: the old one lives about 15 minutes. */
    override suspend fun resolveStream(track: Track): String {
        val dto = json.decodeFromString<DeezerTrackDto>(client.get("$BASE_URL/track/${track.id}").bodyAsText())
        return dto.preview?.takeIf { it.isNotBlank() } ?: error(S.deezerNoPreviewForTrack(track.id))
    }

    private fun DeezerTrackDto.toTrack() = Track(
        id = id.toString(),
        provider = ProviderId.DEEZER,
        title = title,
        artist = artist?.name.orEmpty(),
        artworkUrl = album?.coverBig ?: album?.coverMedium,
        durationMs = PREVIEW_MS,
        // the id at the end of the link is needed by the player to refresh an expired link (see DeezerPreview)
        streamUrl = "$preview#deezer=$id",
    )

    private companion object {
        const val BASE_URL = "https://api.deezer.com"
        const val LIMIT = 30
        const val PREVIEW_MS = 30_000L
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
