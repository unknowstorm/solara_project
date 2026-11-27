package com.unknowstorm.musicapp.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.unknowstorm.musicapp.model.Track
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * Desktop player: decodes MP3 streams in pure Java (JLayer) and plays them through javax.sound.
 * Works on Windows and Linux without installing anything. Tracks without a direct MP3 stream
 * (YouTube, Spotify) are not playable here and are reported as an error.
 */
class DesktopAudioBackend : AudioBackend {
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val lock = Any()
    private var tracks: MutableList<Track> = mutableListOf()
    private var index = 0
    private var repeat = RepeatMode.OFF
    private var shuffle = false
    private var order: List<Int> = emptyList()

    @Volatile private var generation = 0
    @Volatile private var paused = false

    // ---- queue ----

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        synchronized(lock) {
            this.tracks = tracks.toMutableList()
            index = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
            rebuildOrder()
            publishQueue()
        }
        startCurrent(0L)
    }

    override fun playAt(index: Int) {
        synchronized(lock) {
            if (index !in tracks.indices) return
            this.index = index
        }
        startCurrent(0L)
    }

    override fun removeAt(index: Int) {
        var restart = false
        synchronized(lock) {
            if (index !in tracks.indices) return
            tracks.removeAt(index)
            when {
                tracks.isEmpty() -> { generation++; this.index = 0 }
                index < this.index -> this.index--
                index == this.index -> { this.index = this.index.coerceAtMost(tracks.lastIndex); restart = true }
            }
            rebuildOrder()
            publishQueue()
        }
        if (tracks.isEmpty()) stop() else if (restart) startCurrent(0L) else publishFlags()
    }

    override fun moveItem(from: Int, to: Int) {
        synchronized(lock) {
            if (from !in tracks.indices || to !in tracks.indices || from == to) return
            val current = tracks[index]
            tracks.add(to, tracks.removeAt(from))
            index = tracks.indexOf(current)
            rebuildOrder()
            publishQueue()
        }
        publishFlags()
    }

    private fun rebuildOrder() {
        val all = tracks.indices.toMutableList()
        order = if (shuffle) {
            val rest = all.filter { it != index }.shuffled()
            listOf(index) + rest
        } else all
    }

    private fun publishQueue() {
        _queue.value = tracks.map { QueueItem(it.id, it.title, it.artist, it.artworkUrl, it.provider, it.durationMs) }
    }

    private fun publishFlags() {
        val pos = order.indexOf(index)
        _state.value = _state.value.copy(
            hasNext = pos in 0 until order.lastIndex || repeat == RepeatMode.ALL,
            hasPrevious = pos > 0,
            currentIndex = index,
        )
    }

    // ---- transport ----

    override fun pause() {
        paused = true
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun resume() {
        paused = false
        if (tracks.isNotEmpty()) _state.value = _state.value.copy(isPlaying = true, error = null)
    }

    override fun next() {
        val target = synchronized(lock) { stepTarget(+1) } ?: return
        synchronized(lock) { index = target }
        startCurrent(0L)
    }

    override fun previous() {
        // After 3 seconds a tap restarts the track, like on the phone
        if (_state.value.positionMs > 3000) { startCurrent(0L); return }
        val target = synchronized(lock) { stepTarget(-1) } ?: run { startCurrent(0L); return }
        synchronized(lock) { index = target }
        startCurrent(0L)
    }

    private fun stepTarget(delta: Int): Int? {
        if (tracks.isEmpty()) return null
        val pos = order.indexOf(index)
        val np = pos + delta
        return when {
            np in order.indices -> order[np]
            repeat == RepeatMode.ALL -> order[(np + order.size) % order.size]
            else -> null
        }
    }

    override fun seekTo(positionMs: Long) {
        if (tracks.isEmpty()) return
        startCurrent(positionMs.coerceAtLeast(0L))
    }

    override fun stop() {
        generation++
        paused = false
        synchronized(lock) { tracks.clear(); order = emptyList(); index = 0; publishQueue() }
        _state.value = PlaybackState(repeatMode = repeat, shuffle = shuffle)
    }

    override fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = repeat)
        publishFlags()
    }

    override fun toggleShuffle() {
        shuffle = !shuffle
        synchronized(lock) { rebuildOrder() }
        _state.value = _state.value.copy(shuffle = shuffle)
        publishFlags()
    }

    override fun release() {
        generation++
    }

    // ---- playback thread ----

    private fun startCurrent(startMs: Long) {
        val track: Track
        val gen: Int
        synchronized(lock) {
            track = tracks.getOrNull(index) ?: return
            gen = ++generation
        }
        paused = false
        val url = track.streamUrl
        _state.value = PlaybackState(
            mediaId = track.id, title = track.title, artist = track.artist, artworkUrl = track.artworkUrl,
            isPlaying = url != null, isBuffering = url != null, positionMs = startMs, durationMs = track.durationMs,
            error = if (url == null) "This track cannot be played in the desktop version" else null,
            repeatMode = repeat, shuffle = shuffle, provider = track.provider, currentIndex = index,
        )
        publishFlags()
        if (url == null) return
        thread(name = "solara-audio", isDaemon = true) { play(url, track, startMs, gen) }
    }

    private fun play(url: String, track: Track, startMs: Long, gen: Int) {
        var line: SourceDataLine? = null
        var conn: HttpURLConnection? = null
        var finished = false
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Solara/1.0")
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            val bitstream = Bitstream(BufferedInputStream(conn.inputStream, 64 * 1024))
            val decoder = Decoder()
            var playedMs = 0.0
            var lastPublish = 0L
            while (gen == generation) {
                val header = bitstream.readFrame() ?: run { finished = true; null } ?: break
                val frameMs = header.ms_per_frame().toDouble()
                if (playedMs < startMs && line == null) {
                    // Seeking: skip frames without decoding until the target position
                    playedMs += frameMs
                    bitstream.closeFrame()
                    continue
                }
                val out = decoder.decodeFrame(header, bitstream) as SampleBuffer
                if (line == null) {
                    val fmt = AudioFormat(out.sampleFrequency.toFloat(), 16, out.channelCount, true, false)
                    line = (AudioSystem.getSourceDataLine(fmt)).also { it.open(fmt); it.start() }
                    _state.value = _state.value.copy(isBuffering = false)
                }
                val l = line!!
                while (paused && gen == generation) {
                    if (l.isRunning) l.stop()
                    Thread.sleep(60)
                }
                if (gen != generation) break
                if (!l.isRunning) l.start()
                val shorts = out.buffer
                val bytes = ByteArray(out.bufferLength * 2)
                for (i in 0 until out.bufferLength) {
                    bytes[2 * i] = (shorts[i].toInt() and 0xFF).toByte()
                    bytes[2 * i + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
                }
                l.write(bytes, 0, bytes.size)
                bitstream.closeFrame()
                playedMs += frameMs
                val now = System.currentTimeMillis()
                if (now - lastPublish > 250 && gen == generation) {
                    lastPublish = now
                    val pos = startMs + l.microsecondPosition / 1000
                    _state.value = _state.value.copy(positionMs = pos)
                }
            }
            if (gen == generation && finished) line?.drain()
        } catch (e: InterruptedException) {
            return
        } catch (e: Exception) {
            if (gen == generation) {
                println("Solara: desktop playback failed: ${e.message}")
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false, error = "Playback error: ${e.message}")
            }
            return
        } finally {
            runCatching { line?.stop(); line?.close() }
            runCatching { conn?.disconnect() }
        }
        if (gen == generation && finished) onTrackEnded(gen)
    }

    private fun onTrackEnded(gen: Int) {
        if (gen != generation) return
        when {
            repeat == RepeatMode.ONE -> startCurrent(0L)
            else -> {
                val target = synchronized(lock) { stepTarget(+1) }
                if (target != null) {
                    synchronized(lock) { index = target }
                    startCurrent(0L)
                } else {
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
                }
            }
        }
    }
}

@Composable
actual fun rememberAudioBackend(): AudioBackend = remember { DesktopAudioBackend() }
