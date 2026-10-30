package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.model.Service
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.MusicProvider

enum class Tab(val label: String) {
    HOME("Главная"),
    SEARCH("Поиск"),
    QUEUE("Очередь"),
    SETTINGS("Настройки"),
}

@Composable
fun MainScreen(providers: List<MusicProvider>, audio: AudioBackend, onOpenPlayer: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var service by remember { mutableStateOf(Service.ALL) }

    // Источник сопоставляется с чипом по имени: ProviderId.JAMENDO <-> Service.JAMENDO
    val feeds = remember(providers) {
        providers.mapNotNull { provider ->
            val s = Service.entries.firstOrNull { it.name == provider.id.toString() }
            s?.let { HomeFeed(it, provider, defaultMixes(it)) }
        }
    }
    val shownFeeds = remember(feeds, service) {
        if (service == Service.ALL) feeds else feeds.filter { it.service == service }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(shownFeeds, service, { service = it }, audio)
                Tab.SEARCH -> TrackListScreen(providers, audio)
                Tab.QUEUE -> InfoScreen(
                    "Очередь",
                    "Здесь будет текущая очередь: удаление треков, шафл и повтор списка. Следующий этап.",
                )
                Tab.SETTINGS -> SettingsScreen()
            }
        }

        // Мини-плеер лежит над нижней панелью, как на наброске
        NowPlayingBar(audio, onOpenPlayer, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

        NavigationBar {
            Tab.entries.forEach { item ->
                NavigationBarItem(
                    selected = tab == item,
                    onClick = { tab = item },
                    icon = {
                        when (item) {
                            Tab.HOME -> HomeIcon()
                            Tab.SEARCH -> SearchIcon()
                            Tab.QUEUE -> QueueIcon()
                            Tab.SETTINGS -> SettingsIcon()
                        }
                    },
                    label = { Text(item.label) },
                )
            }
        }
    }
}

@Composable
private fun InfoScreen(title: String, body: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Источники музыки", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Service.entries.filter { it != Service.ALL }.forEach { service ->
            ListItem(
                headlineContent = { Text(service.title) },
                supportingContent = {
                    Text(if (service.ready) "Работает. ${service.note}" else "Скоро. ${service.note}")
                },
            )
        }
    }
}
