package com.unknowstorm.musicapp.provider.youtube

import com.unknowstorm.musicapp.auth.supabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A playlist of the user's own YouTube account (title and size only). */
@Serializable
data class YtPlaylist(val id: String, val title: String, val count: Int = 0)

class YtOverview(val playlists: List<YtPlaylist>, val liked: List<YouTubeVideo>)

/** code: unauthorized, revoked, quota, not_linked, failed. */
class YtLinkException(val code: String) : Exception(code)

@Serializable
private data class LinkReply(
    val error: String? = null,
    val url: String? = null,
    val linked: Boolean = false,
    val playlists: List<YtPlaylist> = emptyList(),
    val liked: List<YouTubeVideo> = emptyList(),
    val videos: List<YouTubeVideo> = emptyList(),
)

private val lenient = Json { ignoreUnknownKeys = true }

/**
 * The user's own YouTube account through the Edge Function `youtube-link`.
 * The token lives only on the server. Everything returned here is for the YouTube section only:
 * it is kept in memory and is never added to likes, playlists, history, recommendations or the queue.
 */
class YouTubeLinkRepository(private val client: SupabaseClient = supabase) {
    private suspend fun call(action: String, id: String? = null): LinkReply {
        val text = try {
            client.functions.invoke(
                function = "youtube-link",
                body = buildJsonObject {
                    put("action", action)
                    if (id != null) put("id", id)
                },
            ).bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            throw YtLinkException(if (m.contains("401") || m.contains("unauthorized")) "unauthorized" else "failed")
        }
        val reply = try {
            lenient.decodeFromString<LinkReply>(text)
        } catch (e: Exception) {
            throw YtLinkException("failed")
        }
        reply.error?.let { throw YtLinkException(it) }
        return reply
    }

    suspend fun isLinked(): Boolean = call("status").linked

    /** The Google consent page address. Open it in the browser. */
    suspend fun start(): String = call("start").url ?: throw YtLinkException("failed")

    suspend fun overview(): YtOverview = call("overview").let { YtOverview(it.playlists, it.liked) }

    suspend fun playlist(id: String): List<YouTubeVideo> = call("playlist", id).videos

    /** Revokes the token at Google and deletes it from our server. */
    suspend fun disconnect() {
        call("disconnect")
    }
}
