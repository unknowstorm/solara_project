package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.QueueItem
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Одна запись сохранённой очереди: только метаданные каталога, без ссылок на звук. */
@Serializable
data class SavedQueueEntry(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val durationMs: Long = 0L,
)

@Serializable
data class SavedQueue(val index: Int = 0, val items: List<SavedQueueEntry> = emptyList())

private val PersistedProviders = setOf(ProviderId.JAMENDO, ProviderId.AUDIUS, ProviderId.DEEZER)
private const val QUEUE_KEY = "saved_queue_v1"
private const val QUEUE_MAX = 200

fun SavedQueueEntry.toTrack(): Track? {
    val p = ProviderId.entries.firstOrNull { it.name == provider } ?: return null
    return Track(id, p, title, artist, artwork, durationMs)
}

/** Очередь между запусками. Хранится локально на устройстве, в облако не уходит. */
class QueueStore(private val texts: TextStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _saved = MutableStateFlow(load())
    val saved: StateFlow<SavedQueue?> = _saved.asStateFlow()

    private fun load(): SavedQueue? =
        texts.get(QUEUE_KEY)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<SavedQueue>(it) }.getOrNull() }
            ?.takeIf { it.items.isNotEmpty() }

    fun save(items: List<QueueItem>, currentIndex: Int) {
        val kept = items.withIndex().filter { it.value.provider in PersistedProviders }.take(QUEUE_MAX)
        if (kept.isEmpty()) return
        val entries = kept.map { (_, q) ->
            SavedQueueEntry(q.provider!!.name, q.mediaId, q.title, q.artist, q.artworkUrl, q.durationMs)
        }
        val index = kept.indexOfFirst { it.index == currentIndex }.coerceAtLeast(0)
        val next = SavedQueue(index, entries)
        if (next == _saved.value) return
        _saved.value = next
        texts.set(QUEUE_KEY, json.encodeToString(next))
    }

    fun clear() {
        _saved.value = null
        texts.set(QUEUE_KEY, "")
    }
}

/** Следит за очередью плеера и сохраняет её. Пустую очередь не записываем: её стирает только «Очистить». */
@Composable
fun QueuePersistence(audio: AudioBackend, store: QueueStore) {
    LaunchedEffect(audio, store) {
        combine(audio.queue, audio.state.map { it.currentIndex }.distinctUntilChanged()) { q, i -> q to i }
            .collectLatest { (q, i) ->
                if (q.isEmpty()) return@collectLatest
                delay(800)
                store.save(q, i)
            }
    }
}

/**
 * Восстанавливает очередь: берёт окно вокруг выбранного трека и заново получает ссылки на звук
 * (у Deezer они протухают). Что получить не удалось, пропускаем. Возвращает false, если играть нечего.
 */
suspend fun restoreQueue(
    saved: SavedQueue,
    startIndex: Int,
    providers: List<MusicProvider>,
    audio: AudioBackend,
): Boolean {
    val tracks = saved.items.mapNotNull { it.toTrack() }
    if (tracks.isEmpty()) return false
    val start = startIndex.coerceIn(0, tracks.lastIndex)
    val from = (start - 5).coerceAtLeast(0)
    val window = tracks.subList(from, minOf(tracks.size, from + 65))
    val gate = Semaphore(6)
    val resolved = coroutineScope {
        window.map { track ->
            async {
                val provider = providers.firstOrNull { it.id == track.provider } ?: return@async null
                val url = gate.withPermit {
                    try {
                        withTimeoutOrNull(6_000) { provider.resolveStream(track) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                url?.takeIf { it.isNotBlank() }?.let { track.copy(streamUrl = it) }
            }
        }.awaitAll()
    }.filterNotNull()
    if (resolved.isEmpty()) return false
    val chosen = tracks[start]
    val index = resolved.indexOfFirst { it.provider == chosen.provider && it.id == chosen.id }
    audio.playQueue(resolved, index.coerceAtLeast(0))
    return true
}
