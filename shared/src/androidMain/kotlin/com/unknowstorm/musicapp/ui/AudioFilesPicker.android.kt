package com.unknowstorm.musicapp.ui

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

@Composable
actual fun rememberAudioFilesPicker(onPicked: (List<LocalFileEntry>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val entries = withContext(Dispatchers.IO) {
                    uris.mapNotNull { runCatching { readEntry(context, it) }.getOrNull() }
                }
                if (entries.isNotEmpty()) latest(entries)
            }
        }
    }
    return remember(launcher) { { launcher.launch(arrayOf("audio/*")) } }
}

/** Remembers access to the file between launches and reads title, artist, length and cover from its tags. */
private fun readEntry(context: Context, uri: Uri): LocalFileEntry {
    // Not every file provider allows keeping access; then the file works until the app restarts
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?.substringBeforeLast('.')
    var title: String? = null
    var artist: String? = null
    var duration = 0L
    var artworkUrl: String? = null
    val mmr = MediaMetadataRetriever()
    try {
        mmr.setDataSource(context, uri)
        title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        mmr.embeddedPicture?.let { artworkUrl = saveCover(context, uri, it) }
    } catch (_: Exception) {
        // The tags could not be read: the file name will serve as the title
    } finally {
        runCatching { mmr.release() }
    }
    return LocalFileEntry(
        uri = uri.toString(),
        title = title?.takeIf { it.isNotBlank() } ?: fileName?.takeIf { it.isNotBlank() } ?: "Track",
        artist = artist.orEmpty(),
        durationMs = duration,
        artworkUrl = artworkUrl,
    )
}

private const val COVER_SIZE = 512

/** Shrinks the embedded picture, saves it as a small jpg and returns its address (or null if anything fails). */
private fun saveCover(context: Context, uri: Uri, bytes: ByteArray): String? = runCatching {
    val name = MessageDigest.getInstance("SHA-1")
        .digest(uri.toString().toByteArray())
        .joinToString("") { "%02x".format(it) } + ".jpg"
    val dir = File(context.filesDir, "covers").apply { mkdirs() }
    val file = File(dir, name)
    if (!file.exists()) {
        // Read the size first so a huge picture is not decoded at full resolution
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_SIZE && bounds.outHeight / (sample * 2) >= COVER_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return@runCatching null
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > COVER_SIZE) {
            val k = COVER_SIZE.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * k).toInt().coerceAtLeast(1),
                (bitmap.height * k).toInt().coerceAtLeast(1),
                true,
            )
        } else bitmap
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }
    Uri.fromFile(file).toString()
}.getOrNull()
