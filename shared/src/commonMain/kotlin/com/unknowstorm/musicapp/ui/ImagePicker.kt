package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Side of the square avatar in pixels: the photo is cropped to the center and shrunk to this size. */
const val AvatarSizePx = 512

/**
 * Returns a function that opens the photo picker (gallery on Android, a file chooser window on desktop).
 * The picked photo arrives in [onPicked] already ready: a square [AvatarSizePx]×[AvatarSizePx], JPEG.
 */
@Composable
expect fun rememberAvatarPicker(onPicked: (ByteArray) -> Unit): () -> Unit

/** Turns image bytes (JPEG/PNG) into an image for Compose; null if it failed. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
