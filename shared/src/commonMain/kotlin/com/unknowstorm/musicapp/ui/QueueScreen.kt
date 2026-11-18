package com.unknowstorm.musicapp.ui

import com.unknowstorm.musicapp.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.unknowstorm.musicapp.downloads.DownloadManager
import com.unknowstorm.musicapp.model.ProviderId
import com.unknowstorm.musicapp.model.Track
import com.unknowstorm.musicapp.model.toService
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.player.QueueItem
import com.unknowstorm.musicapp.player.RepeatMode
import com.unknowstorm.musicapp.playlists.PlaylistsRepository
import com.unknowstorm.musicapp.provider.MusicProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Высота строки очереди постоянная: на ней построено перетаскивание. */
private val RowHeight = 64.dp

// Share of the row width the user must swipe before releasing the finger removes the track
private const val SwipeRemoveFraction = 0.5f

/** Строка очереди с устойчивым номером: по нему Compose отличает строки при перестановке и удалении. */
private class QRow(val uid: Long, val item: QueueItem)

/**
 * Вкладка «Очередь»: что сейчас в нашем плеере (Jamendo, Audius, Deezer).
 * Удерживайте строку и тяните, чтобы поменять порядок; смахните строку, чтобы убрать.
 * Когда очередь пуста, показывается последняя сохранённая очередь с кнопкой «Восстановить».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    audio: AudioBackend,
    playlists: PlaylistsRepository,
    onOpenAuth: () -> Unit,
    providers: List<MusicProvider> = emptyList(),
    store: QueueStore? = null,
    downloads: DownloadManager? = null,
) {
    val scope = rememberCoroutineScope()
    val state by audio.state.collectAsState()
    val queue by audio.queue.collectAsState()
    val signedIn by playlists.signedIn.collectAsState()
    val myPlaylists by playlists.playlists.collectAsState()
    val savedQueue by remember(store) { store?.saved ?: MutableStateFlow<SavedQueue?>(null) }.collectAsState()
    var addTarget by remember { mutableStateOf<Track?>(null) }
    var newForTrack by remember { mutableStateOf<Track?>(null) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var showSave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var restoreFailed by remember { mutableStateOf(false) }

    // Локальная копия очереди: перестановка и удаление видны сразу, не дожидаясь ответа плеера
    val rows = remember { mutableStateListOf<QRow>().apply { queue.forEachIndexed { i, q -> add(QRow(i.toLong(), q)) } } }
    var nextUid by remember { mutableLongStateOf(queue.size.toLong()) }
    var dragUid by remember { mutableStateOf<Long?>(null) }
    var dragShift by remember { mutableFloatStateOf(0f) }
    // Y пальца внутри списка: по нему работает автопрокрутка у краёв
    var dragFingerY by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    val haptic = LocalHapticFeedback.current

    // Подсветка идёт за самой строкой, а не за номером позиции: пока плеер применяет перенос, currentIndex отстаёт
    var holdUid by remember { mutableStateOf<Long?>(null) }
    val currentIndexNow by androidx.compose.runtime.rememberUpdatedState(state.currentIndex)
    LaunchedEffect(dragUid) {
        if (dragUid == null && holdUid != null) {
            delay(300) // плеер применяет последний перенос
            holdUid = null
        }
    }

    // Ставит перетаскиваемую строку в слот под её текущим положением (вызывается и жестом, и автопрокруткой)
    fun reorderStep() {
        val uid = dragUid ?: return
        val cur = rows.indexOfFirst { it.uid == uid }
        if (cur < 0) return
        val target = (cur + (dragShift / rowPx).roundToInt()).coerceIn(0, rows.lastIndex)
        if (target == cur) return
        // LazyColumn держит позицию по КЛЮЧУ первой видимой строки: без этого весь список уезжает за верхней строкой
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        rows.add(target, rows.removeAt(cur))
        listState.requestScrollToItem(firstIndex, firstOffset)
        audio.moveItem(cur, target)
        dragShift -= (target - cur) * rowPx
    }

    // Автопрокрутка: палец у верхнего или нижнего края листает список, чем ближе к краю, тем быстрее
    LaunchedEffect(dragUid) {
        if (dragUid == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val info = listState.layoutInfo
            val height = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val edge = rowPx * 1.2f
            val y = dragFingerY
            val k = when {
                y < edge -> -((edge - y) / edge).coerceIn(0f, 1f)
                y > height - edge -> ((y - (height - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (k != 0f) {
                val used = listState.scrollBy(k * rowPx * 10f * dt)
                if (used != 0f) {
                    // Список уехал на used: строка должна остаться под пальцем
                    dragShift += used
                    reorderStep()
                }
            }
        }
    }

    // Синхронизация с плеером; во время перетаскивания не трогаем список
    LaunchedEffect(queue, dragUid) {
        if (dragUid != null) return@LaunchedEffect
        val pool = rows.toMutableList()
        val next = queue.map { item ->
            val j = pool.indexOfFirst { it.item.mediaId == item.mediaId }
            if (j >= 0) QRow(pool.removeAt(j).uid, item) else QRow(nextUid++, item)
        }
        rows.clear()
        rows.addAll(next)
    }

    LaunchedEffect(note) {
        if (note != null) {
            delay(3_000)
            note = null
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            delay(3_000)
            saved = false
        }
    }

    addTarget?.let { target ->
        AddToPlaylistDialog(
            playlists = myPlaylists,
            busy = addBusy,
            error = addError,
            onPick = { pl ->
                scope.launch {
                    addBusy = true
                    addError = null
                    playlists.addTracks(pl.id, listOf(target)).fold(
                        onSuccess = { n ->
                            addTarget = null
                            note = if (n > 0) S.addedTo(pl.name) else S.alreadyIn(pl.name)
                        },
                        onFailure = { addError = it.message },
                    )
                    addBusy = false
                }
            },
            onNew = { newForTrack = target; addTarget = null },
            onDismiss = { addTarget = null; addError = null },
        )
    }
    newForTrack?.let { target ->
        SavePlaylistDialog(
            saving = addBusy,
            error = addError,
            onSave = { name ->
                scope.launch {
                    addBusy = true
                    addError = null
                    playlists.create(name, listOf(target)).fold(
                        onSuccess = { newForTrack = null; note = S.playlistCreated(name.trim()) },
                        onFailure = { addError = it.message },
                    )
                    addBusy = false
                }
            },
            onDismiss = { newForTrack = null; addError = null },
            initialName = target.title.take(60),
        )
    }

    if (showSave) {
        SavePlaylistDialog(
            saving = saving,
            error = saveError,
            onSave = { name ->
                val tracks = queue.mapNotNull { item ->
                    item.provider?.takeIf { it != ProviderId.LOCAL }
                        ?.let { Track(item.mediaId, it, item.title, item.artist, item.artworkUrl, item.durationMs) }
                }
                scope.launch {
                    saving = true
                    saveError = null
                    playlists.create(name, tracks).fold(
                        onSuccess = { showSave = false; saved = true },
                        onFailure = { saveError = it.message },
                    )
                    saving = false
                }
            },
            onDismiss = { showSave = false; saveError = null },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.queue, style = MaterialTheme.typography.headlineMedium)
            when {
                queue.isNotEmpty() -> Row {
                    TextButton(onClick = { if (signedIn) showSave = true else onOpenAuth() }) { Text(S.save) }
                    // «Очистить» стирает и сохранённую очередь; крестик мини-плеера её оставляет
                    TextButton(onClick = { audio.stop(); store?.clear() }) { Text(S.clear) }
                }
                savedQueue != null -> TextButton(onClick = { store?.clear() }) { Text(S.clear) }
            }
        }

        if (saved) {
            Text(
                S.playlistSavedItWillAppear,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        if (queue.isEmpty()) {
            val sq = savedQueue
            if (sq == null) {
                Text(
                    S.theQueueIsEmptyPlay,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                return@Column
            }
            val restore: (Int) -> Unit = { start ->
                if (!restoring) {
                    scope.launch {
                        restoring = true
                        restoreFailed = false
                        val ok = try {
                            restoreQueue(sq, start, providers, audio)
                        } finally {
                            restoring = false
                        }
                        if (!ok) restoreFailed = true
                    }
                }
            }
            SavedQueueSection(sq, restoring, restoreFailed, restore)
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.shuffle,
                onClick = { audio.toggleShuffle() },
                label = { Text(S.shuffle) },
            )
            FilterChip(
                selected = state.repeatMode != RepeatMode.OFF,
                onClick = { audio.cycleRepeat() },
                label = {
                    Text(
                        when (state.repeatMode) {
                            RepeatMode.OFF -> S.repeatOff
                            RepeatMode.ALL -> S.repeatList
                            RepeatMode.ONE -> S.repeatTrack
                        }
                    )
                },
            )
        }
        Text(
            S.queueHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Долгое нажатие и перетаскивание: строка следует за пальцем и меняется местами с соседями
                .pointerInput(rowPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val idx = listState.firstVisibleItemIndex +
                                ((offset.y + listState.firstVisibleItemScrollOffset) / rowPx).toInt()
                            rows.getOrNull(idx)?.let {
                                dragUid = it.uid
                                dragFingerY = offset.y
                                holdUid = rows.getOrNull(currentIndexNow)?.uid
                                dragShift = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            if (dragUid == null) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragFingerY = change.position.y
                            dragShift += amount.y
                            reorderStep()
                        },
                        onDragEnd = { dragUid = null; dragShift = 0f },
                        onDragCancel = { dragUid = null; dragShift = 0f },
                    )
                },
        ) {
            items(rows, key = { it.uid }) { row ->
                val item = row.item
                val index = rows.indexOfFirst { it.uid == row.uid }
                val current = holdUid?.let { it == row.uid } ?: (index == state.currentIndex)
                val dragging = row.uid == dragUid
                // While a finger is down the row only follows it; the track is removed after release
                val touching = remember(row.uid) { booleanArrayOf(false) }
                val rowWidthPx = remember(row.uid) { floatArrayOf(0f) }
                val scheme = MaterialTheme.colorScheme
                // The library never decides anything: the removal is our own check when the finger is lifted
                val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = (if (dragging) Modifier else Modifier.animateItem())
                        .onSizeChanged { rowWidthPx[0] = it.width.toFloat() }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.any { it.pressed }
                                    if (touching[0] && !down) {
                                        // Finger lifted: remove the track only if the row was pulled past the removal point
                                        val off = runCatching { kotlin.math.abs(dismissState.requireOffset()) }.getOrDefault(0f)
                                        if (rowWidthPx[0] > 0f && off >= rowWidthPx[0] * SwipeRemoveFraction) {
                                            val i = rows.indexOfFirst { it.uid == row.uid }
                                            if (i >= 0) {
                                                rows.removeAt(i)
                                                audio.removeAt(i)
                                            }
                                        }
                                    }
                                    touching[0] = down
                                }
                            }
                        }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationY = dragShift
                                shadowElevation = 12f
                            }
                        },
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val off = runCatching { kotlin.math.abs(dismissState.requireOffset()) }.getOrDefault(0f)
                                    val removalPx = (rowWidthPx[0] * SwipeRemoveFraction).coerceAtLeast(1f)
                                    val ramp = (off / removalPx).coerceIn(0f, 1f)
                                    val soft = androidx.compose.ui.graphics.lerp(
                                        scheme.surfaceVariant, scheme.errorContainer, (ramp / 0.7f).coerceIn(0f, 1f),
                                    )
                                    drawRect(
                                        androidx.compose.ui.graphics.lerp(
                                            soft, androidx.compose.ui.graphics.Color(0xFFE53935), ((ramp - 0.7f) / 0.3f).coerceIn(0f, 1f),
                                        )
                                    )
                                }
                                .padding(horizontal = 24.dp),
                            contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            },
                        ) {
                            val crossColor by remember {
                                derivedStateOf {
                                    val off = runCatching { kotlin.math.abs(dismissState.requireOffset()) }.getOrDefault(0f)
                                    val removalPx = (rowWidthPx[0] * SwipeRemoveFraction).coerceAtLeast(1f)
                                    val k = ((off / removalPx - 0.7f) / 0.3f).coerceIn(0f, 1f)
                                    androidx.compose.ui.graphics.lerp(scheme.onErrorContainer, androidx.compose.ui.graphics.Color.White, k)
                                }
                            }
                            CrossIcon(Modifier.size(20.dp), crossColor)
                        }
                    },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .background(
                                if (current) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable {
                                val i = rows.indexOfFirst { it.uid == row.uid }
                                if (i >= 0) audio.playAt(i)
                            }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                            CoverImage(item.artworkUrl, Modifier.fillMaxSize())
                            ServiceBadge(item.provider?.toService(), Modifier.align(Alignment.BottomEnd).padding(3.dp), 18.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (item.provider == ProviderId.DEEZER) S.s30SPreview(item.artist) else item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (downloads != null && item.provider == ProviderId.JAMENDO) {
                            item.toTrack()?.let { DownloadButton(it, downloads) }
                        }
                        if (item.provider == ProviderId.JAMENDO || item.provider == ProviderId.AUDIUS) {
                            IconButton(onClick = { if (signedIn) addTarget = item.toTrack() else onOpenAuth() }) {
                                Text(
                                    "+",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // Значок перетаскивания: тянуть можно за любую часть строки после долгого нажатия
                        Text(
                            "≡",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Последняя сохранённая очередь: показывается, пока живая очередь пуста. Сама не запускается. */
@Composable
private fun ColumnScope.SavedQueueSection(
    saved: SavedQueue,
    restoring: Boolean,
    failed: Boolean,
    onRestore: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.lastQueue(saved.items.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Button(onClick = { onRestore(saved.index) }, enabled = !restoring) {
            Text(if (restoring) S.restoring else S.restore)
        }
    }
    if (failed) {
        Text(
            S.couldNotRestoreTheQueue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(saved.items) { i, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .clickable(enabled = !restoring) { onRestore(i) }
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                    CoverImage(entry.artwork, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        entry.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun QueueItem.toTrack(): Track? =
    provider?.let { Track(mediaId, it, title, artist, artworkUrl, durationMs) }
