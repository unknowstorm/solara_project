package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.spotify.SpotifyItem
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState

/**
 * Страница Spotify на главной, в том же стиле, что и остальные сервисы: общая панель сверху и ряды подборок.
 * Ряды Spotify занимают строку целиком, с другими источниками не смешиваются. Запуск по ссылке живёт на вкладке «Поиск».
 */
@Composable
fun SpotifyScreen(
    remote: SpotifyRemote?,
    onServiceChange: (Service) -> Unit,
    onOpenLikes: () -> Unit,
    onBack: () -> Unit,
    onBeforePlay: () -> Unit,
) {
    val ui = LocalSpotifyMini.current
    val s = remote?.state?.collectAsState()?.value
    PlatformBackHandler(
        enabled = !ui.expanded,
        onBack = { if (s?.browse != null) remote?.closeBrowse() else onBack() },
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Правила Spotify: его бренд не соседствует с брендами других сервисов, поэтому здесь нет панели с их логотипами
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
        }
        if (remote == null || s == null) {
            StatusColumn {
                SpotifyHeader()
                Text("Раздел Spotify доступен только в Android-версии.", Modifier.padding(horizontal = 16.dp))
            }
            return@Column
        }
        when {
            !s.appInstalled -> StatusColumn {
                SpotifyHeader()
                Text(
                    "Для этого раздела нужно приложение Spotify: оно играет музыку само, Solara только управляет им.",
                    Modifier.padding(horizontal = 16.dp),
                )
                Button(onClick = remote::getSpotifyApp, modifier = Modifier.padding(horizontal = 16.dp)) { Text("GET SPOTIFY FREE") }
            }
            !s.connected -> StatusColumn {
                SpotifyHeader()
                Text(
                    "Музыку играет приложение Spotify, Solara управляет им как пульт. Нужен Spotify Premium. " +
                        "Solara ничего из Spotify не сохраняет. После первого подключения оно происходит автоматически.",
                    Modifier.padding(horizontal = 16.dp),
                )
                s.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = remote::connect,
                    enabled = !s.connecting,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(if (s.connecting) "Подключение…" else "Подключить Spotify") }
            }
            s.premium == false -> StatusColumn {
                SpotifyHeader()
                Text("Управление воспроизведением доступно только с подпиской Spotify Premium.", Modifier.padding(horizontal = 16.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotifyOpenButton(remote::openSpotifyApp)
                    TextButton(onClick = remote::disconnect) { Text("Отключить Spotify") }
                }
            }
            else -> SpotifyHome(remote, s, onBeforePlay)
        }
    }
}

@Composable
private fun ColumnScope.StatusColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Полный логотип Spotify (атрибуция контента) и необязательная подсказка. */
@Composable
private fun SpotifyHeader(hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpotifyLogoBar()
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpotifySectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SpotifyRowLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SpotifyHome(remote: SpotifyRemote, s: SpotifyState, onBeforePlay: () -> Unit) {
    val ui = LocalSpotifyMini.current
    LaunchedEffect(Unit) { if (s.shelves == null) remote.loadRecommended() }

    val play: (SpotifyItem) -> Unit = { item ->
        onBeforePlay(); ui.dismissedUri = null; remote.playUri(item.uri)
    }
    val tap: (SpotifyItem) -> Unit = { item ->
        if (item.playable) play(item) else remote.openItem(item)
    }
    val pad = PaddingValues(horizontal = 16.dp)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") {
            SpotifyHeader("Музыку по ссылке можно запустить во вкладке «Поиск»: кнопка «+», затем Spotify.")
        }

        s.error?.let { message ->
            item(key = "error") {
                Text(message, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
            }
        }

        val browse = s.browse
        if (browse != null) {
            // Открыта внутренняя подборка: показываем её вместо рядов
            item(key = "browse-head") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.browseTitle ?: "Подборка", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = remote::closeBrowse) { Text("← К подборкам") }
                }
            }
            item(key = "browse-row") {
                SpotifyTileRow(browse, s.covers, contentPadding = pad, onOpenSpotify = remote::openSpotifyApp, onClick = tap)
            }
        } else {
            val recent = s.history
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    Column {
                        SpotifySectionTitle("Недавно играло")
                        SpotifyTileRow(recent, s.covers, contentPadding = pad, onOpenSpotify = remote::openSpotifyApp, onClick = play)
                    }
                }
            }

            item(key = "shelves-title") { SpotifySectionTitle("Подборки для вас") }
            val shelves = s.shelves
            when {
                shelves == null -> item(key = "loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
                shelves.isEmpty() -> item(key = "empty") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Spotify не вернул подборки. Откройте приложение Spotify, включите что-нибудь и повторите.")
                        TextButton(onClick = remote::loadRecommended) { Text("Повторить") }
                    }
                }
                else -> shelves.forEachIndexed { i, shelf ->
                    item(key = "shelf:$i") {
                        Column {
                            SpotifyRowLabel(shelf.title)
                            SpotifyTileRow(shelf.items, s.covers, contentPadding = pad, onOpenSpotify = remote::openSpotifyApp, onClick = tap)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpotifyOpenButton(remote::openSpotifyApp)
                TextButton(onClick = remote::disconnect) { Text("Отключить Spotify") }
            }
        }
    }
}
