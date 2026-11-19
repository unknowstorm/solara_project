package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.spotify.QueueStatus
import com.unknowstorm.musicapp.spotify.SpotifyLibTrack
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState

private fun sqTitle() = tr("Spotify queue", "Черга Spotify")
private fun sqNow() = tr("Now playing", "Зараз грає")
private fun sqNext() = tr("Next up", "Далі в черзі")
private fun sqSkipped() = tr("Skipped · tap to add back to the queue", "Пропущені · торкніться, щоб додати в чергу")
private fun sqEmpty() = tr("Nothing is queued after this track.", "Після цього треку нічого немає в черзі.")
private fun sqIntro() = tr(
    "To show the queue, Solara needs read-only access to your playback state on Spotify. You sign in on Spotify's own page. " +
        "The queue is only shown here and is never saved. You can turn this off at any time.",
    "Щоб показати чергу, Solara потрібен доступ лише на читання до стану відтворення у Spotify. Ви входите на сторінці самого Spotify. " +
        "Черга лише показується тут і ніколи не зберігається. Це можна вимкнути в будь-який момент.",
)
private fun sqAllow() = tr("Allow queue view", "Дозволити перегляд черги")
private fun sqWaiting() = tr("Finish the sign-in in the browser, then come back.", "Завершіть вхід у браузері й поверніться.")
private fun sqRefresh() = tr("Refresh", "Оновити")
private fun sqOff() = tr("Turn off", "Вимкнути")
private fun sqClose() = tr("Close", "Закрити")
private fun sqOpen() = tr("Open Spotify", "Відкрити Spotify")

/** "Show the queue" icon: three lines and a play mark, drawn on a 24x24 grid. */
@Composable
internal fun SpotifyQueueViewIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier.semantics { contentDescription = sqTitle() }) {
        val k = size.width / 24f
        val stroke = 2f * k
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), stroke, StrokeCap.Round)
        line(4f, 6f, 15f, 6f)
        line(4f, 11f, 15f, 11f)
        line(4f, 16f, 11f, 16f)
        val play = Path().apply {
            moveTo(15f * k, 14f * k)
            lineTo(22f * k, 18f * k)
            lineTo(15f * k, 22f * k)
            close()
        }
        drawPath(play, tint)
    }
}

/**
 * Read-only queue of the Spotify player (Web API), as a page inside the app window (not a separate dialog window),
 * so the real mini player stays visible at the bottom with all its behaviour (tap to expand, swipe away, pause).
 * The page ends where the mini player begins; the area below it (the bottom bar) closes the page when tapped.
 * Every row links back to the track in Spotify. Nothing is stored; the data is erased when the page closes.
 */
@Composable
fun SpotifyQueuePage(remote: SpotifyRemote, onClose: () -> Unit) {
    val s by remote.state.collectAsState()
    val ui = LocalSpotifyMini.current
    val anchor = ui.anchor
    LaunchedEffect(Unit) { remote.openQueue() }
    DisposableEffect(Unit) { onDispose { remote.closeQueue() } }
    LaunchedEffect(s.connected) { if (!s.connected) onClose() }
    // While the full player is open it handles Back itself and returns here
    PlatformBackHandler(enabled = !ui.expanded, onBack = onClose)
    val q = s.queue
    var originY by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { originY = it.positionInRoot().y }) {
        val density = LocalDensity.current
        val rootH = constraints.maxHeight.toFloat()
        val miniTop = anchor.bounds?.top?.minus(originY)
        val reserved = if (ui.shown && miniTop != null) with(density) { (rootH - miniTop).coerceAtLeast(0f).toDp() } else 0.dp
        Surface(
            Modifier.fillMaxSize().padding(bottom = reserved),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.statusBarsPadding().then(if (reserved == 0.dp) Modifier.navigationBarsPadding() else Modifier).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(sqClose()) }
                    Spacer(Modifier.weight(1f))
                    SpotifyLogoBar()
                }
                Text(sqTitle(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (q.status) {
                        QueueStatus.OFF, QueueStatus.LOADING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        QueueStatus.NEEDS_CONSENT -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(sqIntro(), style = MaterialTheme.typography.bodyLarge)
                            q.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = { remote.grantQueueAccess() }) { Text(sqAllow()) }
                        }
                        QueueStatus.AUTHORIZING -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(sqWaiting(), style = MaterialTheme.typography.bodyLarge)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        QueueStatus.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(q.error ?: "", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { remote.refreshQueue() }) { Text(sqRefresh()) }
                        }
                        QueueStatus.READY -> LazyColumn(Modifier.fillMaxSize()) {
                            if (q.skipped.isNotEmpty()) {
                                item(key = "skipped-label") { SqLabel(sqSkipped()) }
                                itemsIndexed(q.skipped, key = { _, t -> "skipped:${t.uri}" }) { _, t ->
                                    SqRow(t, remote, onPlay = { remote.queueUri(t.uri); remote.dismissSkipped(t.uri) })
                                }
                            }
                            q.nowPlaying?.let { now ->
                                item(key = "now-label") { SqLabel(sqNow()) }
                                item(key = "now") { SqRow(now, remote) }
                            }
                            item(key = "next-label") { SqLabel(sqNext()) }
                            if (q.next.isEmpty()) {
                                item(key = "empty") {
                                    Text(
                                        sqEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            } else {
                                itemsIndexed(q.next) { i, t -> SqRow(t, remote, onPlay = { remote.jumpInQueue(i) }) }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpotifyOpenButton({ remote.openSpotifyApp() })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (q.status == QueueStatus.READY || q.status == QueueStatus.ERROR) {
                            TextButton(onClick = { remote.refreshQueue() }) { Text(sqRefresh()) }
                        }
                        if (q.status != QueueStatus.NEEDS_CONSENT && q.status != QueueStatus.OFF) {
                            TextButton(onClick = { remote.forgetQueue() }) { Text(sqOff()) }
                        }
                    }
                }
            }
        }
        // The bottom bar stays visible below the mini player: a tap there closes the page instead of switching tabs under it
        if (reserved > 0.dp) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(reserved)
                    .pointerInput(Unit) { detectTapGestures { onClose() } }
            )
        }
    }
}

@Composable
private fun SqLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** A queue row: cover without cropping tricks or overlays (4 dp corners), metadata unchanged, link back to Spotify. */
@Composable
private fun SqRow(t: SpotifyLibTrack, remote: SpotifyRemote, onPlay: (() -> Unit)? = null) {
    ListItem(
        modifier = if (onPlay != null) Modifier.clickable { onPlay() } else Modifier,
        leadingContent = {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (t.imageUrl != null) CoverImage(t.imageUrl, Modifier.fillMaxSize(), RoundedCornerShape(4.dp))
            }
        },
        headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            IconButton(onClick = { remote.openInSpotify(t.uri) }) {
                Image(
                    spotifyMonoIcon(MaterialTheme.colorScheme.background),
                    contentDescription = sqOpen(),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
