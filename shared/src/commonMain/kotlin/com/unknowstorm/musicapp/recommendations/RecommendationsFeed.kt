package com.unknowstorm.musicapp.recommendations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unknowstorm.musicapp.history.HistoryRepository
import com.unknowstorm.musicapp.likes.LikesRepository
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.DeadTracks
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

private const val TOP_ARTISTS = 3
private const val PER_ARTIST = 10
private const val TOTAL = 30
private const val LIKE_WEIGHT = 3

private fun ProviderId.isOurs() = this == ProviderId.JAMENDO || this == ProviderId.AUDIUS

private fun <T> roundRobin(lists: List<List<T>>): List<T> {
    val result = mutableListOf<T>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) for (list in lists) list.getOrNull(i)?.let(result::add)
    return result
}

/**
 * Ряд «Похоже на то, что вы слушаете».
 * Берёт самых частых исполнителей из истории (прослушивание = 1, лайк = 3),
 * ищет их треки у Jamendo и Audius и убирает уже знакомое.
 */
class RecommendationsFeed(
    private val history: HistoryRepository,
    private val likes: LikesRepository,
    private val providers: List<MusicProvider>,
) {
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var artists by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    private var loadedFor: Boolean? = null

    /** Грузит один раз на состояние «вошёл / гость»; после входа или выхода пересчитывается. */
    suspend fun ensureLoaded(signedIn: Boolean) {
        if (loading || loadedFor == signedIn) return
        loadedFor = signedIn
        if (!signedIn) {
            tracks = emptyList()
            artists = emptyList()
            return
        }
        try {
            load()
        } catch (e: CancellationException) {
            loadedFor = null
            throw e
        } catch (e: Exception) {
            println("Solara: recommendations failed: ${e.message}")
            loadedFor = null // в следующий раз попробуем снова
        }
    }

    private suspend fun load() {
        loading = true
        try {
            val played = history.recent()
            val liked = likes.tracks.value.filter { it.provider.isOurs() }

            // ключ (ник без @ в нижнем регистре) -> (как показать, вес)
            val weights = LinkedHashMap<String, Pair<String, Int>>()
            fun add(artist: String, w: Int) {
                val shown = artist.trim()
                val key = shown.removePrefix("@").lowercase()
                if (key.isBlank()) return
                val old = weights[key]
                weights[key] = (old?.first ?: shown) to ((old?.second ?: 0) + w)
            }
            played.filter { it.provider.isOurs() }.forEach { add(it.artist, 1) }
            liked.forEach { add(it.artist, LIKE_WEIGHT) }

            val top = weights.values.sortedByDescending { it.second }.take(TOP_ARTISTS).map { it.first }
            artists = top
            if (top.isEmpty()) {
                tracks = emptyList()
                return
            }

            val seen = (played + liked).map { "${it.provider}:${it.id}" }.toSet()
            val perArtist = coroutineScope { top.map { name -> async { fetch(name, seen) } }.awaitAll() }
            tracks = roundRobin(perArtist)
                .distinctBy { "${it.provider}:${it.id}" }
                .take(TOTAL)
        } finally {
            loading = false
        }
    }

    private suspend fun fetch(artist: String, seen: Set<String>): List<Track> {
        val query = artist.removePrefix("@")
        val found = coroutineScope {
            providers.filter { it.id.isOurs() }.map { p ->
                async {
                    try {
                        withTimeoutOrNull(8_000) { p.search(query) } ?: emptyList()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        return roundRobin(found)
            .filter { it.provider.isOurs() && "${it.provider}:${it.id}" !in seen && !DeadTracks.isDead(it) }
            // сначала треки самого исполнителя, потом то, что нашлось рядом
            .sortedByDescending { it.artist.contains(query, ignoreCase = true) }
            .take(PER_ARTIST)
    }
}
