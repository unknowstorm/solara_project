package com.unknowstorm.musicapp.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Управляет плеером в PlaybackService и отдаёт его состояние интерфейсу. */
class MediaControllerBackend(context: Context) : AudioBackend {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var pendingItem: MediaItem? = null
    private var released = false
    private var lastError: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("Solara", "player error: ${error.errorCodeName}", error)
            lastError = "Трек недоступен (${error.errorCodeName})"
            publish()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            publish()
            handler.postDelayed(this, 250)
        }
    }

    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()

    init {
        controllerFuture.addListener({
            if (released) {
                MediaController.releaseFuture(controllerFuture)
                return@addListener
            }
            val connected = try {
                controllerFuture.get()
            } catch (e: Exception) {
                return@addListener
            }
            controller = connected
            connected.addListener(listener)
            pendingItem?.let { start(connected, it) }
            pendingItem = null
            publish()
        }, ContextCompat.getMainExecutor(appContext))
        handler.post(ticker)
    }

    override fun play(track: Track, streamUrl: String) {
        lastError = null
        val item = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        val current = controller
        if (current == null) pendingItem = item else start(current, item)
    }

    private fun start(c: MediaController, item: MediaItem) {
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun resume() {
        controller?.play()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun release() {
        released = true
        handler.removeCallbacks(ticker)
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private fun publish() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        _state.value = PlaybackState(
            mediaId = item?.mediaId?.takeIf { it.isNotEmpty() },
            title = item?.mediaMetadata?.title?.toString(),
            artist = item?.mediaMetadata?.artist?.toString(),
            artworkUrl = item?.mediaMetadata?.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            error = lastError,
        )
    }
}