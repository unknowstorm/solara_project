package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unknowstorm.musicapp.auth.AuthRepository
import com.unknowstorm.musicapp.auth.AuthState
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
fun MainScreen(
    providers: List<MusicProvider>,
    audio: AudioBackend,
    authRepo: AuthRepository,
    onOpenPlayer: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var service by remember { mutableStateOf(Service.ALL) }

    val feeds = remember(providers) {
        providers.mapNotNull { provider ->
            val s = Service.entries.firstOrNull { it.name == provider.id.toString() }
            s?.let { HomeFeed(it, provider, defaultMixes(it)) }
        }
    }
    val shownFeeds = remember(feeds, service) {
        if (service == Service.ALL) feeds else feeds.filter { it.service == service }
    }

    // Делаем фон колонки прозрачным (чтобы не было черных полос)
    Column(Modifier.fillMaxSize().background(Color.Transparent)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(shownFeeds, service, { service = it }, audio)
                Tab.SEARCH -> TrackListScreen(providers, audio)
                Tab.QUEUE -> InfoScreen(
                    "Очередь",
                    "Здесь будет текущая очередь: удаление треков, шафл и повтор списка. Следующий этап.",
                )
                Tab.SETTINGS -> SettingsScreen(authRepo, onOpenAuth)
            }
        }

        // Мини-плеер лежит над нижней панелью
        NowPlayingBar(
            audio = audio,
            onOpen = onOpenPlayer,
            // Убираем лишние отступы, чтобы плеер прилегал вплотную
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // ИСПРАВЛЕНИЕ: Убираем тень у NavigationBar и делаем его прозрачным
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp, // Убирает черную тень!
            modifier = Modifier.background(Color.Transparent)
        ) {
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
private fun SettingsScreen(authRepo: AuthRepository, onOpenAuth: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        AccountBlock(authRepo, onOpenAuth)

        // Кнопка смены пароля: только для вошедшего пользователя
        val authState by authRepo.state.collectAsState()
        var showChangePassword by remember { mutableStateOf(false) }
        if (authState is AuthState.SignedIn) {
            TextButton(onClick = { showChangePassword = true }) { Text("Сменить пароль") }
        }
        if (showChangePassword) {
            ChangePasswordDialog(repo = authRepo, onDismiss = { showChangePassword = false })
        }
        Spacer(Modifier.height(24.dp))

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