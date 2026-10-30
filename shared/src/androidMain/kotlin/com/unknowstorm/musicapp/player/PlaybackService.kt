package com.unknowstorm.musicapp.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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

/** Фоновый сервис: держит плеер, уведомление и кнопки на экране блокировки. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val resolvedUrls = ConcurrentHashMap<String, Uri>()

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        // Редиректы Audius проходим один раз на трек, а не при каждой перемотке
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

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && seekAt != 0L) {
                    android.util.Log.i("Solara", "seek ready after ${android.os.SystemClock.elapsedRealtime() - seekAt} ms")
                    seekAt = 0L
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                resolvedUrls.clear() // адрес мог устареть, при повторе разрешим заново
                val http = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                android.util.Log.e(
                    "Solara",
                    "player error ${error.errorCodeName}, http=${http?.responseCode}, url=${http?.dataSpec?.uri}",
                )
            }
        })
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

    /** Идёт по редиректам до файла. Возвращает null, если узел ответил не 200/206. */
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
                        // 200 на запрос части файла: сервер отдаёт файл только целиком
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}