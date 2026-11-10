package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.unknowstorm.musicapp.player.AudioBackend

/**
 * Now this is NOT the mini player but an invisible “placeholder marker”.
 *
 * The real mini player is drawn by [ExpandablePlayer] at the app root (App.kt), so that it can smoothly
 * turn into the full screen. This marker is only needed to:
 *  1) reserve space for the mini player in MainScreen (as before, height 64dp);
 *  2) tell [ExpandablePlayer] where exactly this place is.
 *
 * The signature stayed the same, so MainScreen does not need to change.
 */
@Composable
fun NowPlayingBar(
    audio: AudioBackend,
    @Suppress("UNUSED_PARAMETER") onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchor = LocalMiniPlayerAnchor.current
    val playerState = audio.state.collectAsState()
    val hasTrack by remember { derivedStateOf { playerState.value.title != null } }
    if (!hasTrack) return

    DisposableEffect(anchor) { onDispose { anchor.bounds = null } }

    Box(
        modifier
            .fillMaxWidth()
            .height(MiniHeight)
            .onGloballyPositioned { anchor.bounds = it.boundsInRoot() }
    )
}