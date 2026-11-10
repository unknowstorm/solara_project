package com.unknowstorm.musicapp.provider

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One source's answer: either tracks or the reason for an error. */
data class SourceResult(
    val provider: ProviderId,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

class MusicHub(
    val providers: List<MusicProvider>,
    private val timeoutMs: Long = 8_000,
) {
    /**
     * Searches in parallel in all sources (or only in [sources] if the list is not empty).
     * Results arrive as they become ready; a slow service does not delay the others.
     */
    fun search(query: String, sources: Set<ProviderId> = emptySet()): Flow<SourceResult> =
        channelFlow {
            providers
                .filter { sources.isEmpty() || it.id in sources }
                .forEach { provider ->
                    launch {
                        val result = try {
                            val tracks = withTimeoutOrNull(timeoutMs) { provider.search(query) }
                            if (tracks == null) {
                                SourceResult(provider.id, error = S.noResponse)
                            } else {
                                SourceResult(provider.id, tracks)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            SourceResult(provider.id, error = e.message ?: S.error)
                        }
                        send(result)
                    }
                }
        }
}

/** Interleaves tracks of different sources so that one service does not take the whole list. */
fun interleave(lists: List<List<Track>>): List<Track> {
    val result = ArrayList<Track>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) {
        for (list in lists) list.getOrNull(i)?.let { result.add(it) }
    }
    return result
}
