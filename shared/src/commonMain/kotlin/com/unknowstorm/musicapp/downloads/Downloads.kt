package com.unknowstorm.musicapp.downloads

import androidx.compose.runtime.Composable
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.DownloadSource
import com.unknowstorm.musicapp.provider.MusicProvider
import com.unknowstorm.musicapp.provider.OfflineLookup
import com.unknowstorm.musicapp.ui.TextStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Platform part of downloads: files in the private folder of the app, a short message. */
interface DownloadPlatform {
    /** Downloads [url] into the private folder as [fileName], reports progress 0..1, returns the path of the file. */
    suspend fun download(url: String, fileName: String, onProgress: (Float) -> Unit): String
    fun exists(path: String): Boolean
    fun size(path: String): Long
    fun delete(path: String)
    fun toast(text: String)
}

@Composable
expect fun rememberDownloadPlatform(): DownloadPlatform

/** A track saved on the phone. Only tags and the path are stored here; the sound is in the file. */
@Serializable
data class DownloadedTrack(
    val provider: String,
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val coverPath: String? = null,
    val durationMs: Long = 0L,
    val path: String,
    val sizeBytes: Long = 0L,
)

/** The track as the player needs it: it plays straight from the file. */
fun DownloadedTrack.toPlayable() = Track(
    id = id,
    provider = ProviderId.entries.firstOrNull { it.name == provider } ?: ProviderId.JAMENDO,
    title = title,
    artist = artist,
    // The remote cover stays here so that likes and playlists never get a path from this phone
    artworkUrl = artwork,
    durationMs = durationMs,
    streamUrl = "file://$path",
    downloadable = true,
)

private const val DOWNLOADS_KEY = "downloads_v1"

/**
 * Saving tracks for listening without internet. Only for sources that allow it
 * (Jamendo, and only tracks whose authors allowed downloading). Files are in the private folder of the app.
 * Tracks are saved one at a time; progress is available per track.
 */
class DownloadManager(
    private val texts: TextStore,
    private val platform: DownloadPlatform,
    private val scope: CoroutineScope,
    private val providers: List<MusicProvider>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadedTrack>> = _items.asStateFlow()

    /** Key of a track that is being saved -> progress 0..1, or -1 while it waits or the size is unknown. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val jobs = HashMap<String, Job>()
    private val gate = Mutex()

    init {
        OfflineLookup.lookup = { fileUri(it) }
    }

    fun keyOf(track: Track) = "${track.provider.name}:${track.id}"
    fun keyOf(item: DownloadedTrack) = "${item.provider}:${item.id}"

    private fun load(): List<DownloadedTrack> =
        texts.get(DOWNLOADS_KEY)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<List<DownloadedTrack>>(it) }.getOrNull() }
            ?.filter { platform.exists(it.path) } // files could have been removed from outside
            ?: emptyList()

    private fun persist(list: List<DownloadedTrack>) {
        _items.value = list
        texts.set(DOWNLOADS_KEY, json.encodeToString(list))
    }

    /** The address of the saved file, if the track is on the phone. */
    fun fileUri(track: Track): String? {
        val key = keyOf(track)
        val item = _items.value.firstOrNull { keyOf(it) == key } ?: return null
        return if (platform.exists(item.path)) "file://${item.path}" else null
    }

    fun download(track: Track) {
        val key = keyOf(track)
        if (jobs.containsKey(key) || _items.value.any { keyOf(it) == key }) return
        setProgress(key, -1f)
        jobs[key] = scope.launch {
            try {
                gate.withLock { fetch(track, key) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                platform.toast(S.downloadFailed)
            } finally {
                jobs.remove(key)
                _progress.update { it - key }
            }
        }
    }

    fun cancel(key: String) {
        jobs[key]?.cancel()
    }

    fun remove(key: String) {
        cancel(key)
        val item = _items.value.firstOrNull { keyOf(it) == key } ?: return
        platform.delete(item.path)
        item.coverPath?.let { platform.delete(it) }
        persist(_items.value.filterNot { keyOf(it) == key })
    }

    private fun setProgress(key: String, value: Float) {
        _progress.update { it + (key to value) }
    }

    private suspend fun fetch(track: Track, key: String) {
        val source = providers.firstOrNull { it.id == track.provider } as? DownloadSource
        val url = source?.resolveDownload(track)
        if (url == null) {
            platform.toast(S.downloadNotAllowed)
            return
        }
        val name = "${track.provider.name.lowercase()}_${track.id}".filter { it.isLetterOrDigit() || it == '_' }
        val path = platform.download(url, "$name.mp3") { setProgress(key, it) }
        // The cover is kept too so that the list looks right without internet; if it fails, it is not a problem
        var cover: String? = null
        val art = track.artworkUrl
        if (!art.isNullOrBlank()) {
            try {
                cover = platform.download(art, "$name.jpg") { }
            } catch (e: CancellationException) {
                platform.delete(path)
                throw e
            } catch (e: Exception) {
                cover = null
            }
        }
        val item = DownloadedTrack(
            provider = track.provider.name,
            id = track.id,
            title = track.title,
            artist = track.artist,
            artwork = track.artworkUrl,
            coverPath = cover,
            durationMs = track.durationMs,
            path = path,
            sizeBytes = platform.size(path),
        )
        persist(_items.value.filterNot { keyOf(it) == key } + item)
    }
}
