package com.unknowstorm.musicapp.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a short WAV clip from the microphone after the user tapped the button. The permission is asked only now.
 * The clip lives only in memory.
 */
@Composable
actual fun rememberSongRecorder(): SongRecorder {
    val context = LocalContext.current
    val waiting = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        waiting[0]?.complete(granted)
        waiting[0] = null
    }
    return remember(launcher) {
        object : SongRecorder {
            override suspend fun record(seconds: Int, onStarted: () -> Unit): RecordResult {
                val has = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                val granted = has || CompletableDeferred<Boolean>().let { answer ->
                    waiting[0] = answer
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                    answer.await()
                }
                if (!granted) return RecordResult.PermissionDenied
                return withContext(Dispatchers.IO) { recordClip(context, seconds, onStarted) }
            }
        }
    }
}

private const val SAMPLE_RATE = 44100

/** Records raw 16-bit mono PCM and wraps it into a WAV file: the format that recognition services read most reliably. */
@android.annotation.SuppressLint("MissingPermission")
private suspend fun recordClip(context: Context, seconds: Int, onStarted: () -> Unit): RecordResult {
    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuf <= 0) return RecordResult.Failed
    var rec: AudioRecord? = null
    return try {
        val r = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE),
        )
        rec = r
        if (r.state != AudioRecord.STATE_INITIALIZED) return RecordResult.Failed
        val total = SAMPLE_RATE * 2 * seconds
        val pcm = ByteArray(total)
        r.startRecording()
        onStarted()
        var filled = 0
        while (filled < total) {
            val n = r.read(pcm, filled, minOf(4096, total - filled))
            if (n <= 0) break
            filled += n
            kotlinx.coroutines.yield()
        }
        r.stop()
        if (filled < SAMPLE_RATE) return RecordResult.Failed
        RecordResult.Ok(wav(pcm, filled), "audio/wav")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: recording failed: ${e.message}")
        RecordResult.Failed
    } finally {
        runCatching { rec?.release() }
    }
}

private fun wav(pcm: ByteArray, len: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(44 + len)
    fun le32(v: Int) { out.write(v and 255); out.write(v shr 8 and 255); out.write(v shr 16 and 255); out.write(v shr 24 and 255) }
    fun le16(v: Int) { out.write(v and 255); out.write(v shr 8 and 255) }
    out.write("RIFF".toByteArray()); le32(36 + len); out.write("WAVE".toByteArray())
    out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2); le16(2); le16(16)
    out.write("data".toByteArray()); le32(len)
    out.write(pcm, 0, len)
    return out.toByteArray()
}
