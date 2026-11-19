package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.player.AudioBackend

private fun localDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val s = (total % 60).toString().padStart(2, '0')
    return "${total / 60}:$s"
}

/** The list of music files from this phone. Tap a file to play the whole list starting from it. */
@Composable
fun LocalFilesContent(local: LocalLibrary, audio: AudioBackend, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val files by local.files.collectAsState()
    val playback by audio.state.collectAsState()
    val pick = rememberAudioFilesPicker { local.add(it) }
    val tracks = remember(files) { files.map { it.toTrack() } }
    val played = remember { mutableSetOf<Int>() }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The title takes the rest of the row and is ellipsized; the button keeps its natural width on one line
            if (showTitle) {
                Text(
                    S.libLocal,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Button(onClick = pick) { Text(S.addFiles, maxLines = 1) }
        }
        Text(
            S.localFilesNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        if (tracks.isEmpty()) {
            Text(
                S.noLocalFiles,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                val current = playback.provider == ProviderId.LOCAL && playback.mediaId == track.id
                ListItem(
                    modifier = animatedRow()
                        .staggeredEntrance(index, played, maxIndex = 10, stepMs = 35L, shift = 24.dp, fromScale = 0.96f)
                        .clickable { audio.playQueue(tracks, index) },
                    leadingContent = {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
                            CoverImage(track.artworkUrl, Modifier.fillMaxSize())
                        }
                    },
                    headlineContent = { Text(track.title, maxLines = 1) },
                    supportingContent = { Text(track.artist.ifBlank { S.unknownArtist }, maxLines = 1) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (track.durationMs > 0) {
                                Text(localDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { local.remove(track.id) }) { CrossIcon(Modifier.size(20.dp)) }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ),
                )
            }
        }
    }
}
