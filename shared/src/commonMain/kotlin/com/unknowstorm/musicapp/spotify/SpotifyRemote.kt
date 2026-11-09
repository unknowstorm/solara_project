package com.unknowstorm.musicapp.spotify

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

data class SpotifyItem(
    val uri: String,
    val title: String,
    val subtitle: String? = null,
    val playable: Boolean = true,
    val hasChildren: Boolean = false,
    val imageKey: String? = null,
)

/** Ряд (полка) рекомендаций Spotify: заголовок и элементы. */
data class SpotifyShelf(val title: String, val items: List<SpotifyItem>)

/** Состояние Spotify живёт только в памяти. Ничего из него не сохраняется. */
data class SpotifyState(
    val appInstalled: Boolean = true,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val premium: Boolean? = null,
    val uri: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val cover: ImageBitmap? = null,
    val isPaused: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val saved: Boolean = false,
    val error: String? = null,
    val history: List<SpotifyItem> = emptyList(),
    val browse: List<SpotifyItem>? = null,
    val browseTitle: String? = null,
    val covers: Map<String, ImageBitmap> = emptyMap(),
    val shelves: List<SpotifyShelf>? = null,
)

interface SpotifyRemote {
    val state: StateFlow<SpotifyState>
    fun connect()
    fun disconnect()
    fun playUri(uri: String)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun toggleSaved()
    fun openSpotifyApp()
    /** Открывает конкретный контент (spotify:track:…, spotify:album:…, spotify:playlist:…) в приложении Spotify. */
    fun openInSpotify(uri: String)
    fun getSpotifyApp()
    fun loadRecommended()
    fun openItem(item: SpotifyItem)
    fun closeBrowse()
    /** Список треков альбома или плейлиста по URI (spotify:album:… / spotify:playlist:…). Результат приходит в state.browse. */
    fun openByUri(uri: String) {}
}

val LocalSpotifyRemote = staticCompositionLocalOf<SpotifyRemote?> { null }

private val LINK = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(track|playlist|album)/([A-Za-z0-9]+)""")
private val URI = Regex("""^spotify:(track|playlist|album):([A-Za-z0-9]+)$""")

/** Ссылка open.spotify.com или spotify:... в URI для App Remote. Иначе null. */
fun parseSpotifyLink(text: String): String? {
    val s = text.trim()
    URI.find(s)?.let { return s }
    return LINK.find(s)?.let { "spotify:${it.groupValues[1]}:${it.groupValues[2]}" }
}
