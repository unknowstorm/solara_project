package com.unknowstorm.musicapp.history

import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
import com.unknowstorm.musicapp.auth.supabase
import com.unknowstorm.musicapp.likes.asTrack
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.PlaybackState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of the `listening_history` table (user_id is set by the database: auth.uid()). */
@Serializable
private data class HistoryRow(
    val provider: String,
    @SerialName("track_id") val trackId: String,
    val title: String,
    val artist: String,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
)

private const val LISTEN_MS = 30_000L

/**
 * Writes to the cloud a track that was really listened to: 30 seconds of playback
 * (or half, if the track is shorter than a minute). Seeking forward does not count.
 * Only Jamendo and Audius: the database does not accept anything else.
 */
class HistoryRepository(
    auth: AuthRepository,
    audio: AudioBackend,
    private val client: SupabaseClient = supabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val userId = MutableStateFlow<String?>(null)
    private val _recorded = MutableStateFlow(0)
    /** Grows with every listen written to the cloud: the recommendations use it to know the history changed. */
    val recorded: StateFlow<Int> = _recorded

    private var key: String? = null
    private var played = 0L
    private var lastPos = 0L
    private var logged = false

    init {
        scope.launch {
            auth.state.collect { s ->
                when (s) {
                    is AuthState.SignedIn -> userId.value = s.userId
                    is AuthState.Guest -> userId.value = null
                    else -> Unit
                }
            }
        }
        scope.launch { audio.state.collect { onState(it) } }
    }

    /** Recently listened tracks, newest first (without audio links). */
    suspend fun recent(count: Long = 200L): List<Track> = try {
        client.from("listening_history")
            .select { order("played_at", Order.DESCENDING); limit(count) }
            .decodeList<HistoryRow>()
            .mapNotNull { r ->
                ProviderId.entries.firstOrNull { it.name == r.provider }?.let {
                    Track(r.trackId, it, r.title, r.artist, r.artworkUrl, r.durationMs)
                }
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: history load failed: ${e.message}")
        emptyList()
    }

    private fun onState(s: PlaybackState) {
        val track = s.asTrack()
        val k = track?.let { "${it.provider}:${it.id}" }
        if (k != key) {
            key = k
            played = 0L
            lastPos = s.positionMs
            logged = false
        } else {
            val delta = s.positionMs - lastPos
            if (s.isPlaying && delta in 0..1_500) played += delta
            // The track started over (repeat of a single track): we count a new listen
            if (s.positionMs < 2_000 && lastPos > 5_000) {
                played = 0L
                logged = false
            }
            lastPos = s.positionMs
        }
        if (track == null || logged) return
        val need = if (s.durationMs > 0) minOf(LISTEN_MS, s.durationMs / 2) else LISTEN_MS
        if (played >= need) {
            logged = true
            record(track)
        }
    }

    private fun record(track: Track) {
        if (track.provider != ProviderId.JAMENDO && track.provider != ProviderId.AUDIUS) return
        if (userId.value == null) return
        scope.launch {
            try {
                client.from("listening_history").insert(
                    HistoryRow(
                        track.provider.name, track.id, track.title, track.artist,
                        track.artworkUrl, track.durationMs,
                    )
                )
                _recorded.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Solara: history insert failed: ${e.message}")
            }
        }
    }
}
