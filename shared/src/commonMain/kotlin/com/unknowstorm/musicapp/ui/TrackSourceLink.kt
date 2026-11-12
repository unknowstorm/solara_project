package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AudiusTrackPage(val data: AudiusTrackLink? = null)

@Serializable
private data class AudiusTrackLink(val permalink: String? = null)

private val linkClient by lazy {
    HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
        }
    }
}

/** Audius track page: the address is taken from the `permalink` field of the API answer. */
private suspend fun audiusTrackUrl(id: String): String? = try {
    val page: AudiusTrackPage = linkClient.get("https://api.audius.co/v1/tracks/$id") {
        parameter("app_name", "Solara")
    }.body()
    page.data?.permalink?.takeIf { it.startsWith("/") }?.let { "https://audius.co$it" }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    println("Solara: audius track link failed: ${e.message}")
    null
}

/**
 * "Open on Jamendo / Audius": a direct link from the track to its page on the source service
 * (required by the Jamendo API Terms, clause 4.1). Shown only for Jamendo and Audius tracks.
 */
@Composable
fun TrackSourceLink(provider: ProviderId?, trackId: String?, modifier: Modifier = Modifier) {
    if (trackId.isNullOrBlank() || (provider != ProviderId.JAMENDO && provider != ProviderId.AUDIUS)) return
    val openLink = rememberExternalLinkOpener()
    val scope = rememberCoroutineScope()
    val name = if (provider == ProviderId.JAMENDO) "Jamendo" else "Audius"
    TextButton(
        onClick = {
            scope.launch {
                val url = if (provider == ProviderId.JAMENDO) {
                    "https://www.jamendo.com/track/$trackId"
                } else {
                    audiusTrackUrl(trackId)
                }
                if (url != null) openLink(url)
            }
        },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        modifier = modifier,
    ) {
        Text(S.openOn(name), style = MaterialTheme.typography.labelLarge)
    }
}
