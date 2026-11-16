package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyRepeat
import com.unknowstorm.musicapp.spotify.SpotifyState

/**
 * Shuffle and repeat toggles. The state is Spotify's own (read from its player events), nothing is stored here.
 * A button is disabled when Spotify reports that the action is restricted.
 */
@Composable
internal fun SpotifyShuffleButton(s: SpotifyState, remote: SpotifyRemote, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val label = S.spotifyShuffle
    FilledTonalIconToggleButton(
        checked = s.shuffling,
        onCheckedChange = { remote.setShuffle(it) },
        enabled = s.canShuffle,
        modifier = modifier.size(size).semantics { contentDescription = label },
    ) { SpShuffleIcon(Modifier.size(size * 0.55f)) }
}

/**
 * [cycleTrack] false (track list): off <-> repeat the playlist. true (full player): off -> playlist -> track -> off.
 * Spotify has one repeat mode, so both places show and change the same value.
 */
@Composable
internal fun SpotifyRepeatButton(
    s: SpotifyState,
    remote: SpotifyRemote,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cycleTrack: Boolean = false,
) {
    val label = S.spotifyRepeat
    val on = s.repeat != SpotifyRepeat.OFF
    val enabled = if (cycleTrack) s.canRepeatContext || s.canRepeatTrack else s.canRepeatContext || on
    FilledTonalIconToggleButton(
        checked = on,
        onCheckedChange = { remote.setRepeat(nextRepeat(s, cycleTrack)) },
        enabled = enabled,
        modifier = modifier.size(size).semantics { contentDescription = label },
    ) { SpRepeatIcon(s.repeat == SpotifyRepeat.TRACK, Modifier.size(size * 0.55f)) }
}

private fun nextRepeat(s: SpotifyState, cycleTrack: Boolean): SpotifyRepeat {
    if (!cycleTrack) return if (s.repeat == SpotifyRepeat.OFF) SpotifyRepeat.CONTEXT else SpotifyRepeat.OFF
    val order = listOf(SpotifyRepeat.OFF, SpotifyRepeat.CONTEXT, SpotifyRepeat.TRACK)
    var mode = s.repeat
    repeat(order.size) {
        mode = order[(order.indexOf(mode) + 1) % order.size]
        val allowed = when (mode) {
            SpotifyRepeat.OFF -> true
            SpotifyRepeat.CONTEXT -> s.canRepeatContext
            SpotifyRepeat.TRACK -> s.canRepeatTrack
        }
        if (allowed) return mode
    }
    return SpotifyRepeat.OFF
}

// ---------- Icons: a 24x24 grid, rounded strokes, like the other icons in the app ----------

private fun modeStroke(width: Float = 2f) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Two crossing arrows. */
@Composable
private fun SpShuffleIcon(modifier: Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val p = Path().apply {
                moveTo(3f, 7f); lineTo(6.5f, 7f); cubicTo(11f, 7f, 10f, 17f, 14.5f, 17f); lineTo(20f, 17f)
                moveTo(3f, 17f); lineTo(6.5f, 17f); cubicTo(11f, 17f, 10f, 7f, 14.5f, 7f); lineTo(20f, 7f)
                moveTo(17.5f, 4.5f); lineTo(20.5f, 7f); lineTo(17.5f, 9.5f)
                moveTo(17.5f, 14.5f); lineTo(20.5f, 17f); lineTo(17.5f, 19.5f)
            }
            drawPath(p, tint, style = modeStroke())
        }
    }
}

/** A loop of two arrows; with a "1" inside when a single track is repeated. */
@Composable
private fun SpRepeatIcon(one: Boolean, modifier: Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val loop = Path().apply {
                moveTo(4f, 12f); lineTo(4f, 10f); cubicTo(4f, 8f, 5.5f, 7f, 7f, 7f); lineTo(19f, 7f)
                moveTo(16.5f, 4.5f); lineTo(19.5f, 7f); lineTo(16.5f, 9.5f)
                moveTo(20f, 12f); lineTo(20f, 14f); cubicTo(20f, 16f, 18.5f, 17f, 17f, 17f); lineTo(5f, 17f)
                moveTo(7.5f, 14.5f); lineTo(4.5f, 17f); lineTo(7.5f, 19.5f)
            }
            drawPath(loop, tint, style = modeStroke())
            if (one) {
                val digit = Path().apply { moveTo(10.6f, 10.6f); lineTo(12.4f, 9.6f); lineTo(12.4f, 14.6f) }
                drawPath(digit, tint, style = modeStroke(1.6f))
            }
        }
    }
}
