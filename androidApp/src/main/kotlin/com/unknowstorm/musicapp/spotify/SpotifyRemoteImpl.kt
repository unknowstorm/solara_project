package com.unknowstorm.musicapp.spotify

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val AUTH_REQUEST = 7341

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
    private val connectTimeout = Runnable {
        _state.update {
            if (it.connecting) it.copy(
                connecting = false,
                error = if (silent) null else "Spotify не ответил. Откройте приложение Spotify и разрешите ему всплывающие окна в фоне, затем повторите.",
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

    /** Тихое подключение при запуске: только если пользователь уже подключал Spotify и не отключал. */
    fun autoConnect() {
        if (!prefs.getBoolean("auto", false)) return
        if (remote?.isConnected == true || _state.value.connecting) return
        silent = true
        ignoreIdle = true
        // Разрешение уже выдано при ручном подключении: окно входа пропускаем, подключаемся напрямую
        connectRemote()
    }

    private fun startConnect() {
        if (remote?.isConnected == true) return
        if (!SpotifyAppRemote.isSpotifyInstalled(activity)) {
            _state.update { it.copy(appInstalled = false) }
            return
        }
        if (SpotifyConfig.CLIENT_ID.isBlank()) {
            _state.update { it.copy(error = "Не задан Client ID Spotify") }
            return
        }
        _state.update { it.copy(appInstalled = true, connecting = true, error = null) }
        handler.removeCallbacks(connectTimeout)
        // Окно разрешения открываем из Solara, пока она на экране: из фона Android его блокирует
        val request = AuthorizationRequest.Builder(
            SpotifyConfig.CLIENT_ID, AuthorizationResponse.Type.TOKEN, SpotifyConfig.REDIRECT_URI,
        ).setScopes(arrayOf("app-remote-control")).build()
        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST, request)
    }

    /** Вызывается из MainActivity. Токен из ответа не используется и не сохраняется. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != AUTH_REQUEST) return
        val response = AuthorizationClient.getResponse(resultCode, data)
        if (response.type == AuthorizationResponse.Type.TOKEN) {
            connectRemote()
        } else {
            _state.update {
                it.copy(connecting = false, error = "Spotify не подтвердил доступ. Проверьте, что ваш аккаунт добавлен в User Management.")
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
            _state.update { it.copy(error = "Не задан Client ID Spotify") }
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
                    "CouldNotFindSpotifyApp" -> "Приложение Spotify не найдено"
                    "NotLoggedInException" -> "Войдите в аккаунт в приложении Spotify"
                    "UserNotAuthorizedException" -> "Разрешите доступ в окне Spotify. Аккаунт должен быть добавлен в User Management."
                    "AuthenticationFailedException" -> "Spotify отклонил приложение: проверьте Client ID, SHA-1 и пакет в Dashboard"
                    else -> "Не удалось подключиться к Spotify"
                }
                _state.update { it.copy(connecting = false, connected = false, error = if (silent) null else text) }
            }
        })
    }

    private fun subscribe(r: SpotifyAppRemote) {
        sub?.cancel()
        val subscription = r.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { ps ->
            // После тихого автоподключения не показываем то, что Spotify играл раньше: ждём, пока что-то реально заиграет
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
                )
            }
            if (track != null && track.uri != lastTrackUri) {
                lastTrackUri = track.uri
                val keepCover = optimisticKey != null && optimisticKey == track.imageUri?.raw
                optimisticKey = null
                requestCover(track.imageUri)
                _state.update { st ->
                    val item = SpotifyItem(track.uri, track.name, track.artist?.name, imageKey = track.imageUri?.raw)
                    st.copy(cover = if (keepCover) st.cover else null, saved = false,
                        history = (listOf(item) + st.history.filterNot { h -> h.uri == track.uri }).take(30))
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
            _state.update { it.copy(error = "Связь со Spotify потеряна") }
        }
        sub = subscription
    }

    override fun disconnect() {
        prefs.edit().putBoolean("auto", false).apply()
        teardown()
    }

    private fun teardown() {
        coverRequested.clear()
        shelvesLoading = false
        if (current === this) current = null
        sub?.cancel(); sub = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        lastTrackUri = null
        handler.removeCallbacks(ticker)
        // Всё, что пришло из Spotify, стираем из памяти
        _state.value = SpotifyState(appInstalled = SpotifyAppRemote.isSpotifyInstalled(activity))
    }

    /** Вызывается при закрытии активности. Если приложение закрыли совсем (не поворот экрана), ставим Spotify на паузу. */
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
        // Сразу показываем выбранное в мини-плеере: пока Spotify отвечает, плеер не пропадает
        val known = listItems[uri]
        val hist = _state.value.history.firstOrNull { it.uri == uri }
        val name = known?.title ?: hist?.title
        if (name != null) {
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
                    // Трек сразу встаёт первым в «Недавно играло», чтобы список не менялся второй раз при ответе Spotify
                    history = if (uri.startsWith("spotify:track:")) {
                        (listOf(SpotifyItem(uri, name, known?.subtitle ?: hist?.subtitle, imageKey = key)) +
                            st.history.filterNot { h -> h.uri == uri }).take(30)
                    } else st.history,
                    error = null,
                )
            }
        }
        r.playerApi.play(uri).setErrorCallback {
            _state.update { it.copy(isPaused = true, error = "Не удалось запустить. Нужен Premium и открытый вход в Spotify.") }
        }
    }

    override fun pause() { remote?.playerApi?.pause() }
    override fun resume() { remote?.playerApi?.resume() }
    override fun next() { remote?.playerApi?.skipNext() }
    override fun previous() { remote?.playerApi?.skipPrevious() }
    override fun seekTo(ms: Long) { remote?.playerApi?.seekTo(ms) }

    override fun toggleSaved() {
        val r = remote ?: return
        val uri = _state.value.uri ?: return
        val add = !_state.value.saved
        _state.update { it.copy(saved = add) }
        // Лайк уходит в Spotify и у нас нигде не хранится
        if (add) r.userApi.addToLibrary(uri) else r.userApi.removeFromLibrary(uri)
    }

    private val coverRequested = HashSet<String>()

    /** Обложка элемента: берём у самого Spotify без изменений, держим только в памяти. */
    private fun requestCover(uri: com.spotify.protocol.types.ImageUri?) {
        val r = remote ?: return
        val u = uri ?: run { android.util.Log.d("SpotifyCover", "нет imageUri"); return }
        val key = u.raw
        if (key.isNullOrBlank()) {
            android.util.Log.d("SpotifyCover", "пустой imageUri")
            return
        }
        if (!coverRequested.add(key)) return
        r.imagesApi.getImage(u, Image.Dimension.MEDIUM)
            .setResultCallback { bmp ->
                _state.update { st -> st.copy(covers = st.covers + (key to bmp.asImageBitmap())) }
            }
            .setErrorCallback { e ->
                coverRequested.remove(key)
                android.util.Log.d("SpotifyCover", "ошибка $key: ${e.message}")
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

    private var shelvesLoading = false

    private fun toSpotifyItem(li: com.spotify.protocol.types.ListItem) =
        SpotifyItem(li.uri, li.title, li.subtitle, li.playable, li.hasChildren, li.imageUri?.raw)

    /** Каждая верхняя полка Spotify становится отдельным рядом (не больше 12 элементов, лимит Spotify 20). */
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
            .setResultCallback { showItems(item.title, it.items) }
            .setErrorCallback { _state.update { st -> st.copy(error = "Не удалось открыть подборку") } }
    }

    override fun closeBrowse() {
        _state.update { it.copy(browse = null, browseTitle = null) }
    }

    override fun openSpotifyApp() {
        activity.packageManager.getLaunchIntentForPackage("com.spotify.music")?.let { activity.startActivity(it) }
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
