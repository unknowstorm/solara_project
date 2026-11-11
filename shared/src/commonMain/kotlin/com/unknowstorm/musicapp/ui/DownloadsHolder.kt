package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unknowstorm.musicapp.downloads.DownloadManager

/**
 * The download manager for places that have no direct way to get it (the big player).
 * The main screen puts it here; a screen that changes simply starts showing the download button.
 */
object DownloadsHolder {
    var manager: DownloadManager? by mutableStateOf(null)
}
