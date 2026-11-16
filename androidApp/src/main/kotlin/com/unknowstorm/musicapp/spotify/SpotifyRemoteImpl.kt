package com.unknowstorm.musicapp.spotify

import com.unknowstorm.musicapp.i18n.S
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val AUTH_REQUEST = 7341
private const val LIB_REDIRECT = "solara://spotify-library"

class SpotifyRemoteImpl(private val activity: Activity) : SpotifyRemote {
    companion object {
        @Volatile var current: SpotifyRemoteImpl? = null
    }

    fun pauseNow() { remote?.playerApi?.pause() }

    private val _state = MutableStateFlow(SpotifyState(appInstalled = SpotifyAppRemote.isSpotifyInstalled(activity)))
    override val state: StateFlow<SpotifyState> = _state.asStateFlow()

    private var remote: SpotifyAppRemote? = null
    private var sub: Subscription<PlayerState>? = null
    private var lastTrackUri: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("spotify_remote", 0)
    private var silent = false
    private var ignoreIdle = false
    private var optimisticKey: String? = null
    // The track chosen in a list: while a collection starts, Spotify first plays track 1, which must not enter "Recent"
    private var expectedUri: String? = null
    private var expectedName: String? = null
    private val clearExpected = Runnable { expectedUri = null; expectedName = null }
    private val connectTimeout = Runnable {
        _state.update {
            if (it.connecting) it.copy(
                connecting = false,
                error = if (silent) null else S.spotifyDidNotRespondOpen,
            ) else it
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            _state.update { s ->
                if (s.connected && !s.isPaused && s.durationMs > 0)
                    s.copy(positionMs = (s.positionMs + 500).coerceAtMost(s.durationMs))
                else s
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun connect() {
        silent = false
        ignoreIdle = false
        startConnect()
    }

    /** Silent connection at launch: only if the user has connected Spotify before and has not disconnected it. */
    fun autoConnect() {
        if (!prefs.getBoolean("auto", false)) return
        if (remote?.isConnected == true || _state.value.connecting) return
        silent = true
        ignoreIdle = true
        // The permission was already granted during the manual connection: skip the sign-in window and connect directly
        connectRemote()
    }

    private fun startConnect() {
        if (remote?.isConnected == true) return
        if (!SpotifyAppRemote.isSpotifyInstalled(activity)) {
            _state.update { it.copy(appInstalled = false) }
            return
        }
        if (SpotifyConfig.CLIENT_ID.isBlank()) {
            _state.update { it.copy(error = S.spotifyClientIdIsNot) }
            return
        }
        _state.update { it.copy(appInstalled = true, connecting = true, error = null) }
        handler.removeCallbacks(connectTimeout)
        // We open the permission window from Solara while it is on screen: Android blocks it from the background
        // Spotify removed the implicit grant (Type.TOKEN): we use the authorization code flow with PKCE
        val request = AuthorizationRequest.Builder(
            SpotifyConfig.CLIENT_ID, AuthorizationResponse.Type.CODE, SpotifyConfig.REDIRECT_URI,
        )
            .setScopes(arrayOf("app-remote-control"))
            .setCustomParam("code_challenge_method", "S256")
            .setCustomParam("code_challenge", newPkceChallenge())
            .build()
        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST, request)
    }

    /**
     * PKCE challenge for the consent request. We do not exchange the code from the response for a token (App Remote only needs
     * the user's consent), so the verifier string is not needed after the request and is not stored anywhere.
     */
    private fun newPkceChallenge(): String {
        val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        val bytes = ByteArray(64).also { java.security.SecureRandom().nextBytes(it) }
        val verifier = android.util.Base64.encodeToString(bytes, flags)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(digest, flags)
    }

    /** Called from MainActivity. The code from the response is not used or stored. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != AUTH_REQUEST) return
        val response = AuthorizationClient.getResponse(resultCode, data)
        android.util.Log.d("SpotifyAuth", "type=${response.type} error=${response.error}")
        if (response.type == AuthorizationResponse.Type.CODE) {
            connectRemote()
        } else {
            _state.update {
                it.copy(connecting = false, error = S.spotifyDidNotConfirmAccess)
            }
        }
    }

    private fun connectRemote() {
        if (remote?.isConnected == true) return
        if (!SpotifyAppRemote.isSpotifyInstalled(activity)) {
            _state.update { it.copy(appInstalled = false) }
            return
        }
        if (SpotifyConfig.CLIENT_ID.isBlank()) {
            _state.update { it.copy(error = S.spotifyClientIdIsNot) }
            return
        }
        _state.update { it.copy(appInstalled = true, connecting = true, error = null) }
        handler.removeCallbacks(connectTimeout)
        handler.postDelayed(connectTimeout, 20_000)
        val params = ConnectionParams.Builder(SpotifyConfig.CLIENT_ID)
            .setRedirectUri(SpotifyConfig.REDIRECT_URI)
            .showAuthView(!silent)
            .build()
        SpotifyAppRemote.connect(activity, params, object : Connector.ConnectionListener {
            override fun onConnected(r: SpotifyAppRemote) {
                handler.removeCallbacks(connectTimeout)
                remote = r
                prefs.edit().putBoolean("auto", true).apply()
                current = this@SpotifyRemoteImpl
                runCatching { activity.startService(Intent(activity, SpotifyTaskGuard::class.java)) }
                _state.update { it.copy(connecting = false, connected = true, error = null) }
                r.userApi.capabilities.setResultCallback { c ->
                    _state.update { it.copy(premium = c.canPlayOnDemand) }
                }
                subscribe(r)
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 500)
            }

            override fun onFailure(t: Throwable) {
                handler.removeCallbacks(connectTimeout)
                val text = when (t.javaClass.simpleName) {
                    "CouldNotFindSpotifyApp" -> S.theSpotifyAppWasNot
                    "NotLoggedInException" -> S.signInToYourAccount
                    "UserNotAuthorizedException" -> S.allowAccessInTheSpotify
                    "AuthenticationFailedException" -> S.spotifyRejectedTheAppCheck
                    else -> S.couldNotConnectToSpotify
                }
                _state.update { it.copy(connecting = false, connected = false, error = if (silent) null else text) }
            }
        })
    }

    private fun subscribe(r: SpotifyAppRemote) {
        sub?.cancel()
        val subscription = r.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { ps ->
            // After a silent auto-connect we do not show what Spotify was playing earlier: we wait until something actually starts playing
            if (ignoreIdle) {
                if (ps.isPaused) return@setEventCallback
                ignoreIdle = false
            }
            val track = ps.track
            _state.update {
                it.copy(
                    uri = track?.uri,
                    title = track?.name,
                    artist = track?.artist?.name,
                    isPaused = ps.isPaused,
                    positionMs = ps.playbackPosition,
                    durationMs = track?.duration ?: 0L,
                    error = null,
                    shuffling = if (holdShuffle) it.shuffling else (ps.playbackOptions?.isShuffling ?: false),
                    repeat = when (ps.playbackOptions?.repeatMode) {
                        Repeat.ONE -> SpotifyRepeat.TRACK
                        Repeat.ALL -> SpotifyRepeat.CONTEXT
                        else -> SpotifyRepeat.OFF
                    },
                    canShuffle = ps.playbackRestrictions?.canToggleShuffle ?: true,
                    canRepeatContext = ps.playbackRestrictions?.canRepeatContext ?: true,
                    canRepeatTrack = ps.playbackRestrictions?.canRepeatTrack ?: true,
                )
            }
            if (track != null && track.uri != lastTrackUri) {
                lastTrackUri = track.uri
                val keepCover = optimisticKey != null && optimisticKey == track.imageUri?.raw
                optimisticKey = null
                val exp = expectedUri
                val skipHistory = exp != null && track.uri != exp && track.name != expectedName
                if (exp != null && !skipHistory) expectedUri = null
                requestCover(track.imageUri)
                _state.update { st ->
                    val item = SpotifyItem(track.uri, track.name, track.artist?.name, imageKey = track.imageUri?.raw)
                    st.copy(cover = if (keepCover) st.cover else null, saved = false,
                        history = if (skipHistory) st.history else (listOf(item) + st.history.filterNot { h -> h.uri == track.uri }).take(30))
                }
                r.imagesApi.getImage(track.imageUri, Image.Dimension.LARGE).setResultCallback { bmp ->
                    if (lastTrackUri == track.uri) _state.update { it.copy(cover = bmp.asImageBitmap()) }
                }
                r.userApi.getLibraryState(track.uri).setResultCallback { ls ->
                    if (lastTrackUri == track.uri) _state.update { it.copy(saved = ls.isAdded) }
                }
            }
        }
        subscription.setErrorCallback {
            _state.update { it.copy(error = S.connectionToSpotifyWasLost) }
        }
        sub = subscription
    }

    override fun disconnect() {
        prefs.edit().putBoolean("auto", false).apply()
        vault.clear()
        teardown()
    }

    private fun teardown() {
        coverRequested.clear()
        shelvesLoading = false
        // Memory only: the stored refresh token (auto-import) stays until the user turns it off or disconnects
        wipeLibraryMemory()
        if (current === this) current = null
        sub?.cancel(); sub = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        lastTrackUri = null
        handler.removeCallbacks(ticker)
        // Everything that came from Spotify is erased from memory
        _state.value = SpotifyState(appInstalled = SpotifyAppRemote.isSpotifyInstalled(activity))
    }

    // ---------- Library import (Web API): token and data only in memory ----------
    private val web = SpotifyWebApi(SpotifyConfig.CLIENT_ID, LIB_REDIRECT)
    private val libScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var libraryJob: Job? = null
    private var playlistJob: Job? = null
    private var libVerifier: String? = null
    private var libState: String? = null
    private val vault by lazy { SpotifyTokenVault(prefs) }

    private fun setLibrary(block: (SpotifyLibrary) -> SpotifyLibrary) {
        _state.update { it.copy(library = block(it.library)) }
    }

    /** Button "Import": with a stored token it reads the library right away, otherwise it signs in through the browser. */
    override fun importLibrary() {
        val rt = vault.load()
        if (rt != null) loadWithToken(rt, browserOnRevoked = true) else startBrowserAuth()
    }

    /** Called when the Spotify section opens: repeats the import without a tap if the user imported before. */
    override fun autoImportLibrary() {
        if (_state.value.library.status != LibraryStatus.IDLE) return
        val rt = vault.load()
        if (rt == null) {
            if (vault.has()) vault.clear() // cannot be decrypted (for example after a backup restore)
            return
        }
        loadWithToken(rt, browserOnRevoked = false)
    }

    /** Settings card: only the profile name (token refresh + /me), no library. Does nothing without a stored token. */
    override fun refreshProfile() {
        val lib = _state.value.library
        if (lib.accountName != null || lib.status != LibraryStatus.IDLE) return
        val rt = vault.load() ?: return
        libScope.launch {
            try {
                val keep = web.refreshAccess(rt)
                if (keep != rt) vault.save(keep)
                val name = web.accountName
                setLibrary { if (it.status == LibraryStatus.IDLE) it.copy(accountName = name) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.d("SpotifyLibrary", "profile failed: ${e.message}")
                if (e is SpotifyHttpError && e.code == 400) vault.clear()
            } finally {
                if (_state.value.library.status == LibraryStatus.IDLE) web.clear()
            }
        }
    }

    override fun disableAutoImport() {
        vault.clear()
        setLibrary { it.copy(autoImport = false) }
    }

    private fun loadWithToken(refresh: String, browserOnRevoked: Boolean) {
        setLibrary { it.copy(status = LibraryStatus.LOADING, autoImport = true, error = null) }
        libraryJob?.cancel()
        libraryJob = libScope.launch {
            try {
                val keep = web.refreshAccess(refresh)
                if (keep != refresh) vault.save(keep)
                readLibrary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.d("SpotifyLibrary", "auto import failed: ${e.message}")
                web.clear()
                if (e is SpotifyHttpError && e.code == 400) {
                    // access revoked or the refresh token expired: forget it
                    vault.clear()
                    if (browserOnRevoked) {
                        handler.post { startBrowserAuth() }
                    } else {
                        setLibrary { SpotifyLibrary(status = LibraryStatus.ERROR, error = spotifyAccessRevokedText()) }
                    }
                } else {
                    setLibrary { SpotifyLibrary(status = LibraryStatus.ERROR, error = spotifyLibraryErrorText(e), autoImport = true) }
                }
            }
        }
    }

    private suspend fun readLibrary() {
        val playlists = web.playlists()
        val (saved, total) = web.savedTracks()
        setLibrary {
            SpotifyLibrary(
                status = LibraryStatus.READY, playlists = playlists, saved = saved,
                savedTotal = total, autoImport = vault.has(), accountName = web.accountName,
            )
        }
    }

    /**
     * Authorization code + PKCE in the system browser (the Spotify auth library does not pass the PKCE challenge,
     * so Spotify would ask for a client secret). The redirect comes back through SpotifyLibraryCallbackActivity.
     */
    private fun startBrowserAuth() {
        if (SpotifyConfig.CLIENT_ID.isBlank()) {
            setLibrary { SpotifyLibrary(status = LibraryStatus.ERROR, error = S.spotifyClientIdIsNot) }
            return
        }
        val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        val rnd = java.security.SecureRandom()
        val verifier = android.util.Base64.encodeToString(ByteArray(64).also { rnd.nextBytes(it) }, flags)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = android.util.Base64.encodeToString(digest, flags)
        val state = android.util.Base64.encodeToString(ByteArray(16).also { rnd.nextBytes(it) }, flags)
        libVerifier = verifier
        libState = state
        setLibrary { SpotifyLibrary(status = LibraryStatus.AUTHORIZING) }
        val uri = Uri.Builder()
            .scheme("https").authority("accounts.spotify.com").path("authorize")
            .appendQueryParameter("client_id", SpotifyConfig.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", LIB_REDIRECT)
            .appendQueryParameter("scope", "user-library-read playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .build()
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            libVerifier = null
            libState = null
            setLibrary { SpotifyLibrary(status = LibraryStatus.ERROR, error = S.couldNotConnectToSpotify) }
        }
    }

    /** Called by SpotifyLibraryCallbackActivity with solara://spotify-library?code=...&state=... */
    fun onLibraryRedirect(data: Uri?) {
        val verifier = libVerifier
        val expected = libState
        libVerifier = null
        libState = null
        val code = data?.getQueryParameter("code")
        if (verifier == null || data == null || data.getQueryParameter("state") != expected || code.isNullOrBlank()) {
            setLibrary { SpotifyLibrary(status = LibraryStatus.ERROR, error = S.spotifyDidNotConfirmAccess) }
            return
        }
        setLibrary { it.copy(status = LibraryStatus.LOADING, error = null) }
        libraryJob?.cancel()
        libraryJob = libScope.launch {
            try {
                web.exchangeCode(code, verifier)?.let { vault.save(it) }
                readLibrary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.d("SpotifyLibrary", "import failed: ${e.message}")
                web.clear()
                setLibrary { SpotifyLibrary(status = LibraryStatus.ERROR, error = spotifyLibraryErrorText(e)) }
            }
        }
    }

    override fun openLibraryPlaylist(playlist: SpotifyLibPlaylist) {
        setLibrary { it.copy(openPlaylist = playlist, playlistTracks = null, error = null) }
        playlistJob?.cancel()
        playlistJob = libScope.launch {
            try {
                val tracks = web.playlistTracks(playlist.id)
                setLibrary { if (it.openPlaylist?.id == playlist.id) it.copy(playlistTracks = tracks) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.d("SpotifyLibrary", "playlist failed: ${e.message}")
                setLibrary { if (it.openPlaylist?.id == playlist.id) it.copy(playlistTracks = emptyList(), error = spotifyLibraryErrorText(e)) else it }
            }
        }
    }

    override fun closeLibraryPlaylist() {
        playlistJob?.cancel()
        setLibrary { it.copy(openPlaylist = null, playlistTracks = null, error = null) }
    }

    private fun wipeLibraryMemory() {
        libraryJob?.cancel()
        playlistJob?.cancel()
        libVerifier = null
        libState = null
        web.clear()
        setLibrary { SpotifyLibrary() }
    }

    /** "Remove import and sign out": memory and the stored token. */
    override fun forgetLibrary() {
        wipeLibraryMemory()
        vault.clear()
    }

    /** Called when the activity is closed. If the app was closed completely (not a screen rotation), we pause Spotify. */
    fun release() {
        val r = remote
        android.util.Log.d("SpotifyRelease", "release finishing=${activity.isFinishing} config=${activity.isChangingConfigurations} connected=${r?.isConnected}")
        if (!activity.isChangingConfigurations && r != null && r.isConnected) {
            r.playerApi.pause().setResultCallback { teardown() }.setErrorCallback { teardown() }
            handler.postDelayed({ teardown() }, 800)
        } else {
            teardown()
        }
    }

    override fun playUri(uri: String) {
        val r = remote ?: return
        ignoreIdle = false
        // Show the selection in the mini player right away: while Spotify responds, the player does not disappear
        val known = listItems[uri]
        val hist = _state.value.history.firstOrNull { it.uri == uri }
        val name = known?.title ?: hist?.title
        // Only a track may pre-fill the player. A playlist or album card must never put its own
        // title or cover into the player: they belong to the collection, not to the playing track
        if (name != null && uri.startsWith("spotify:track:")) {
            val key = known?.imageUri?.raw ?: hist?.imageKey
            optimisticKey = key
            _state.update { st ->
                st.copy(
                    uri = uri,
                    title = name,
                    artist = known?.subtitle ?: hist?.subtitle,
                    isPaused = false,
                    positionMs = 0L,
                    durationMs = 0L,
                    cover = key?.let { st.covers[it] },
                    // The track immediately becomes first in “Recently played”, so the list does not change a second time when Spotify responds
                    history = if (uri.startsWith("spotify:track:")) {
                        (listOf(SpotifyItem(uri, name, known?.subtitle ?: hist?.subtitle, imageKey = key)) +
                            st.history.filterNot { h -> h.uri == uri }).take(30)
                    } else st.history,
                    error = null,
                )
            }
        }
        r.playerApi.play(uri).setErrorCallback {
            _state.update { it.copy(isPaused = true, error = S.couldNotStartPlaybackPremium) }
        }
    }

    override fun pause() { remote?.playerApi?.pause() }
    override fun resume() { remote?.playerApi?.resume() }
    override fun next() { remote?.playerApi?.skipNext() }
    override fun previous() { remote?.playerApi?.skipPrevious() }
    override fun seekTo(ms: Long) { remote?.playerApi?.seekTo(ms) }

    // While a list starts playing from a chosen track, shuffle is switched off for a moment: the displayed state is held
    private var holdShuffle = false
    private val releaseShuffle = Runnable { holdShuffle = false }

    override fun setShuffle(on: Boolean) {
        _state.update { it.copy(shuffling = on) }
        remote?.playerApi?.setShuffle(on)
    }

    override fun setRepeat(mode: SpotifyRepeat) {
        _state.update { it.copy(repeat = mode) }
        remote?.playerApi?.setRepeat(
            when (mode) {
                SpotifyRepeat.TRACK -> Repeat.ONE
                SpotifyRepeat.CONTEXT -> Repeat.ALL
                SpotifyRepeat.OFF -> Repeat.OFF
            }
        )
    }

    override fun toggleSaved() {
        val r = remote ?: return
        val uri = _state.value.uri ?: return
        val add = !_state.value.saved
        _state.update { it.copy(saved = add) }
        // The like goes to Spotify and is not stored anywhere on our side
        if (add) r.userApi.addToLibrary(uri) else r.userApi.removeFromLibrary(uri)
    }

    private val coverRequested = HashSet<String>()

    /** Item cover: taken from Spotify itself without changes, kept only in memory. */
    private fun requestCover(uri: com.spotify.protocol.types.ImageUri?) {
        val r = remote ?: return
        val u = uri ?: run { android.util.Log.d("SpotifyCover", "no imageUri"); return }
        val key = u.raw
        if (key.isNullOrBlank()) {
            android.util.Log.d("SpotifyCover", "empty imageUri")
            return
        }
        if (!coverRequested.add(key)) return
        r.imagesApi.getImage(u, Image.Dimension.MEDIUM)
            .setResultCallback { bmp ->
                _state.update { st -> st.copy(covers = st.covers + (key to bmp.asImageBitmap())) }
            }
            .setErrorCallback { e ->
                coverRequested.remove(key)
                android.util.Log.d("SpotifyCover", "error ${key}: ${e.message}")
            }
    }

    private val listItems = HashMap<String, com.spotify.protocol.types.ListItem>()

    private fun showItems(title: String, items: Array<com.spotify.protocol.types.ListItem>?) {
        val list = (items ?: emptyArray()).filter { it.uri != null && it.title != null }
        list.forEach { listItems[it.uri] = it; requestCover(it.imageUri) }
        _state.update { st ->
            st.copy(browseTitle = title, browse = list.map {
                SpotifyItem(it.uri, it.title, it.subtitle, it.playable, it.hasChildren, it.imageUri?.raw)
            })
        }
    }

    /** Plays an album or playlist from the given index: "next" then follows the list instead of Spotify autoplay. */
    override fun playInContext(contextUri: String, index: Int, item: SpotifyItem?) {
        val r = remote ?: return
        ignoreIdle = false
        // A collection joins "Recent" only once a track has been started from it (memory only, at most 10 of 30)
        val st0 = _state.value
        st0.browseHeader?.takeIf { st0.browseUri == contextUri }?.let { card ->
            _state.update { st ->
                var collections = 0
                st.copy(
                    history = (listOf(card) + st.history.filterNot { it.uri == card.uri })
                        .filter { it.uri.startsWith("spotify:track:") || ++collections <= 10 }
                        .take(30),
                )
            }
        }
        expectedUri = item?.uri
        expectedName = item?.title
        handler.removeCallbacks(clearExpected)
        if (item != null) handler.postDelayed(clearExpected, 5000)
        // Starting from a chosen track needs shuffle off; the user's choice is restored once that track plays
        val wantShuffle = _state.value.shuffling
        holdShuffle = wantShuffle
        handler.removeCallbacks(releaseShuffle)
        if (wantShuffle) handler.postDelayed(releaseShuffle, 6000)
        r.playerApi.setShuffle(false)
        r.playerApi.play(contextUri)
            .setResultCallback {
                run {
                    r.playerApi.skipToIndex(contextUri, index)
                        .setResultCallback {
                            android.util.Log.d("SpotifyBrowse", "skipToIndex #$index ok")
                            if (wantShuffle) {
                                r.playerApi.setShuffle(true)
                                    .setResultCallback { holdShuffle = false }
                                    .setErrorCallback { holdShuffle = false }
                            }
                        }
                        .setErrorCallback { e ->
                            android.util.Log.d("SpotifyBrowse", "skipToIndex error: ${e.message}")
                            item?.let { playUri(it.uri) }
                        }
                }
            }
            .setErrorCallback { e ->
                android.util.Log.d("SpotifyBrowse", "play context error: ${e.message}")
                _state.update { it.copy(isPaused = true, error = S.couldNotStartPlaybackPremium) }
            }
        // Known SDK issue: on long playlists skipToIndex may do nothing. If Spotify is not on the chosen track, play it directly.
        if (item != null) {
            handler.postDelayed({
                r.playerApi.playerState.setResultCallback { st ->
                    val now = st.track
                    if (now != null && now.uri != item.uri && now.name != item.title) {
                        android.util.Log.d("SpotifyBrowse", "not on #$index, now=${now.name}: playing the track directly")
                        playUri(item.uri)
                    }
                }
            }, 2500)
        }
    }

    private var shelvesLoading = false

    private fun toSpotifyItem(li: com.spotify.protocol.types.ListItem) =
        SpotifyItem(li.uri, li.title, li.subtitle, li.playable, li.hasChildren, li.imageUri?.raw)

    /** Each top-level Spotify shelf becomes a separate row (at most 12 items, Spotify limit is 20). */
    override fun loadRecommended() {
        val r = remote ?: return
        if (shelvesLoading) return
        shelvesLoading = true
        r.contentApi.getRecommendedContentItems(com.spotify.android.appremote.api.ContentApi.ContentType.DEFAULT)
            .setResultCallback { top ->
                val tops = (top.items ?: emptyArray()).filter { it.uri != null && it.title != null }
                tops.forEach { listItems[it.uri] = it }
                val sources = tops.filter { it.hasChildren && !it.playable }.take(8)
                val results = arrayOfNulls<SpotifyShelf>(sources.size)
                var pending = sources.size
                fun publish() {
                    val ordered = results.filterNotNull()
                    if (ordered.isNotEmpty() || pending == 0) {
                        _state.update { st -> st.copy(shelves = ordered) }
                    }
                    if (pending == 0) shelvesLoading = false
                }
                if (sources.isEmpty()) publish()
                sources.forEachIndexed { index, shelf ->
                    r.contentApi.getChildrenOfItem(shelf, 12, 0)
                        .setResultCallback { kids ->
                            val list = (kids.items ?: emptyArray()).filter { it.uri != null && it.title != null }
                            list.forEach { listItems[it.uri] = it; requestCover(it.imageUri) }
                            if (list.isNotEmpty()) results[index] = SpotifyShelf(shelf.title, list.map { toSpotifyItem(it) })
                            pending--
                            publish()
                        }
                        .setErrorCallback {
                            pending--
                            publish()
                        }
                }
            }
            .setErrorCallback {
                shelvesLoading = false
                _state.update { st -> st.copy(shelves = emptyList()) }
            }
    }

    override fun openItem(item: SpotifyItem) {
        val r = remote ?: return
        val li = listItems[item.uri] ?: return
        r.contentApi.getChildrenOfItem(li, 20, 0)
            .setResultCallback {
                showItems(item.title, it.items)
                startPaging(li, 20, it.items?.size ?: 0, it.total)
                _state.update { st -> st.copy(browseUri = item.uri, browseHeader = item) }
            }
            .setErrorCallback { _state.update { st -> st.copy(error = S.couldNotOpenTheCollection) } }
    }

    // Paging of the open album or playlist: the next page is requested when the list is scrolled to its end
    private var browseParent: com.spotify.protocol.types.ListItem? = null
    private var browsePage = 50
    private var browseNext = 0
    private var browseLoadingMore = false

    private fun startPaging(parent: com.spotify.protocol.types.ListItem, page: Int, loaded: Int, total: Int = -1) {
        browseParent = parent
        browsePage = page
        browseNext = loaded
        browseLoadingMore = false
        _state.update { it.copy(browseMore = if (total >= 0) loaded < total else loaded >= page, browseTotal = total.takeIf { t -> t >= 0 }) }
    }

    private fun appendItems(items: Array<com.spotify.protocol.types.ListItem>) {
        val list = items.filter { it.uri != null && it.title != null }
        list.forEach { listItems[it.uri] = it; requestCover(it.imageUri) }
        _state.update { st ->
            val current = st.browse
            if (current == null) st else st.copy(browse = current + list.map { toSpotifyItem(it) })
        }
    }

    override fun loadMoreBrowse() {
        val r = remote ?: return
        val parent = browseParent ?: return
        if (browseLoadingMore || !_state.value.browseMore) return
        browseLoadingMore = true
        r.contentApi.getChildrenOfItem(parent, browsePage, browseNext)
            .setResultCallback { res ->
                if (browseParent !== parent) return@setResultCallback
                val raw = res.items ?: emptyArray<com.spotify.protocol.types.ListItem>()
                android.util.Log.d("SpotifyBrowse", "more: asked offset=$browseNext, got ${raw.size}, total=${res.total} offset=${res.offset}")
                browseNext += raw.size
                appendItems(raw)
                browseLoadingMore = false
                val knownTotal = _state.value.browseTotal
                _state.update { it.copy(browseMore = if (knownTotal != null) browseNext < knownTotal else raw.size >= browsePage) }
            }
            .setErrorCallback { e ->
                android.util.Log.d("SpotifyBrowse", "load more error: ${e.message}")
                browseLoadingMore = false
                _state.update { it.copy(browseMore = false) }
            }
    }

    /** Opens an album or playlist by link through the App Remote Content API (no Web API, no tokens). Logs: adb logcat -s SpotifyBrowse */
    override fun openByUri(uri: String) {
        val r = remote ?: return
        val kind = uri.split(":").getOrNull(1)
        if (kind != "album" && kind != "playlist") return
        val li = runCatching {
            com.google.gson.Gson().fromJson(
                """{"id":"$uri","uri":"$uri","title":"","playable":true,"hasChildren":true}""",
                com.spotify.protocol.types.ListItem::class.java,
            )
        }.getOrNull() ?: run {
            android.util.Log.d("SpotifyBrowse", "could not build ListItem for ${uri}")
            return
        }
        r.contentApi.getChildrenOfItem(li, 50, 0)
            .setResultCallback { res ->
                android.util.Log.d("SpotifyBrowse", "${uri} -> items: ${res.items?.size}")
                android.util.Log.d("SpotifyBrowse", "page0 total=${res.total} offset=${res.offset} limit=${res.limit}")
                showItems(if (kind == "album") S.album else S.playlist, res.items)
                _state.update { st -> st.copy(browseUri = uri, browseHeader = null) }
                startPaging(li, 50, res.items?.size ?: 0, res.total)
            }
            .setErrorCallback { e ->
                android.util.Log.d("SpotifyBrowse", "error ${uri}: ${e.message}")
                _state.update { st -> st.copy(error = S.spotifyReturnedNoTrackList) }
            }
    }

    override fun closeBrowse() {
        browseParent = null
        _state.update { it.copy(browse = null, browseTitle = null, browseMore = false, browseTotal = null, browseUri = null, browseHeader = null) }
    }

    override fun openSpotifyApp() {
        activity.packageManager.getLaunchIntentForPackage("com.spotify.music")?.let { activity.startActivity(it) }
    }

    /** Opens a specific track, album or playlist in the Spotify app (metadata must lead to Spotify). */
    override fun openInSpotify(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.spotify.music")
        runCatching { activity.startActivity(intent) }.onFailure { openSpotifyApp() }
    }

    override fun getSpotifyApp() {
        val uri = Uri.parse("market://details?id=com.spotify.music")
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music"))
            )
        }
    }
}
