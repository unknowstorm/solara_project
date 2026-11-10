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
import kotlin.math.pow

private const val TOP_ARTISTS = 3
private const val PER_ARTIST = 10
private const val TOTAL = 30
private const val LIKE_WEIGHT = 3.0
private const val EXPLORE_POOL = 3   // how many artists after the top may be picked for "something new"
private const val DECAY = 0.985      // each older listen weighs 1.5% less than the next newer one

private val ArtistSuffix = Regex("""\s+(music|official|records|topic|beats)$""")
private val TitleExtras = Regex("""\s*[\(\[].*?[\)\]]""")

/** "@HasenChat Music" and "hasenchat" are the same artist. */
private fun artistKey(name: String): String {
    var k = name.trim().removePrefix("@").lowercase()
    repeat(2) {
        val stripped = k.replace(ArtistSuffix, "")
        if (stripped.length >= 3) k = stripped
    }
    return k
}

/** Same song on different services or under different ids. */
private fun trackKey(t: Track): String = artistKey(t.artist) + "|" + t.title.lowercase().replace(TitleExtras, "").trim()

private fun ProviderId.isOurs() = this == ProviderId.JAMENDO || this == ProviderId.AUDIUS

private fun <T> roundRobin(lists: List<List<T>>): List<T> {
    val result = mutableListOf<T>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) for (list in lists) list.getOrNull(i)?.let(result::add)
    return result
}

/**
 * “Similar to what you listen to” row.
 * Takes the most frequent artists from the history (a listen = 1, a like = 3),
 * searches their tracks on Jamendo and Audius and removes what is already familiar.
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

    /** Loads once per “signed in / guest” state; recalculated after signing in or out. */
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
            loadedFor = null // we will try again next time
        }
    }

    private suspend fun load() {
        loading = true
        try {
            val played = history.recent()
            val liked = likes.tracks.value.filter { it.provider.isOurs() }

            // normalized artist -> (how to display it, weight). History is newest first: older listens weigh less.
            val weights = LinkedHashMap<String, Pair<String, Double>>()
            fun add(artist: String, w: Double) {
                val shown = artist.trim()
                val key = artistKey(shown)
                if (key.isBlank()) return
                val old = weights[key]
                weights[key] = (old?.first ?: shown) to ((old?.second ?: 0.0) + w)
            }
            var rank = 0
            for (t in played) if (t.provider.isOurs()) add(t.artist, DECAY.pow(rank++))
            liked.forEach { add(it.artist, LIKE_WEIGHT) }

            val ranked = weights.values.sortedByDescending { it.second }.map { it.first }
            // the top artists plus one "discovery": a random one from the next few
            val top = ranked.take(TOP_ARTISTS) + ranked.drop(TOP_ARTISTS).take(EXPLORE_POOL).shuffled().take(1)
            artists = top
            if (top.isEmpty()) {
                tracks = emptyList()
                return
            }

            val seen = (played + liked).map { "${it.provider}:${it.id}" }.toSet()
            val perArtist = coroutineScope { top.map { name -> async { fetch(name, seen) } }.awaitAll() }
            val seenKeys = (played + liked).map(::trackKey).toSet()
            tracks = roundRobin(perArtist)
                .distinctBy { "${it.provider}:${it.id}" }
                // the same song on Jamendo and Audius, or one you already know under another id
                .distinctBy { trackKey(it) }
                .filter { trackKey(it) !in seenKeys }
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
            // first the artist's own tracks, then what was found nearby
            .sortedByDescending { artistKey(it.artist).contains(artistKey(artist)) }
            .take(PER_ARTIST)
    }
}
