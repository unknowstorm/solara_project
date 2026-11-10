package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

@Composable
actual fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onPicked)
    return remember {
        {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { pickAndPrepare() }.getOrNull() }
                if (bytes != null) latest(bytes)
            }
        }
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

private val Extensions = setOf("jpg", "jpeg", "png", "bmp", "gif")

/** Открывает окно выбора файла, обрезает фото по центру до квадрата и сжимает в JPEG. */
private fun pickAndPrepare(): ByteArray? {
    val dialog = FileDialog(null as Frame?, S.chooseAPhoto, FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in Extensions }
        isVisible = true // блокирует поток, пока окно открыто
    }
    val name = dialog.file ?: return null
    val src = ImageIO.read(File(dialog.directory, name)) ?: return null

    val side = min(src.width, src.height)
    val square = src.getSubimage((src.width - side) / 2, (src.height - side) / 2, side, side)
    val target = min(side, AvatarSizePx)

    // JPEG не умеет прозрачность, поэтому рисуем на белом фоне в формате RGB
    val out = BufferedImage(target, target, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.color = Color.WHITE
    g.fillRect(0, 0, target, target)
    g.drawImage(square, 0, 0, target, target, null)
    g.dispose()

    return ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
}
