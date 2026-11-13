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

/** A row (shelf) of Spotify recommendations: title and items. */
data class SpotifyShelf(val title: String, val items: List<SpotifyItem>)

enum class LibraryStatus { IDLE, AUTHORIZING, LOADING, READY, ERROR }

/** A track of the user's library (Web API). Memory only. position is its index inside the playlist (for the playback context). */
data class SpotifyLibTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val position: Int = 0,
)

data class SpotifyLibPlaylist(
    val id: String,
    val uri: String,
    val name: String,
    val owner: String?,
    val trackCount: Int?,
    val imageUrl: String?,
    /** Spotify gives the contents only of playlists the user owns or collaborates on. */
    val readable: Boolean,
)

/** The imported library: lives only in memory and is erased on disconnect. */
data class SpotifyLibrary(
    val status: LibraryStatus = LibraryStatus.IDLE,
    val error: String? = null,
    val playlists: List<SpotifyLibPlaylist> = emptyList(),
    val saved: List<SpotifyLibTrack> = emptyList(),
    val savedTotal: Int? = null,
    val openPlaylist: SpotifyLibPlaylist? = null,
    val playlistTracks: List<SpotifyLibTrack>? = null,
    /** An encrypted refresh token is stored on this device, so the import repeats by itself. */
    val autoImport: Boolean = false,
)

/** Spotify state lives only in memory. Nothing from it is saved. */
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
    /** More tracks can be loaded for the open album or playlist. */
    val browseMore: Boolean = false,
    /** Exact number of tracks in the open album or playlist, as reported by Spotify. */
    val browseTotal: Int? = null,
    /** URI and card of the open collection (kept here so the Home screen can be recreated and still show it). */
    val browseUri: String? = null,
    val browseHeader: SpotifyItem? = null,
    val covers: Map<String, ImageBitmap> = emptyMap(),
    val shelves: List<SpotifyShelf>? = null,
    val library: SpotifyLibrary = SpotifyLibrary(),
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
    /** Opens specific content (spotify:track:…, spotify:album:…, spotify:playlist:…) in the Spotify app. */
    fun openInSpotify(uri: String)
    fun getSpotifyApp()
    fun loadRecommended()
    fun openItem(item: SpotifyItem)
    fun closeBrowse()
    /** Loads the next page of the open album or playlist (called when the list is scrolled to its end). */
    fun loadMoreBrowse() {}
    /** Track list of an album or playlist by URI (spotify:album:… / spotify:playlist:…). The result arrives in state.browse. */
    fun openByUri(uri: String) {}
    /** Plays an album or playlist from the given track index, so "next" follows the list. */
    fun playInContext(contextUri: String, index: Int, item: SpotifyItem? = null) {}
    /** Sign in (PKCE, read-only) and read the user's playlists and saved tracks into memory. */
    fun importLibrary() {}
    /** Called when the Spotify section opens: repeats the import without a tap if the user imported before. */
    fun autoImportLibrary() {}
    /** Deletes the stored token (the library already on screen stays until the app is closed). */
    fun disableAutoImport() {}
    fun openLibraryPlaylist(playlist: SpotifyLibPlaylist) {}
    fun closeLibraryPlaylist() {}
    /** Erases the token and everything imported from memory. */
    fun forgetLibrary() {}
}

val LocalSpotifyRemote = staticCompositionLocalOf<SpotifyRemote?> { null }

private val LINK = Regex("""open\.spotify\.com/(?:intl-[a-z-]+/)?(track|playlist|album)/([A-Za-z0-9]+)""")
private val URI = Regex("""^spotify:(track|playlist|album):([A-Za-z0-9]+)$""")

/** An open.spotify.com or spotify:... link turned into a URI for App Remote. Otherwise null. */
fun parseSpotifyLink(text: String): String? {
    val s = text.trim()
    URI.find(s)?.let { return s }
    return LINK.find(s)?.let { "spotify:${it.groupValues[1]}:${it.groupValues[2]}" }
}
