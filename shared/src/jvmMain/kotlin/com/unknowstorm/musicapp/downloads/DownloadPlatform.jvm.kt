package com.unknowstorm.musicapp.downloads

import androidx.compose.runtime.Composable

/** There are no downloads on the desktop version yet. */
@Composable
actual fun rememberDownloadPlatform(): DownloadPlatform = NoDownloads

private object NoDownloads : DownloadPlatform {
    override suspend fun download(url: String, fileName: String, onProgress: (Float) -> Unit): String =
        throw UnsupportedOperationException("Downloads are available on Android only")
    override fun exists(path: String) = false
    override fun size(path: String) = 0L
    override fun delete(path: String) {}
    override fun toast(text: String) {}
}
