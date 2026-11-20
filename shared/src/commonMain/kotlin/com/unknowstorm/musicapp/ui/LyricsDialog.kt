package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.provider.LyricsRegistry
import kotlinx.coroutines.CancellationException

/**
 * TODO: move these labels into i18n/Strings.kt (UA + EN) like the rest of the texts.
 * They live here only because Strings.kt was not part of the files this change was made from.
 */
internal object LyricsText {
    const val title = "Lyrics"
    const val noLyrics = "There are no lyrics for this track."
    const val failed = "Could not load the lyrics. Check the connection and try again."
    const val source = "Lyrics: Jamendo"
}

private sealed interface LyricsState {
    object Loading : LyricsState
    object Empty : LyricsState
    object Failed : LyricsState
    class Loaded(val text: String) : LyricsState
}

/** Lyrics of a track, fetched when the dialog opens and kept only in memory. */
@Composable
internal fun LyricsDialog(track: Track, onDismiss: () -> Unit) {
    val state by produceState<LyricsState>(LyricsState.Loading, track.provider, track.id) {
        value = LyricsState.Loading
        val source = LyricsRegistry.jamendo
        value = if (track.provider != ProviderId.JAMENDO || source == null) {
            LyricsState.Empty
        } else {
            try {
                source.fetchLyrics(track)?.let { LyricsState.Loaded(it) } ?: LyricsState.Empty
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Solara: lyrics load failed: ${e.message}")
                LyricsState.Failed
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.close) } },
        title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            when (val s = state) {
                LyricsState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is LyricsState.Loaded -> Column {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(s.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        LyricsText.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                LyricsState.Empty -> Text(LyricsText.noLyrics)
                LyricsState.Failed -> Text(LyricsText.failed, color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** The Material "notes" icon (24dp path), tinted by LocalContentColor. */
internal val PlayerMenuLyricsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Lyrics",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes("M3,18h12v-2H3v2zM3,6v2h18V6H3zM3,13h18v-2H3v2z"),
        fill = SolidColor(Color.Black),
    ).build()
}
