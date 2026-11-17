package com.unknowstorm.musicapp.playlists

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.auth.supabase
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** [cover] is null (no own cover) or a version tag of the uploaded photo like "u1a2b3c". */
data class Playlist(val id: String, val name: String, val cover: String? = null)

/** A track in a playlist together with its position (it can be removed by it). */
data class PlaylistEntry(val position: Int, val track: Track)

@Serializable
private data class PlaylistRow(val id: String, val name: String, val cover: String? = null)

/** No default value on purpose: null must be sent to the server to clear the cover. */
@Serializable
private data class CoverUpdate(val cover: String?)

@Serializable
private data class ArtworkRow(@SerialName("artwork_url") val artworkUrl: String? = null)

@Serializable
private data class PlaylistInsert(val name: String)

@Serializable
private data class ItemRow(
    @SerialName("playlist_id") val playlistId: String,
    val position: Int,
    val provider: String,
    @SerialName("track_id") val trackId: String,
    val title: String,
    val artist: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
)

private fun ProviderId.canBeSaved() = this == ProviderId.JAMENDO || this == ProviderId.AUDIUS

private const val COVER_BUCKET = "playlist-covers"
private const val PREVIEW_COUNT = 4

/** The version is in the file name, so a changed cover is never served from a CDN cache. */
private fun coverPath(uid: String, playlistId: String, marker: String) = "$uid/$playlistId-$marker.jpg"

/** User playlists in the cloud (tables `playlists` and `playlist_items`, RLS: own rows only). */
class PlaylistsRepository(
    auth: AuthRepository,
    private val client: SupabaseClient = supabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var userId: String? = null

    /** Downloaded covers: the key is "<playlist id>/<version tag>". */
    private val coverCache = mutableMapOf<String, ByteArray>()

    /** Cover URLs of the first tracks (for the collage), by playlist id. */
    private val previewCache = mutableMapOf<String, List<String>>()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    /** Playlists, newest first. */
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init {
        scope.launch {
            auth.state.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> if (userId != s.userId) {
                        userId = s.userId
                        _playlists.value = emptyList()
                        _signedIn.value = true
                        refresh()
                    }
                    is AuthState.Guest -> {
                        userId = null
                        _signedIn.value = false
                        _playlists.value = emptyList()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        val owner = userId ?: return
        scope.launch {
            try {
                val rows = client.from("playlists")
                    .select { order("created_at", Order.DESCENDING); limit(100) }
                    .decodeList<PlaylistRow>()
                if (userId == owner) _playlists.value = rows.map { Playlist(it.id, it.name, it.cover) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Solara: playlists refresh failed: ${e.message}")
            }
        }
    }

    /** Creates a playlist from tracks. Deezer and anything else the database does not accept is skipped. */
    suspend fun create(name: String, tracks: List<Track>): Result<Unit> {
        val clean = name.trim().take(100)
        if (clean.isEmpty()) return Result.failure(Exception(S.enterAName))
        val items = tracks.filter { it.provider.canBeSaved() }
        if (items.isEmpty()) return Result.failure(Exception(S.theQueueHasNoJamendo))
        var createdId: String? = null
        return try {
            val row = client.from("playlists")
                .insert(PlaylistInsert(clean)) { select() }
                .decodeSingle<PlaylistRow>()
            createdId = row.id
            client.from("playlist_items").insert(
                items.mapIndexed { i, t ->
                    ItemRow(row.id, i, t.provider.name, t.id, t.title, t.artist, t.artworkUrl, t.durationMs)
                }
            )
            previewCache.remove(row.id)
            _playlists.update { listOf(Playlist(row.id, row.name, row.cover)) + it }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist create failed: ${e.message}")
            // We do not leave an empty playlist if the tracks were not written
            createdId?.let { id ->
                runCatching { client.from("playlists").delete { filter { eq("id", id) } } }
            }
            Result.failure(Exception(S.couldNotSaveThePlaylist))
        }
    }

    /** Playlist tracks in order (without audio links: playLiked fetches them). */
    suspend fun loadTracks(playlistId: String): List<Track> = try {
        client.from("playlist_items")
            .select {
                filter { eq("playlist_id", playlistId) }
                order("position", Order.ASCENDING)
            }
            .decodeList<ItemRow>()
            .mapNotNull { r ->
                ProviderId.entries.firstOrNull { it.name == r.provider }?.let { p ->
                    Track(r.trackId, p, r.title, r.artist, r.artworkUrl, r.durationMs)
                }
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: playlist load failed: ${e.message}")
        emptyList()
    }

    /** Playlist contents in order; null if loading failed. */
    suspend fun loadEntries(playlistId: String): List<PlaylistEntry>? = try {
        client.from("playlist_items")
            .select {
                filter { eq("playlist_id", playlistId) }
                order("position", Order.ASCENDING)
            }
            .decodeList<ItemRow>()
            .mapNotNull { r ->
                ProviderId.entries.firstOrNull { it.name == r.provider }?.let { p ->
                    PlaylistEntry(r.position, Track(r.trackId, p, r.title, r.artist, r.artworkUrl, r.durationMs))
                }
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: playlist entries load failed: ${e.message}")
        null
    }

    suspend fun removeEntry(playlistId: String, position: Int): Boolean = try {
        client.from("playlist_items").delete {
            filter {
                eq("playlist_id", playlistId)
                eq("position", position)
            }
        }
        previewCache.remove(playlistId)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: playlist entry remove failed: ${e.message}")
        false
    }

    suspend fun rename(playlistId: String, name: String): Result<Unit> {
        val clean = name.trim().take(100)
        if (clean.isEmpty()) return Result.failure(Exception(S.enterAName))
        return try {
            client.from("playlists").update(PlaylistInsert(clean)) { filter { eq("id", playlistId) } }
            _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(name = clean) else it } }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist rename failed: ${e.message}")
            Result.failure(Exception(S.couldNotRename))
        }
    }

    /** Appends tracks to the end of the playlist, skipping those already in it. Returns how many were added. */
    suspend fun addTracks(playlistId: String, tracks: List<Track>): Result<Int> {
        val existing = loadEntries(playlistId)
            ?: return Result.failure(Exception(S.couldNotLoadThePlaylist))
        val have = existing.map { "${it.track.provider}:${it.track.id}" }.toSet()
        val fresh = tracks
            .filter { it.provider.canBeSaved() && "${it.provider}:${it.id}" !in have }
            .distinctBy { "${it.provider}:${it.id}" }
        if (fresh.isEmpty()) return Result.success(0)
        val start = (existing.maxOfOrNull { it.position } ?: -1) + 1
        return try {
            client.from("playlist_items").insert(
                fresh.mapIndexed { i, t ->
                    ItemRow(playlistId, start + i, t.provider.name, t.id, t.title, t.artist, t.artworkUrl, t.durationMs)
                }
            )
            previewCache.remove(playlistId)
            Result.success(fresh.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist add failed: ${e.message}")
            Result.failure(Exception(S.couldNotAddToThe))
        }
    }

    /** The cover photo of the playlist by its version tag; null if there is none or it could not be downloaded. */
    suspend fun loadCover(playlist: Playlist): ByteArray? {
        val marker = playlist.cover ?: return null
        val uid = userId ?: return null
        val key = "${playlist.id}/$marker"
        coverCache[key]?.let { return it }
        val bytes = try {
            client.storage.from(COVER_BUCKET).downloadPublic(coverPath(uid, playlist.id, marker))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist cover load failed: ${e.message}")
            null
        } ?: return null
        coverCache[key] = bytes
        return bytes
    }

    /** Uploads a new cover (JPEG, already a square). The previous file is removed. */
    suspend fun setCover(playlistId: String, jpeg: ByteArray): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception(S.noActiveSession))
        val old = _playlists.value.firstOrNull { it.id == playlistId }?.cover
        val marker = "u" + Random.nextInt(0, Int.MAX_VALUE).toString(36)
        val bucket = client.storage.from(COVER_BUCKET)
        var uploaded = false
        return try {
            bucket.upload(coverPath(uid, playlistId, marker), jpeg) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            uploaded = true
            client.from("playlists").update(CoverUpdate(marker)) { filter { eq("id", playlistId) } }
            coverCache["$playlistId/$marker"] = jpeg
            _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(cover = marker) else it } }
            if (old != null) runCatching { bucket.delete(coverPath(uid, playlistId, old)) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist cover save failed: ${e.message}")
            // The database did not accept the new tag: we do not leave an orphan file
            if (uploaded) runCatching { bucket.delete(coverPath(uid, playlistId, marker)) }
            val m = e.message.orEmpty()
            Result.failure(
                Exception(
                    if ("exceeded the maximum allowed size" in m || "too large" in m.lowercase()) S.thePhotoIsTooLarge
                    else S.failed(m.take(120))
                )
            )
        }
    }

    /** Removes the own cover: the collage of track covers returns. */
    suspend fun removeCover(playlistId: String): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception(S.noActiveSession))
        val old = _playlists.value.firstOrNull { it.id == playlistId }?.cover ?: return Result.success(Unit)
        return try {
            client.from("playlists").update(CoverUpdate(null)) { filter { eq("id", playlistId) } }
            _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(cover = null) else it } }
            coverCache.remove("$playlistId/$old")
            runCatching { client.storage.from(COVER_BUCKET).delete(coverPath(uid, playlistId, old)) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist cover remove failed: ${e.message}")
            Result.failure(Exception(S.failed(e.message.orEmpty().take(120))))
        }
    }

    /** Cover URLs of the first tracks (up to 4 different ones) for the collage; cached. */
    suspend fun loadPreview(playlistId: String): List<String> {
        previewCache[playlistId]?.let { return it }
        return try {
            val urls = client.from("playlist_items")
                .select(Columns.list("artwork_url")) {
                    filter { eq("playlist_id", playlistId) }
                    order("position", Order.ASCENDING)
                    limit(12)
                }
                .decodeList<ArtworkRow>()
                .mapNotNull { it.artworkUrl?.takeIf { u -> u.isNotBlank() } }
                .distinct()
                .take(PREVIEW_COUNT)
            previewCache[playlistId] = urls
            urls
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist preview load failed: ${e.message}")
            emptyList()
        }
    }

    fun delete(playlistId: String) {
        val gone = _playlists.value.firstOrNull { it.id == playlistId }
        _playlists.update { list -> list.filterNot { it.id == playlistId } }
        previewCache.remove(playlistId)
        scope.launch {
            try {
                client.from("playlists").delete { filter { eq("id", playlistId) } }
                val uid = userId
                val marker = gone?.cover
                if (uid != null && marker != null) {
                    runCatching { client.storage.from(COVER_BUCKET).delete(coverPath(uid, playlistId, marker)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh()
            }
        }
    }
}
