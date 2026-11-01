package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.provider.youtube.YouTubeApi
import com.unknowstorm.musicapp.provider.youtube.YouTubeException
import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.youtube_logo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val FLAG_ACCEPTED = "youtube_terms_accepted_v1"
private const val YOUTUBE_HOME = "https://www.youtube.com"

/** Раздел YouTube: сначала согласие с условиями, затем поиск и встроенный плеер. */
@Composable
fun YouTubeScreen(audio: AudioBackend, session: YouTubeSession, onBack: () -> Unit) {
    val flags = rememberFlagStore()
    var accepted by remember { mutableStateOf(flags.get(FLAG_ACCEPTED)) }
    if (accepted) {
        YouTubeBrowser(audio, session, onBack)
    } else {
        YouTubeConsentScreen(
            onAccept = {
                flags.set(FLAG_ACCEPTED, true)
                accepted = true
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun YouTubeBrowser(audio: AudioBackend, session: YouTubeSession, onBack: () -> Unit) {
    val api = remember { YouTubeApi.shared }
    val openLink = rememberExternalLinkOpener()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var heading by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<YouTubeVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(label: String, quiet: Boolean = false, request: suspend () -> List<YouTubeVideo>) {
        scope.launch {
            loading = true
            error = null
            try {
                videos = request()
                heading = label
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                if (!quiet) error = e.message
            } catch (e: Exception) {
                if (!quiet) error = "Не удалось загрузить: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load("Популярная музыка", quiet = true) { api.popularMusic() } }

    // Ушли с экрана YouTube: играющее видео переезжает в плавающее окно
    DisposableEffect(Unit) { onDispose { session.minimize() } }

    val current = session.video
    val showPlayer = current != null && session.docked
    PlatformBackHandler(enabled = showPlayer, onBack = { session.minimize() })

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { if (showPlayer) session.minimize() else onBack() }) {
                Text(if (showPlayer) "Свернуть" else "← Назад")
            }
            Spacer(Modifier.weight(1f))
            // Логотип ведёт на YouTube: на видео, если оно открыто, иначе на главную
            YouTubeLogo(
                onClick = {
                    openLink(if (showPlayer && current != null) "$YOUTUBE_HOME/watch?v=${current.id}" else YOUTUBE_HOME)
                },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!showPlayer || current == null) {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Поиск на YouTube") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val q = query.trim()
                                if (q.isNotEmpty() && !loading) {
                                    focusManager.clearFocus()
                                    load("Результаты: $q") { api.search(q) }
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Text(
                        "Поиск запускается по нажатию Enter: у YouTube дневной лимит запросов на всё приложение.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (heading.isNotEmpty()) {
                        Text(
                            heading,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else if (!loading && error == null) {
                        Text(
                            "Введите запрос и нажмите Enter.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(videos, key = { it.id }) { video ->
                            VideoRow(video) {
                                // Звук нашего плеера и YouTube не должны играть одновременно
                                audio.stop()
                                session.open(video, videos)
                            }
                        }
                    }
                }
            } else {
                PlayerPane(current, session, openLink)
            }
        }

        LegalFooter()
    }
}

@Composable
private fun PlayerPane(video: YouTubeVideo, session: YouTubeSession, openLink: (String) -> Unit) {
    // Ушли с развёрнутого вида: прежние координаты больше недействительны
    DisposableEffect(Unit) { onDispose { session.dockBounds = null } }
    Column(
        Modifier
            .fillMaxSize()
            // Свайп вниз ниже видео сворачивает его. Плеер перекрывает свою область и жестов не получает.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 72.dp.toPx()) session.minimize() },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                )
            },
    ) {
        // Здесь стоит плеер: он рисуется поверх этого места общим слоем (YouTubeOverlay).
        // Вокруг плеера нет наложений: наши элементы стоят только под ним.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .onGloballyPositioned { session.dockBounds = it.boundsInWindow() },
        )
        // Кнопки стоят сразу под видео, поэтому не смещаются при длинном названии
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { session.previous() },
                enabled = session.hasPrev,
                modifier = Modifier.weight(1f),
            ) {
                YtSkipGlyph(false, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Предыдущее")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { session.next() },
                enabled = session.hasNext,
                modifier = Modifier.weight(1f),
            ) {
                Text("Следующее")
                Spacer(Modifier.width(8.dp))
                YtSkipGlyph(true, Modifier.size(16.dp))
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleLarge)
            Text(
                video.channel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.errorCode?.let {
                Spacer(Modifier.height(8.dp))
                Text(playerErrorText(it), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { openLink("https://music.youtube.com/watch?v=${video.id}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Открыть в YouTube Music") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openLink("$YOUTUBE_HOME/watch?v=${video.id}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Открыть в YouTube") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { session.close() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Закрыть видео") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Видео играет, пока оно видно на экране: в полном виде или в плавающем окне, которое можно " +
                    "перетаскивать за верхнюю полосу (быстрый свайп вверх по ней разворачивает окно). Свайп вниз под видео сворачивает его в окно. Если свернуть приложение или выключить экран, видео " +
                    "встаёт на паузу (правила YouTube). Фоновое воспроизведение работает в официальных " +
                    "приложениях YouTube и YouTube Music.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoRow(video: YouTubeVideo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Миниатюра без обрезки и без значков поверх: по правилам её нельзя менять
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(168.dp).aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                video.channel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.durationMs > 0) {
                Text(
                    formatYtDuration(video.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Официальный логотип YouTube на сплошном белом фоне, без изменений. Ведёт на YouTube. */
@Composable
fun YouTubeLogo(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(Res.drawable.youtube_logo),
            contentDescription = "YouTube",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).height(24.dp),
        )
    }
}

private fun playerErrorText(code: Int): String = when (code) {
    101, 150 -> "Автор запретил встраивание этого видео. Откройте его в YouTube."
    100 -> "Видео недоступно: удалено, закрыто или недоступно в вашем регионе."
    153 -> "YouTube не смог проверить приложение. Откройте видео в YouTube."
    5 -> "Ошибка плеера. Попробуйте ещё раз или откройте видео в YouTube."
    else -> "Видео не удалось воспроизвести (код $code)."
}

private fun formatYtDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
}
