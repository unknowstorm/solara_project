package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A song found by "What's playing?": title, artist and album only. No links to other services, no covers. */
@Serializable
data class RecognizedEntry(val title: String, val artist: String, val album: String? = null)

/**
 * The list of recognized songs. Kept only on this phone (the same local storage as the saved queue), never uploaded.
 * The newest song is first; a repeated song moves to the top instead of being added twice.
 */
object RecognizedHistory {
    private const val KEY = "recognized_songs_v1"
    private const val MAX = 100
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RecognizedEntry.serializer())
    private var texts: TextStore? = null
    private val _items = MutableStateFlow<List<RecognizedEntry>>(emptyList())
    val items: StateFlow<List<RecognizedEntry>> = _items.asStateFlow()

    /** Called once from the main screen: connects the local storage and loads the saved list. */
    fun attach(store: TextStore) {
        if (texts === store) return
        texts = store
        _items.value = store.get(KEY)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: emptyList()
    }

    fun add(song: RecognizedSong) {
        val entry = RecognizedEntry(song.title.trim(), song.artist.trim(), song.album)
        val same = { e: RecognizedEntry -> e.title.equals(entry.title, ignoreCase = true) && e.artist.equals(entry.artist, ignoreCase = true) }
        save((listOf(entry) + _items.value.filterNot(same)).take(MAX))
    }

    fun remove(entry: RecognizedEntry) = save(_items.value - entry)

    fun clear() = save(emptyList())

    private fun save(list: List<RecognizedEntry>) {
        _items.value = list
        texts?.set(KEY, if (list.isEmpty()) "" else json.encodeToString(serializer, list))
    }
}

/** The "Recognized" section of the library. */
@Composable
internal fun RecognizedContent() {
    val items by RecognizedHistory.items.collectAsState()
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                tr(
                    "Songs you find with the microphone button in Search will appear here.",
                    "Тут з’являтимуться пісні, знайдені кнопкою мікрофона на вкладці «Пошук».",
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.tracks(items.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { RecognizedHistory.clear() }) { Text(tr("Clear", "Очистити")) }
            }
        }
        items(items, key = { "${it.title}|${it.artist}" }) { e ->
            Row(
                Modifier.then(animatedRow()).fillMaxWidth()
                    .clickable { PendingSearch.query = "${e.artist} ${e.title}".trim() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { RecognizeGlyph(Modifier.size(24.dp), MaterialTheme.colorScheme.onSecondaryContainer) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(e.artist.takeIf { it.isNotBlank() }, e.album?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { RecognizedHistory.remove(e) }) { Text("✕", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/**
 * A search that another screen asked for (a recognized song tapped in the library). MainScreen switches to the Search tab,
 * TrackListScreen runs the query and clears it. Lives only in memory.
 */
internal object PendingSearch {
    var query by mutableStateOf<String?>(null)
}
