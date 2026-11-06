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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Строка таблицы `listening_history` (user_id проставляет база: auth.uid()). */
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
 * Пишет в облако трек, который реально прослушан: 30 секунд воспроизведения
 * (или половина, если трек короче минуты). Перемотка вперёд не засчитывается.
 * Только Jamendo и Audius: остальное база не принимает.
 */
class HistoryRepository(
    auth: AuthRepository,
    audio: AudioBackend,
    private val client: SupabaseClient = supabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val userId = MutableStateFlow<String?>(null)

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
            // Трек пошёл заново (повтор одного трека): считаем новое прослушивание
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Solara: history insert failed: ${e.message}")
            }
        }
    }
}
