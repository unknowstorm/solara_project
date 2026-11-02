package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.spotify_logo
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState
import com.unknowstorm.musicapp.spotify.parseSpotifyLink
import org.jetbrains.compose.resources.painterResource

/** Изолированный раздел Spotify: ни в «Все», ни в поиск, ни в очередь, ни в лайки не попадает. */
@Composable
fun SpotifyScreen(remote: SpotifyRemote?, onBack: () -> Unit, onBeforePlay: () -> Unit) {
    PlatformBackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
        }
        // Полный логотип на чёрном фоне, без изменений
        Surface(color = Color.Black, shape = RoundedCornerShape(8.dp)) {
            Image(
                painterResource(Res.drawable.spotify_logo),
                contentDescription = "Spotify",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).height(32.dp),
            )
        }
        if (remote == null) {
            Text("Раздел Spotify доступен только в Android-версии.")
            return@Column
        }
        val s by remote.state.collectAsState()
        when {
            !s.appInstalled -> {
                Text("Для этого раздела нужно приложение Spotify: оно играет музыку само, Solara только управляет им.")
                Button(onClick = remote::getSpotifyApp) { Text("GET SPOTIFY FREE") }
            }
            !s.connected -> {
                Text(
                    "Музыку играет приложение Spotify, Solara управляет им как пульт. Нужен Spotify Premium. " +
                        "Solara ничего из Spotify не сохраняет.",
                )
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = remote::connect, enabled = !s.connecting) {
                    Text(if (s.connecting) "Подключение…" else "Подключить Spotify")
                }
            }
            s.premium == false -> {
                Text("Управление воспроизведением доступно только с подпиской Spotify Premium.")
                OutlinedButton(onClick = remote::openSpotifyApp) { Text("OPEN SPOTIFY") }
                TextButton(onClick = remote::disconnect) { Text("Отключить Spotify") }
            }
            else -> SpotifyConnected(remote, s, onBeforePlay)
        }
    }
}

@Composable
private fun SpotifyConnected(remote: SpotifyRemote, s: SpotifyState, onBeforePlay: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = link,
        onValueChange = { link = it; linkError = false },
        label = { Text("Ссылка Spotify на трек, альбом или плейлист") },
        isError = linkError,
        supportingText = { if (linkError) Text("Не похоже на ссылку Spotify") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = {
        val uri = parseSpotifyLink(link)
        if (uri == null) linkError = true else { onBeforePlay(); remote.playUri(uri) }
    }) { Text("Играть по ссылке") }
    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (s.title != null) {
        // Обложка без обрезки, блюра и надписей; углы 4 dp
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            s.cover?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Text(s.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(s.artist ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

        var drag by remember { mutableStateOf<Float?>(null) }
        val frac = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
        Slider(
            value = drag ?: frac,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { remote.seekTo((it * s.durationMs).toLong()) }
                drag = null
            },
            enabled = s.durationMs > 0,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = remote::toggleSaved) {
                if (s.saved) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                else AddIcon(Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = remote::previous, modifier = Modifier.size(56.dp)) { SkipIcon(next = false, modifier = Modifier.size(32.dp)) }
            FilledIconButton(
                onClick = { if (s.isPaused) remote.resume() else remote.pause() },
                modifier = Modifier.size(72.dp),
            ) { PlayPauseIcon(!s.isPaused, Modifier.size(40.dp)) }
            IconButton(onClick = remote::next, modifier = Modifier.size(56.dp)) { SkipIcon(next = true, modifier = Modifier.size(32.dp)) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = remote::openSpotifyApp) { Text("OPEN SPOTIFY") }
        TextButton(onClick = remote::disconnect) { Text("Отключить Spotify") }
    }
}
