package com.unknowstorm.musicapp.spotify

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

class SpotifyHttpError(val code: Int, val detail: String? = null) :
    Exception("Spotify HTTP $code" + (detail?.let { ": $it" } ?: ""))

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

/**
 * Spotify Web API: read-only. The token and everything read live only in memory (see clear()).
 * Authorization code + PKCE: there is no client secret in the app. No refresh token is kept.
 */
class SpotifyWebApi(private val clientId: String, private val redirectUri: String) {
    private val http = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var token: String? = null
    @Volatile private var meId: String? = null
    /** Spotify profile name of the signed-in user. Memory only; erased by clear(). */
    @Volatile var accountName: String? = null
        private set

    fun clear() {
        token = null
        meId = null
        accountName = null
    }

    /** Returns the refresh token (null if Spotify did not issue one). */
    suspend fun exchangeCode(code: String, verifier: String): String? = tokenRequest(
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "client_id" to clientId,
        "code_verifier" to verifier,
    )

    /** New access token from a stored refresh token. Returns the refresh token to keep (Spotify may rotate it). */
    suspend fun refreshAccess(refreshToken: String): String =
        tokenRequest(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to clientId,
        ) ?: refreshToken

    private suspend fun tokenRequest(vararg form: Pair<String, String>): String? {
        val r = http.submitForm(
            "https://accounts.spotify.com/api/token",
            Parameters.build { form.forEach { (k, v) -> append(k, v) } },
        )
        if (!r.status.isSuccess()) {
            val detail = runCatching {
                val o = json.parseToJsonElement(r.bodyAsText()).jsonObject
                "token: " + o["error"].str() + " / " + o["error_description"].str()
            }.getOrNull()
            throw SpotifyHttpError(r.status.value, detail)
        }
        val o = json.parseToJsonElement(r.bodyAsText()).jsonObject
        token = o["access_token"].str() ?: throw SpotifyHttpError(0)
        val me = get("/me")
        meId = me["id"].str()
        accountName = me["display_name"].str()?.takeIf { it.isNotBlank() }
        return o["refresh_token"].str()
    }

    private suspend fun get(path: String): JsonObject {
        val t = token ?: throw SpotifyHttpError(401)
        val r = http.get("https://api.spotify.com/v1$path") { header(HttpHeaders.Authorization, "Bearer $t") }
        if (!r.status.isSuccess()) throw SpotifyHttpError(r.status.value, path.substringBefore('?'))
        return json.parseToJsonElement(r.bodyAsText()).jsonObject
    }

    suspend fun playlists(): List<SpotifyLibPlaylist> {
        val out = ArrayList<SpotifyLibPlaylist>()
        var offset = 0
        while (offset < MAX_PLAYLISTS) {
            val page = get("/me/playlists?limit=50&offset=$offset")
            val items = page["items"].arr() ?: break
            items.forEach { parsePlaylist(it.obj())?.let(out::add) }
            if (items.isEmpty() || page["next"].str() == null) break
            offset += 50
        }
        return out
    }

    /** Only for playlists the user owns or collaborates on: for others Spotify returns no contents. */
    suspend fun playlistTracks(id: String): List<SpotifyLibTrack> {
        val out = ArrayList<SpotifyLibTrack>()
        var offset = 0
        while (offset < MAX_PER_PLAYLIST) {
            val page = get("/playlists/$id/items?limit=50&offset=$offset")
            val items = page["items"].arr() ?: break
            items.forEachIndexed { i, e ->
                val eo = e.obj()
                // since February 2026 the track is under "item" (earlier: "track")
                parseTrack((eo?.get("item") ?: eo?.get("track")).obj(), offset + i)?.let(out::add)
            }
            if (items.isEmpty() || page["next"].str() == null) break
            offset += 50
        }
        return out
    }

    suspend fun savedTracks(): Pair<List<SpotifyLibTrack>, Int?> {
        val out = ArrayList<SpotifyLibTrack>()
        var offset = 0
        var total: Int? = null
        while (offset < MAX_SAVED) {
            val page = get("/me/tracks?limit=50&offset=$offset")
            total = page["total"].int() ?: total
            val items = page["items"].arr() ?: break
            items.forEachIndexed { i, e -> parseTrack(e.obj()?.get("track").obj(), offset + i)?.let(out::add) }
            if (items.isEmpty() || page["next"].str() == null) break
            offset += 50
        }
        return out to total
    }

    private fun image(images: JsonElement?): String? {
        val a = images.arr() ?: return null
        return (a.getOrNull(1) ?: a.firstOrNull()).obj()?.get("url").str()
    }

    private fun parsePlaylist(o: JsonObject?): SpotifyLibPlaylist? {
        if (o == null) return null
        val id = o["id"].str() ?: return null
        val owner = o["owner"].obj()
        val collaborative = (o["collaborative"] as? JsonPrimitive)?.booleanOrNull == true
        val total = o["items"].obj()?.get("total").int() ?: o["tracks"].obj()?.get("total").int()
        return SpotifyLibPlaylist(
            id = id,
            uri = o["uri"].str() ?: "spotify:playlist:$id",
            name = o["name"].str().orEmpty(),
            owner = owner?.get("display_name").str(),
            trackCount = total,
            imageUrl = image(o["images"]),
            readable = collaborative || (meId != null && owner?.get("id").str() == meId),
        )
    }

    private fun parseTrack(o: JsonObject?, position: Int): SpotifyLibTrack? {
        if (o == null) return null
        val uri = o["uri"].str() ?: return null
        if (!uri.startsWith("spotify:track:")) return null
        if ((o["is_local"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val artists = o["artists"].arr()?.mapNotNull { it.obj()?.get("name").str() }?.joinToString(", ").orEmpty()
        return SpotifyLibTrack(uri, o["name"].str().orEmpty(), artists, image(o["album"].obj()?.get("images")), position)
    }

    private companion object {
        const val MAX_PLAYLISTS = 100
        const val MAX_PER_PLAYLIST = 300
        const val MAX_SAVED = 300
    }
}
