package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Сторона квадратной аватарки в пикселях: фото обрезается по центру и ужимается до этого размера. */
const val AvatarSizePx = 512

/**
 * Возвращает функцию, которая открывает выбор фото (галерея на Android, окно выбора файла на ПК).
 * Выбранное фото приходит в [onPicked] уже готовым: квадрат [AvatarSizePx]×[AvatarSizePx], JPEG.
 */
@Composable
expect fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** Превращает байты картинки (JPEG/PNG) в картинку для Compose; null, если не удалось. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
