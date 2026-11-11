package com.unknowstorm.musicapp.player

import com.unknowstorm.musicapp.i18n.S
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Controls the player in PlaybackService and exposes its state to the UI. */
class MediaControllerBackend(context: Context) : AudioBackend {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var pendingQueue: Pair<List<MediaItem>, Int>? = null
    private var released = false
    private var lastError: String? = null
    private var errorStreak = 0
    private var retriedMediaId: String? = null
    private val clearError = Runnable {
        lastError = null
        publish()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) updateQueue()
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                errorStreak = 0
                retriedMediaId = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("Solara", "player error: ${error.errorCodeName}", error)
            val c = controller ?: return
            // First failure of a track: quietly retry from the same position
            val httpCode = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
            // 4xx errors (except 408 and 429) are not fixed by retrying
            val permanent = httpCode != null && httpCode in 400..499 && httpCode != 408 && httpCode != 429 && httpCode != 401 && httpCode != 403
            val id = c.currentMediaItem?.mediaId
            if (httpCode == 404 || httpCode == 410) {
                val dead = c.currentMediaItem?.mediaMetadata?.extras?.getString("provider")
                    ?.let { n -> ProviderId.entries.firstOrNull { it.name == n } }
                if (id != null && dead != null) DeadTracks.mark(dead, id)
            }
            if (!permanent && id != null && retriedMediaId != id) {
                retriedMediaId = id
                c.prepare()
                c.play()
                return
            }
            errorStreak++
            // When repeating a single track, "next" is the same track, so repeat is turned off
            if (c.repeatMode == Player.REPEAT_MODE_ONE) c.repeatMode = Player.REPEAT_MODE_OFF
            if (errorStreak < c.mediaItemCount && c.hasNextMediaItem()) {
                showError(S.trackUnavailablePlayingTheNext)
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            } else {
                showError(S.trackUnavailable)
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
            updateQueue()
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
                .setExtras(Bundle().apply { putString("provider", provider.name); putLong("durationMs", durationMs) })
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

    private fun updateQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            QueueItem(
                mediaId = m.mediaId,
                title = m.mediaMetadata.title?.toString().orEmpty(),
                artist = m.mediaMetadata.artist?.toString().orEmpty(),
                artworkUrl = m.mediaMetadata.artworkUri?.toString(),
                durationMs = m.mediaMetadata.extras?.getLong("durationMs") ?: 0L,
                provider = m.mediaMetadata.extras?.getString("provider")
                    ?.let { n -> ProviderId.entries.firstOrNull { it.name == n } },
            )
        }
    }

    override fun playAt(index: Int) {
        lastError = null
        controller?.let {
            if (index in 0 until it.mediaItemCount) {
                it.seekTo(index, 0L)
                it.prepare()
                it.play()
            }
        }
    }

    override fun moveItem(from: Int, to: Int) {
        controller?.let {
            if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount && from != to) it.moveMediaItem(from, to)
        }
    }

    override fun removeAt(index: Int) {
        controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    }

    private fun showError(text: String) {
        lastError = text
        handler.removeCallbacks(clearError)
        handler.postDelayed(clearError, 4000)
    }

    override fun release() {
        released = true
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(clearError)
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private fun slowSeekHint(c: MediaController, item: MediaItem?): String? {
        val id = item?.mediaId ?: return null
        val slow = RangeSupport.isNoRange("AUDIUS:$id") &&
            c.playbackState == Player.STATE_BUFFERING &&
            c.currentPosition > 15_000
        return if (slow) S.audiusServesThisTrackOnly else null
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
            error = lastError ?: slowSeekHint(c, item),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffle = c.shuffleModeEnabled,
            currentIndex = c.currentMediaItemIndex,
            provider = item?.mediaMetadata?.extras?.getString("provider")
                ?.let { name -> ProviderId.entries.firstOrNull { it.name == name } },
        )
    }
}
