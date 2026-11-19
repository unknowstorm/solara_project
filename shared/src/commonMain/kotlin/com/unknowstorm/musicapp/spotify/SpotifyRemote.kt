package com.unknowstorm.musicapp.spotify

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/** Turns the read-only queue view of the Spotify player on or off. */
const val SPOTIFY_QUEUE_ENABLED = true

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
    /** The full-screen list of Liked Songs is open (the library block shows only the latest few). */
    val savedOpen: Boolean = false,
    val openPlaylist: SpotifyLibPlaylist? = null,
    val playlistTracks: List<SpotifyLibTrack>? = null,
    /** An encrypted refresh token is stored on this device, so the import repeats by itself. */
    val autoImport: Boolean = false,
    /** Spotify profile name of the signed-in user (memory only; null if Spotify did not give one). */
    val accountName: String? = null,
)

/** Repeat modes as Spotify reports them: off, the whole playlist/album, a single track. */
enum class SpotifyRepeat { OFF, CONTEXT, TRACK }

enum class QueueStatus { OFF, NEEDS_CONSENT, AUTHORIZING, LOADING, READY, ERROR }

/** The user's Spotify queue (Web API, read-only): what is playing and what is queued after it. Memory only. */
data class SpotifyQueue(
    val status: QueueStatus = QueueStatus.OFF,
    val error: String? = null,
    val nowPlaying: SpotifyLibTrack? = null,
    val next: List<SpotifyLibTrack> = emptyList(),
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
    /** Shuffle and repeat as Spotify reports them, and whether Spotify currently allows changing them. */
    val shuffling: Boolean = false,
    val repeat: SpotifyRepeat = SpotifyRepeat.OFF,
    val canShuffle: Boolean = true,
    val canRepeatContext: Boolean = true,
    val canRepeatTrack: Boolean = true,
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
    val queue: SpotifyQueue = SpotifyQueue(),
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
    /** Appends a track to the end of the Spotify queue (App Remote can only append: no reordering, no removing). */
    fun queueUri(uri: String) {}
    fun setShuffle(on: Boolean) {}
    fun setRepeat(mode: SpotifyRepeat) {}
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
    /** Settings card: reads only the profile name (no library) if a stored token exists. Memory only. */
    fun refreshProfile() {}
    /** Deletes the stored token (the library already on screen stays until the app is closed). */
    fun disableAutoImport() {}
    fun openLibraryPlaylist(playlist: SpotifyLibPlaylist) {}
    fun openSavedTracks() {}
    fun closeSavedTracks() {}
    fun closeLibraryPlaylist() {}
    /** Erases the token and everything imported from memory. */
    fun forgetLibrary() {}
    /** Queue panel opened: loads the queue if the user already allowed it, otherwise asks for consent. */
    fun openQueue() {}
    fun refreshQueue() {}
    /** Queue panel closed: stops refreshing and erases the queue from memory. */
    fun closeQueue() {}
    /** Separate one-time consent for reading the queue (read-only). */
    fun grantQueueAccess() {}
    /** Deletes the stored queue token and the queue from memory. */
    fun forgetQueue() {}
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
