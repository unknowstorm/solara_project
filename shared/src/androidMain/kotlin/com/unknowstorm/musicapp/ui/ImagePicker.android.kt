package com.unknowstorm.musicapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

@Composable
actual fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { prepareAvatar(context, uri) }.getOrNull() }
                if (bytes != null) latest(bytes)
            }
        }
    }
    return remember(launcher) { { launcher.launch("image/*") } }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

/** Обрезает фото по центру до квадрата, учитывает поворот из EXIF и сжимает в JPEG. */
private fun prepareAvatar(context: Context, uri: Uri): ByteArray? {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Не читаем гигантское фото целиком: уменьшаем при чтении, пока сторона не станет близка к нужной
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= AvatarSizePx && bounds.outHeight / (sample * 2) >= AvatarSizePx) {
        sample *= 2
    }
    val src = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    val degrees = resolver.openInputStream(uri)?.use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f

    val side = min(src.width, src.height)
    val matrix = Matrix().apply { if (degrees != 0f) postRotate(degrees) }
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side, matrix, true)
    val target = min(square.width, AvatarSizePx)
    val scaled = if (square.width == target) square else Bitmap.createScaledBitmap(square, target, target, true)

    return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}
