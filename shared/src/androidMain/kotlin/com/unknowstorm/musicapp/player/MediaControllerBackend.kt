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
    private var pendingQueue: Pair<List<MediaItem>, Int>? = null
    private var released = false
    private var lastError: String? = null
    private var errorStreak = 0

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) errorStreak = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("Solara", "player error: ${error.errorCodeName}", error)
            val c = controller ?: return
            errorStreak++
            // При повторе одного трека «следующий» это он же, поэтому повтор выключаем
            if (c.repeatMode == Player.REPEAT_MODE_ONE) c.repeatMode = Player.REPEAT_MODE_OFF
            if (errorStreak < c.mediaItemCount && c.hasNextMediaItem()) {
                lastError = "Трек недоступен, пропускаю (${error.errorCodeName})"
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            } else {
                lastError = "Трек недоступен (${error.errorCodeName})"
            }
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
            pendingQueue?.let { (items, index) -> start(connected, items, index) }
            pendingQueue = null
            publish()
        }, ContextCompat.getMainExecutor(appContext))
        handler.post(ticker)
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        lastError = null
        errorStreak = 0
        val playable = tracks.filter { !it.streamUrl.isNullOrBlank() }
        val startId = tracks.getOrNull(startIndex)?.id
        val index = playable.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        if (playable.isEmpty()) return
        val items = playable.map { it.toMediaItem() }
        val current = controller
        if (current == null) pendingQueue = items to index else start(current, items, index)
    }

    private fun Track.toMediaItem() = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                .build()
        )
        .build()

    private fun start(c: MediaController, items: List<MediaItem>, index: Int) {
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun resume() {
        controller?.play()
    }

    override fun next() {
        lastError = null
        controller?.seekToNextMediaItem()
    }

    override fun previous() {
        lastError = null
        controller?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun stop() {
        lastError = null
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
        publish()
    }

    override fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
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
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffle = c.shuffleModeEnabled,
        )
    }
}
