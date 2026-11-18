package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A music file chosen on the phone. Only the address and tags are stored, the file itself stays where it is. */
@Serializable
data class LocalFileEntry(
    val uri: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
)

/** The address of the file doubles as the track id and as the stream address (the player opens content:// itself). */
fun LocalFileEntry.toTrack() = Track(
    id = uri,
    provider = ProviderId.LOCAL,
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    streamUrl = uri,
)

private const val LOCAL_KEY = "local_files_v1"

/** The list of files from the phone. It lives only on this device and never goes to the cloud. */
class LocalLibrary(private val texts: TextStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _files = MutableStateFlow(load())
    val files: StateFlow<List<LocalFileEntry>> = _files.asStateFlow()

    private fun load(): List<LocalFileEntry> =
        texts.get(LOCAL_KEY)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<List<LocalFileEntry>>(it) }.getOrNull() }
            ?: emptyList()

    fun add(entries: List<LocalFileEntry>) {
        val known = _files.value.map { it.uri }.toSet()
        val fresh = entries.filter { it.uri !in known }.distinctBy { it.uri }
        if (fresh.isNotEmpty()) update(_files.value + fresh)
    }

    fun remove(uri: String) = update(_files.value.filterNot { it.uri == uri })

    private fun update(list: List<LocalFileEntry>) {
        _files.value = list
        texts.set(LOCAL_KEY, json.encodeToString(list))
    }
}

/** Opens the system file picker for audio. Returns the function that opens it. */
@Composable
expect fun rememberAudioFilesPicker(onPicked: (List<LocalFileEntry>) -> Unit): () -> Unit
