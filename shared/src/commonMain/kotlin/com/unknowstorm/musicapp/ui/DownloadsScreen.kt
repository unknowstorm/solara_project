package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.downloads.DownloadManager
import com.unknowstorm.musicapp.downloads.toPlayable
import com.unknowstorm.musicapp.i18n.S
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.player.AudioBackend

private fun humanSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    if (mb >= 100) return "${mb.toInt()} MB"
    val tenths = (mb * 10).toInt()
    return "${tenths / 10}.${tenths % 10} MB"
}

private fun humanDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * The ↓ button next to a track: save for listening without internet.
 * It is shown only for Jamendo tracks, and not at all when we know that the author did not allow downloading.
 * ↓ (not saved) → a ring (saving, tap to cancel) → ✓ (saved).
 */
@Composable
fun DownloadButton(track: Track, downloads: DownloadManager, modifier: Modifier = Modifier) {
    if (track.provider == ProviderId.JAMENDO && track.downloadable != false) {
        val items by downloads.items.collectAsState()
        val active by downloads.progress.collectAsState()
        val key = downloads.keyOf(track)
        val progress = active[key]
        val saved = items.any { downloads.keyOf(it) == key }
        Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
            when {
                saved -> Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                progress != null -> Box(
                    Modifier.size(40.dp).clickable { downloads.cancel(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress >= 0f) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                else -> IconButton(onClick = { downloads.download(track) }) {
                    Text("↓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** «Cache / downloads»: what is saved on the phone. It plays without internet. */
@Composable
fun DownloadsContent(downloads: DownloadManager, audio: AudioBackend, modifier: Modifier = Modifier) {
    val items by downloads.items.collectAsState()
    val active by downloads.progress.collectAsState()
    val playback by audio.state.collectAsState()
    val tracks = remember(items) { items.map { it.toPlayable() } }
    val totalBytes = items.sumOf { it.sizeBytes }

    Column(modifier.fillMaxSize()) {
        if (items.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    S.downloadsSummary(items.size, humanSize(totalBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { audio.playQueue(tracks, 0) }) { Text(S.playAll) }
            }
        }
        if (active.isNotEmpty()) {
            Text(
                S.downloadingNow(active.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        if (items.isEmpty()) {
            Text(
                S.downloadsEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(items, key = { _, d -> downloads.keyOf(d) }) { index, d ->
                val current = playback.provider == ProviderId.JAMENDO && playback.mediaId == d.id
                ListItem(
                    modifier = Modifier.clickable { audio.playQueue(tracks, index) },
                    leadingContent = {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))) {
                            // The cover from the file works without internet
                            CoverImage(d.coverPath?.let { "file://$it" } ?: d.artwork, Modifier.fillMaxSize())
                        }
                    },
                    headlineContent = { Text(d.title, maxLines = 1) },
                    supportingContent = { Text("${d.artist} · ${humanSize(d.sizeBytes)}", maxLines = 1) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d.durationMs > 0) {
                                Text(humanDuration(d.durationMs), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { downloads.remove(downloads.keyOf(d)) }) { Text("✕") }
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
