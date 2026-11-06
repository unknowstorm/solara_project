package com.unknowstorm.musicapp.playlists

import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.auth.supabase
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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

data class Playlist(val id: String, val name: String)

/** Трек в плейлисте вместе с позицией (по ней его можно удалить). */
data class PlaylistEntry(val position: Int, val track: Track)

@Serializable
private data class PlaylistRow(val id: String, val name: String)

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

/** Плейлисты пользователя в облаке (таблицы `playlists` и `playlist_items`, RLS: только свои). */
class PlaylistsRepository(
    auth: AuthRepository,
    private val client: SupabaseClient = supabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var userId: String? = null

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    /** Плейлисты, новые сверху. */
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
                if (userId == owner) _playlists.value = rows.map { Playlist(it.id, it.name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Solara: playlists refresh failed: ${e.message}")
            }
        }
    }

    /** Создаёт плейлист из треков. Deezer и прочее, что база не принимает, пропускается. */
    suspend fun create(name: String, tracks: List<Track>): Result<Unit> {
        val clean = name.trim().take(100)
        if (clean.isEmpty()) return Result.failure(Exception("Введите название"))
        val items = tracks.filter { it.provider.canBeSaved() }
        if (items.isEmpty()) return Result.failure(Exception("В очереди нет треков Jamendo или Audius"))
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
            _playlists.update { listOf(Playlist(row.id, row.name)) + it }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist create failed: ${e.message}")
            // Не оставляем пустой плейлист, если не записались треки
            createdId?.let { id ->
                runCatching { client.from("playlists").delete { filter { eq("id", id) } } }
            }
            Result.failure(Exception("Не удалось сохранить плейлист"))
        }
    }

    /** Треки плейлиста по порядку (без ссылок на звук: их берёт playLiked). */
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

    /** Состав плейлиста по порядку; null, если загрузить не удалось. */
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
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: playlist entry remove failed: ${e.message}")
        false
    }

    suspend fun rename(playlistId: String, name: String): Result<Unit> {
        val clean = name.trim().take(100)
        if (clean.isEmpty()) return Result.failure(Exception("Введите название"))
        return try {
            client.from("playlists").update(PlaylistInsert(clean)) { filter { eq("id", playlistId) } }
            _playlists.update { list -> list.map { if (it.id == playlistId) it.copy(name = clean) else it } }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist rename failed: ${e.message}")
            Result.failure(Exception("Не удалось переименовать"))
        }
    }

    /** Добавляет треки в конец плейлиста, уже лежащие в нём пропускает. Возвращает, сколько добавлено. */
    suspend fun addTracks(playlistId: String, tracks: List<Track>): Result<Int> {
        val existing = loadEntries(playlistId)
            ?: return Result.failure(Exception("Не удалось загрузить плейлист"))
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
            Result.success(fresh.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Solara: playlist add failed: ${e.message}")
            Result.failure(Exception("Не удалось добавить в плейлист"))
        }
    }

    fun delete(playlistId: String) {
        _playlists.update { list -> list.filterNot { it.id == playlistId } }
        scope.launch {
            try {
                client.from("playlists").delete { filter { eq("id", playlistId) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh()
            }
        }
    }
}
