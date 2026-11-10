package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable

/**
 * Fullscreen window mode while [active] = true.
 * Android: hides the system bars and rotates the screen to landscape; on exit restores everything as it was.
 * Desktop: does nothing (the video already stretches over the whole app window).
 */
@Composable
expect fun FullscreenWindowEffect(active: Boolean)
