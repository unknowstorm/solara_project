package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Ищет во всех источниках сразу и перемешивает результаты. Ошибка одного источника не ломает остальные. */
suspend fun searchAll(providers: List<MusicProvider>, query: String): List<Track> {
    var firstError: Exception? = null
    var failures = 0
    val lists = coroutineScope {
        providers.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Solara: ${provider.id} search failed: ${e.message}")
                    failures++
                    firstError = firstError ?: e
                    emptyList<Track>()
                }
            }
        }.awaitAll()
    }
    if (providers.isNotEmpty() && failures == providers.size) {
        throw firstError ?: IllegalStateException(S.sourcesUnavailable)
    }
    return interleave(lists)
}

private fun <T> interleave(lists: List<List<T>>): List<T> {
    val result = mutableListOf<T>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) {
        for (list in lists) list.getOrNull(i)?.let(result::add)
    }
    return result
}
