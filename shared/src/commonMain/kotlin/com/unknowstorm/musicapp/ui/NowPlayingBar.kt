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
 * Теперь это НЕ мини-плеер, а невидимая «метка места».
 *
 * Настоящий мини-плеер рисует [ExpandablePlayer] в корне приложения (App.kt), чтобы он мог плавно
 * превращаться в полный экран. Эта метка нужна только затем, чтобы:
 *  1) зарезервировать в MainScreen место под мини-плеер (как раньше, высота 64dp);
 *  2) сообщить [ExpandablePlayer], где именно это место находится.
 *
 * Сигнатура осталась прежней, поэтому MainScreen менять не нужно.
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