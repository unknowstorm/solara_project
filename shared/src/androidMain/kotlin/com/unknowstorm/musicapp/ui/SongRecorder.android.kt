package com.unknowstorm.musicapp.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Records a short AAC clip from the microphone after the user tapped the button. The permission is asked only now.
 * The clip lives in a temporary file that is deleted right after it is read into memory.
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

@Suppress("DEPRECATION")
private fun newRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

private suspend fun recordClip(context: Context, seconds: Int, onStarted: () -> Unit): RecordResult {
    val file = File.createTempFile("clip", ".m4a", context.cacheDir)
    var recorder: MediaRecorder? = null
    return try {
        val r = newRecorder(context)
        recorder = r
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(96_000)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        onStarted()
        delay(seconds * 1000L)
        r.stop()
        RecordResult.Ok(file.readBytes(), "audio/mp4")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Solara: recording failed: ${e.message}")
        RecordResult.Failed
    } finally {
        runCatching { recorder?.release() }
        file.delete()
    }
}
