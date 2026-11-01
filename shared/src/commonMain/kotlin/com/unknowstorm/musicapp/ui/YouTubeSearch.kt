package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.youtube.YouTubeApi
import com.unknowstorm.musicapp.provider.youtube.YouTubeException
import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import kotlinx.coroutines.CancellationException

/**
 * Выдача YouTube на вкладке «Поиск». Отдельно от других источников (правила YouTube запрещают
 * смешивать результаты). Запрос уходит только когда пользователь нажал Enter: [submitted].
 */
@Composable
fun YouTubeSearchPane(
    submitted: String,
    audio: AudioBackend,
    session: YouTubeSession,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YouTubeConsentGate(onLeave = onLeave) {
        val api = remember { YouTubeApi.shared }
        val openLink = rememberExternalLinkOpener()
        var videos by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
        var heading by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(submitted) {
            if (submitted.isEmpty()) return@LaunchedEffect
            loading = true
            error = null
            try {
                videos = api.search(submitted)
                heading = "Результаты: $submitted"
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                error = e.message
            } catch (e: Exception) {
                error = "Не удалось загрузить: ${e.message}"
            } finally {
                loading = false
            }
        }

        Column(modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YouTubeLogo(onClick = { openLink(YOUTUBE_HOME) })
                Spacer(Modifier.width(12.dp))
                Text(
                    "Поиск запускается по Enter: у YouTube дневной лимит запросов на всё приложение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (heading.isNotEmpty()) {
                Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } else if (!loading && error == null) {
                Text("Введите запрос и нажмите Enter на клавиатуре.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                items(videos) { video ->
                    VideoRow(video) {
                        // Звук нашего плеера и YouTube не должны играть одновременно
                        audio.stop()
                        session.open(video, videos)
                    }
                }
                item { LegalFooter() }
            }
        }
    }
}
