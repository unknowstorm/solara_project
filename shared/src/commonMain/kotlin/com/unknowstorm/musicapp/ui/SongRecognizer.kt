package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.auth.supabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A recognized song. The links come from the recognition service; any of them may be missing. */
data class RecognizedSong(
    val title: String,
    val artist: String,
    val album: String?,
    val spotifyUrl: String?,
)

sealed interface RecognizeOutcome {
    class Found(val song: RecognizedSong) : RecognizeOutcome
    data object NotFound : RecognizeOutcome
    data object NeedSignIn : RecognizeOutcome
    data object RateLimit : RecognizeOutcome
    data object Unavailable : RecognizeOutcome
    data object Failed : RecognizeOutcome
}

@Serializable
private data class RecognizeLinks(val spotify: String? = null)

@Serializable
private data class RecognizeReply(
    val found: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val links: RecognizeLinks? = null,
    val error: String? = null,
)

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Sends a short clip to the Edge Function `recognize-song`. The recognition service token lives only in the function,
 * the clip is not stored: it goes to the recognition service and is forgotten.
 */
class SongRecognizer(private val client: SupabaseClient = supabase) {

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun recognize(audio: ByteArray, mime: String): RecognizeOutcome {
        return try {
            val resp = client.functions.invoke(
                function = "recognize-song",
                body = buildJsonObject {
                    put("audio", Base64.encode(audio))
                    put("mime", mime)
                },
            )
            fromReply(resp.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: recognize failed: ${e.message}")
            // A non-2xx reply may arrive as an exception: look for the code in its text
            val text = (e.message ?: "") + " " + e.toString()
            when {
                "rate_limit" in text || "429" in text -> RecognizeOutcome.RateLimit
                "quota" in text || "503" in text || "not_configured" in text || "upstream" in text -> RecognizeOutcome.Unavailable
                "\"auth\"" in text || "401" in text -> RecognizeOutcome.NeedSignIn
                else -> RecognizeOutcome.Failed
            }
        }
    }

    private fun fromReply(body: String): RecognizeOutcome {
        val reply = runCatching { lenientJson.decodeFromString<RecognizeReply>(body) }.getOrNull()
            ?: return RecognizeOutcome.Failed
        return when {
            reply.error == "rate_limit" -> RecognizeOutcome.RateLimit
            reply.error == "auth" -> RecognizeOutcome.NeedSignIn
            reply.error != null -> RecognizeOutcome.Unavailable
            reply.found && reply.title.isNotBlank() -> RecognizeOutcome.Found(
                RecognizedSong(reply.title, reply.artist, reply.album?.takeIf { it.isNotBlank() }, reply.links?.spotify),
            )
            else -> RecognizeOutcome.NotFound
        }
    }
}
