package com.unknowstorm.musicapp.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Background service: holds the player, the notification and the lock-screen buttons. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val resolvedUrls = ConcurrentHashMap<String, Uri>()

    override fun onCreate() {
        super.onCreate()
        // DefaultDataSource opens http(s) links, content:// (files from the phone) and file:// (downloads)
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
        )
        // Audius redirects are followed once per track, not on every seek
        val resolvingFactory = ResolvingDataSource.Factory(
            dataSourceFactory,
            ResolvingDataSource.Resolver { spec ->
                val uri = spec.uri
                android.util.Log.i("Solara", "open pos=${spec.position} len=${spec.length} ${uri.host}${uri.path}")
                if (uri.host == "api.audius.co" && uri.path.orEmpty().endsWith("/stream")) {
                    val key = uri.toString()
                    val target = resolvedUrls[key]
                        ?: resolveRedirects(uri)?.also { resolvedUrls[key] = it }
                        ?: uri
                    spec.withUri(target)
                } else if (uri.host.orEmpty().endsWith("dzcdn.net") &&
                    uri.fragment.orEmpty().startsWith("deezer=")
                ) {
                    spec.withUri(DeezerPreview.fresh(uri) ?: uri)
                } else {
                    spec
                }
            },
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    this,
                    DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
                ).setDataSourceFactory(resolvingFactory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            private var seekAt = 0L

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    seekAt = android.os.SystemClock.elapsedRealtime()
                    android.util.Log.i("Solara", "seek to ${newPosition.positionMs} ms")
                }
            }

            // Номер аудиосессии может смениться: эквалайзер цепляется к новой
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqualizerEngine.attach(audioSessionId)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && seekAt != 0L) {
                    android.util.Log.i("Solara", "seek ready after ${android.os.SystemClock.elapsedRealtime() - seekAt} ms")
                    seekAt = 0L
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                resolvedUrls.clear() // the address may have expired; we will resolve it again on retry
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                android.util.Log.e(
                    "Solara",
                    "player error ${error.errorCodeName}, http=${http?.responseCode}, url=${http?.dataSpec?.uri}",
                )
            }
        })
        EqualizerEngine.attach(player.audioSessionId)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val builder = MediaSession.Builder(this, player)
        if (launchIntent != null) {
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        mediaSession = builder.build()
    }

    /** Follows redirects to the file. Returns null if the node answered something other than 200/206. */
    private fun resolveRedirects(start: Uri): Uri? {
        if (resolvedUrls.size > 200) resolvedUrls.clear()
        var current = URL(start.toString())
        repeat(5) {
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Range", "bytes=0-0")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    current = URL(current, location)
                } else {
                    if (code == 200 || code == 206) {
                        // 200 for a partial-file request: the server serves the file only in full
                        val id = start.pathSegments.getOrNull(start.pathSegments.size - 2)
                        RangeSupport.set("AUDIUS:$id", code == 200)
                        return Uri.parse(current.toString())
                    }
                    return null
                }
            } catch (e: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        EqualizerEngine.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/**
 * Системный эквалайзер Android поверх аудиосессии ExoPlayer.
 * Сервис и UI живут в одном процессе, поэтому UI управляет им напрямую.
 */
internal object EqualizerEngine : EqualizerController {
    private val _info = MutableStateFlow<EqualizerInfo?>(null)
    override val info: StateFlow<EqualizerInfo?> = _info.asStateFlow()
    private var equalizer: Equalizer? = null
    private var wanted = EqualizerSettings()

    /** Создаёт эффект для аудиосессии и сразу применяет последние настройки. */
    @Synchronized
    fun attach(sessionId: Int) {
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        try {
            val eq = Equalizer(0, sessionId)
            val range = eq.bandLevelRange
            _info.value = EqualizerInfo(
                bands = (0 until eq.numberOfBands).map { i ->
                    EqualizerBand(eq.getCenterFreq(i.toShort()) / 1000, range[0].toInt(), range[1].toInt())
                },
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
            )
            equalizer = eq
            applyNow()
        } catch (e: Exception) {
            android.util.Log.w("Solara", "equalizer unavailable: ${e.message}")
            equalizer = null
            _info.value = null
        }
    }

    @Synchronized
    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        _info.value = null
    }

    @Synchronized
    override fun apply(settings: EqualizerSettings) {
        wanted = settings
        applyNow()
    }

    @Synchronized
    override fun presetLevels(index: Int): List<Int> {
        val eq = equalizer ?: return emptyList()
        return try {
            eq.usePreset(index.toShort())
            (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun applyNow() {
        val eq = equalizer ?: return
        try {
            val range = eq.bandLevelRange
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(bands, wanted.levels.size)) {
                eq.setBandLevel(i.toShort(), wanted.levels[i].coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = wanted.enabled
        } catch (e: Exception) {
            android.util.Log.w("Solara", "equalizer apply failed: ${e.message}")
        }
    }
}
