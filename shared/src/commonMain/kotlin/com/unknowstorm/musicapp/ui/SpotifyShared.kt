package com.unknowstorm.musicapp.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.resources.Res
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import com.unknowstorm.musicapp.resources.spotify_icon_black
import com.unknowstorm.musicapp.resources.spotify_icon_white
import com.unknowstorm.musicapp.resources.spotify_logo_black
import com.unknowstorm.musicapp.resources.spotify_logo_white
import com.unknowstorm.musicapp.spotify.SpotifyItem
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.parseSpotifyLink
import org.jetbrains.compose.resources.painterResource

/** Полный логотип Spotify на чёрном фоне, без изменений. */
@Composable
internal fun SpotifyLogoBar(modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp)) {
        Image(spotifyMonoLogo(MaterialTheme.colorScheme.background), contentDescription = "Spotify", modifier = Modifier.height(24.dp))
    }
}

/** Обложка Spotify: без обрезки, надписей, значков и блюра, углы 4 dp. */
@Composable
internal fun SpotifyCover(bitmap: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
internal fun SpotifyTile(item: SpotifyItem, covers: Map<String, ImageBitmap>, onClick: () -> Unit) {
    // У верхнеуровневых подборок Spotify не отдаёт обложку: показываем нейтральную плитку с названием
    if (item.imageKey.isNullOrBlank()) {
        Box(
            Modifier.size(120.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick).padding(10.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        SpotifyCover(covers[item.imageKey], 120.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Ряд плиток Spotify: не больше 20 элементов, в конце ссылка на приложение Spotify. */
@Composable
internal fun SpotifyTileRow(
    list: List<SpotifyItem>,
    covers: Map<String, ImageBitmap>,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    onOpenSpotify: (() -> Unit)? = null,
    onClick: (SpotifyItem) -> Unit,
) {
    val shown = list.distinctBy { it.uri }.take(20)
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shown.size, key = { shown[it].uri }) { i -> SpotifyTile(shown[i], covers) { onClick(shown[i]) } }
        if (onOpenSpotify != null) {
            item {
                // Последняя плитка ряда: тот же размер, что у обложек, углы 4 dp
                Column(
                    Modifier.size(120.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenSpotify).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(spotifyMonoIcon(MaterialTheme.colorScheme.surfaceVariant), contentDescription = "Spotify", modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "OPEN SPOTIFY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// Макет должен вмещать: плейлист/альбом 25 символов, исполнитель 18, трек 23.
// Усечение (многоточие) допустимо только если не хватает места; метаданные не меняем.

/** Режим Spotify на вкладке «Поиск»: запуск по ссылке и недавние треки. Остальных источников здесь нет. */
@Composable
fun SpotifySearchPane(
    remote: SpotifyRemote?,
    onBeforePlay: () -> Unit,
    onOpenSection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = LocalSpotifyMini.current
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SpotifyLogoBar() }
        if (remote == null) {
            Text("Spotify доступен только в Android-версии.")
            return@Column
        }
        val s by remote.state.collectAsState()
        if (!s.connected || s.premium == false) {
            Text(
                if (s.premium == false) "Управление Spotify доступно только с подпиской Premium."
                else "Spotify не подключён. Подключите его один раз в разделе Spotify, дальше подключение будет автоматическим.",
            )
            Button(onClick = onOpenSection) { Text("Открыть раздел Spotify") }
            return@Column
        }
        var link by remember { mutableStateOf("") }
        var linkError by remember { mutableStateOf(false) }
        Text(
            "Поиск по названию для Spotify недоступен. Вставьте ссылку на трек, альбом или плейлист " +
                "(в приложении Spotify: Поделиться → Копировать ссылку).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it; linkError = false },
            label = { Text("Ссылка Spotify") },
            isError = linkError,
            supportingText = { if (linkError) Text("Не похоже на ссылку Spotify") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            val uri = parseSpotifyLink(link)
            if (uri == null) linkError = true else {
                onBeforePlay()
                ui.dismissedUri = null
                remote.playUri(uri)
                link = ""
            }
        }) { Text("Играть по ссылке") }
        // Эксперимент: список треков альбома или плейлиста через Content API App Remote
        val browseUri = parseSpotifyLink(link)
            ?.takeIf { it.startsWith("spotify:album:") || it.startsWith("spotify:playlist:") }
        if (browseUri != null) {
            OutlinedButton(onClick = { remote.openByUri(browseUri); link = ""; onOpenSection() }) { Text("Показать треки") }
        }
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (s.history.isNotEmpty()) {
            Text("Недавно играло", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            SpotifyTileRow(s.history, s.covers) { item ->
                onBeforePlay(); ui.dismissedUri = null; remote.playUri(item.uri)
            }
        }
        TextButton(onClick = onOpenSection) { Text("Открыть раздел Spotify") }
    }
}


/** Монохромная иконка Spotify: белая на тёмном фоне, чёрная на светлом. Зелёную можно только на чёрном или белом. */
@Composable
internal fun spotifyMonoIcon(background: Color): Painter =
    painterResource(if (background.luminance() < 0.179f) Res.drawable.spotify_icon_white else Res.drawable.spotify_icon_black)

/** Кнопка «OPEN SPOTIFY» с иконкой, как в примерах гайдлайнов. */
@Composable
internal fun SpotifyOpenButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Image(spotifyMonoIcon(MaterialTheme.colorScheme.background), contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("OPEN SPOTIFY")
    }
}

/** Монохромный полный логотип Spotify: белый на тёмном фоне, чёрный на светлом. */
@Composable
internal fun spotifyMonoLogo(background: Color): Painter =
    painterResource(if (background.luminance() < 0.179f) Res.drawable.spotify_logo_white else Res.drawable.spotify_logo_black)
