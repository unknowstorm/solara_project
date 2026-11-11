package com.unknowstorm.musicapp.likes

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.auth.supabase
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.PlaybackState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of the `likes` table (user_id is set by the database itself: auth.uid()). */
@Serializable
private data class LikeRow(
    val provider: String,
    @SerialName("track_id") val trackId: String,
    val title: String,
    val artist: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
)

private fun LikeRow.toTrack(): Track? {
    val p = ProviderId.entries.firstOrNull { it.name == provider } ?: return null
    // We do not store the audio link: it expires, a fresh one comes from MusicProvider.resolveStream
    return Track(trackId, p, title, artist, artworkUrl, durationMs)
}

private fun Track.toRow() = LikeRow(provider.name, id, title, artist, artworkUrl, durationMs)

/** Current player track as a Track (null if the source or id is unknown). */
fun PlaybackState.asTrack(): Track? {
    val id = mediaId ?: return null
    val p = provider ?: return null
    val name = title ?: return null
    return Track(id, p, name, artist ?: "", artworkUrl, durationMs)
}

fun List<Track>.containsTrack(track: Track?): Boolean =
    track != null && any { it.provider == track.provider && it.id == track.id }

/**
 * User likes in the cloud (table `likes`, RLS: everyone sees only their own).
 * They work the same for any source, because the pair (provider, track_id) is stored.
 */
class LikesRepository(
    auth: AuthRepository,
    private val client: SupabaseClient = supabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var userId: String? = null

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    /** Liked tracks, newest first. */
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        scope.launch {
            auth.state.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> if (userId != s.userId) {
                        userId = s.userId
                        // Another account: we do not show the old list for a single second
                        _tracks.value = emptyList()
                        _signedIn.value = true
                        refresh()
                    }
                    is AuthState.Guest -> {
                        userId = null
                        _signedIn.value = false
                        _tracks.value = emptyList()
                    }
                    else -> Unit // Loading / Error: leave as is (for example, no network)
                }
            }
        }
    }

    /** Pulls likes from the cloud. */
    fun refresh() {
        val owner = userId ?: return
        scope.launch {
            try {
                val rows = client.from("likes")
                    .select { order("liked_at", Order.DESCENDING); limit(200) }
                    .decodeList<LikeRow>()
                // Another user may have signed in while the request was running: then we discard the response
                if (userId == owner) _tracks.value = rows.mapNotNull { it.toTrack() }.filter { it.provider != ProviderId.DEEZER }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(S.couldNotLoadLikes)
            }
        }
    }

    /** Sets or removes a like. The UI updates immediately and rolls back on a network error. */
    fun toggle(track: Track) {
        if (!_signedIn.value) return
        // Deezer: only 30-second previews, not part of the shared likes
        // Local files stay on the phone: their names must not go to the cloud
        if (track.provider == ProviderId.DEEZER || track.provider == ProviderId.LOCAL) return
        val wasLiked = _tracks.value.containsTrack(track)
        _tracks.update { list ->
            if (wasLiked) list.filterNot { it.provider == track.provider && it.id == track.id }
            else listOf(track) + list
        }
        scope.launch {
            mutex.withLock {
                try {
                    if (wasLiked) {
                        client.from("likes").delete {
                            filter {
                                eq("provider", track.provider.name)
                                eq("track_id", track.id)
                            }
                        }
                    } else {
                        try {
                            client.from("likes").insert(track.toRow())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Already liked from another device: this is not an error
                            if (e.message?.contains("duplicate", ignoreCase = true) != true) throw e
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _tracks.update { list ->
                        if (wasLiked) listOf(track) + list
                        else list.filterNot { it.provider == track.provider && it.id == track.id }
                    }
                    showError(if (wasLiked) S.couldNotRemoveTheLike else S.couldNotAddTheLike)
                }
            }
        }
    }

    private fun showError(message: String) {
        _error.value = message
        scope.launch {
            delay(4_000)
            if (_error.value == message) _error.value = null
        }
    }
}
