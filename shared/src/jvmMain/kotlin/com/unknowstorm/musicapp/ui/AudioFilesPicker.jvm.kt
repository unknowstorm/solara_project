package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable

/** There is no file picker on the desktop version yet. */
@Composable
actual fun rememberAudioFilesPicker(onPicked: (List<LocalFileEntry>) -> Unit): () -> Unit = { }
