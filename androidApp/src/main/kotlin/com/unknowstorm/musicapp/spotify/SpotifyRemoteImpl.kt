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
    private val _state = MutableStateFlow(SpotifyState(appInstalled = SpotifyAppRemote.isSpotifyInstalled(activity)))
    override val state: StateFlow<SpotifyState> = _state.asStateFlow()

    private var remote: SpotifyAppRemote? = null
    private var sub: Subscription<PlayerState>? = null
    private var lastTrackUri: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val connectTimeout = Runnable {
        _state.update {
            if (it.connecting) it.copy(
                connecting = false,
                error = "Spotify не ответил. Откройте приложение Spotify и разрешите ему всплывающие окна в фоне, затем повторите.",
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
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(activity, params, object : Connector.ConnectionListener {
            override fun onConnected(r: SpotifyAppRemote) {
                handler.removeCallbacks(connectTimeout)
                remote = r
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
                _state.update { it.copy(connecting = false, connected = false, error = text) }
            }
        })
    }

    private fun subscribe(r: SpotifyAppRemote) {
        sub?.cancel()
        val subscription = r.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { ps ->
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
                _state.update { it.copy(cover = null, saved = false) }
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
        sub?.cancel(); sub = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        lastTrackUri = null
        handler.removeCallbacks(ticker)
        // Всё, что пришло из Spotify, стираем из памяти
        _state.value = SpotifyState(appInstalled = SpotifyAppRemote.isSpotifyInstalled(activity))
    }

    fun release() = disconnect()

    override fun playUri(uri: String) {
        val r = remote ?: return
        r.playerApi.play(uri).setErrorCallback {
            _state.update { it.copy(error = "Не удалось запустить. Нужен Premium и открытый вход в Spotify.") }
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
