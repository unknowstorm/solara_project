package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.player.AudioBackend
import com.unknowstorm.musicapp.resources.Res
import com.unknowstorm.musicapp.resources.spotify_like
import com.unknowstorm.musicapp.resources.spotify_liked
import com.unknowstorm.musicapp.spotify.SpotifyRemote
import com.unknowstorm.musicapp.spotify.SpotifyState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpMiniInset = 8.dp
private val SpMiniCoverSize = 48.dp
private val SpMiniCorner = 16.dp
/** Правила Spotify: углы обложки 4 dp, без изменений формы. */
private val SpCoverCorner = 4.dp
private val SpBelowCoverBlock = 340.dp
private val SpScreenSideMargin = 24.dp
private val SpCoverDownShift = 40.dp

/** Общее состояние мини-плеера Spotify. Живёт только в памяти. */
@Stable
class SpotifyMiniState {
    /** Мини-плеер сейчас должен быть виден (трек есть, наш плеер не играет). */
    var shown by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    /** Трек, который пользователь закрыл крестиком или свайпом. */
    var dismissedUri by mutableStateOf<String?>(null)
    val anchor = MiniPlayerAnchor()
}

val LocalSpotifyMini = staticCompositionLocalOf { SpotifyMiniState() }

/** Пустое место в MainScreen, где стоит мини-плеер Spotify (рисует его SpotifyExpandablePlayer). */
@Composable
fun SpotifyMiniSlot(hidden: Boolean = false) {
    val ui = LocalSpotifyMini.current
    if (!ui.shown || hidden) return
    Spacer(
        Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(MiniHeight)
            .onGloballyPositioned { ui.anchor.bounds = it.boundsInRoot() }
    )
}

/**
 * Мини-плеер и полный плеер Spotify: тот же морфинг, что у ExpandablePlayer,
 * но отдельный компонент: ничего общего с AudioBackend и нашими источниками.
 * Ставить в корневой Box поверх основного контента.
 */
@Composable
fun SpotifyExpandablePlayer(remote: SpotifyRemote, audio: AudioBackend, modifier: Modifier = Modifier) {
    val ui = LocalSpotifyMini.current
    val anchor = ui.anchor
    val ownAnchor = LocalMiniPlayerAnchor.current
    val stateHolder = remote.state.collectAsState()
    val audioHolder = audio.state.collectAsState()
    // Сырое условие. Spotify при смене трека может на секунду прислать «пустое» состояние
    val rawHasTrack by remember {
        derivedStateOf {
            val st = stateHolder.value
            st.connected && st.premium != false && st.title != null &&
                audioHolder.value.title == null &&
                !(ui.dismissedUri != null && ui.dismissedUri == st.uri && st.isPaused)
        }
    }
    val audioBusy by remember { derivedStateOf { audioHolder.value.title != null } }
    // Последнее состояние с названием трека: его показываем, пока длится короткий разрыв
    val held = remember { mutableStateOf(stateHolder.value) }
    LaunchedEffect(Unit) {
        snapshotFlow { stateHolder.value }.collect { if (it.title != null) held.value = it }
    }
    // Spotify на миг присылает пустое состояние при смене трека: держим плеер до 1,2 с, но только в неявных случаях
    var everShown by remember { mutableStateOf(false) }
    var graceExpired by remember { mutableStateOf(false) }
    val explicitHide by remember {
        derivedStateOf {
            val st = stateHolder.value
            // Наш плеер должен сначала занять своё место, и только потом Spotify скрывается: без разрыва между ними
            (audioBusy && ownAnchor.bounds != null) || !st.connected || st.premium == false ||
                (ui.dismissedUri != null && ui.dismissedUri == st.uri && st.isPaused)
        }
    }
    LaunchedEffect(rawHasTrack) {
        if (rawHasTrack) {
            everShown = true
            graceExpired = false
        } else {
            delay(1200)
            graceExpired = true
        }
    }
    // Считаем прямо в композиции: плеер скрывается и появляется в том же кадре, что и смена источника
    val hasTrack = rawHasTrack || (everShown && !graceExpired && !explicitHide)
    val expanded = ui.expanded
    val scope = rememberCoroutineScope()

    SideEffect { ui.shown = hasTrack }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(rawHasTrack, audioHolder.value.title != null, stateHolder.value.title) }.collect {
            println("Solara: timeline spotifyMini=${it.first} audioTitle=${it.second} spTitle=${it.third} spPaused=${stateHolder.value.isPaused} audioPlaying=${audioHolder.value.isPlaying}")
        }
    }
    DisposableEffect(Unit) { onDispose { ui.shown = false } }
    // Снова нажали play: закрытый ранее трек опять показываем
    LaunchedEffect(Unit) {
        snapshotFlow { stateHolder.value.isPaused }.collect { paused -> if (!paused) ui.dismissedUri = null }
    }

    val progress = remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val settling = remember { floatArrayOf(-1f) }

    fun settle(target: Float, velocity: Float = 0f) {
        settling[0] = target
        animJob?.cancel()
        animJob = scope.launch {
            animate(
                initialValue = progress.floatValue,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { v, _ -> progress.floatValue = v.coerceIn(0f, 1f) }
            settling[0] = -1f
        }
    }

    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        if (settling[0] != target && progress.floatValue != target) settle(target)
    }

    LaunchedEffect(hasTrack) {
        if (!hasTrack) {
            animJob?.cancel()
            settling[0] = -1f
            progress.floatValue = 0f
            ui.expanded = false
        }
    }
    if (!hasTrack) return

    PlatformBackHandler(enabled = expanded, onBack = { ui.expanded = false })

    var origin by remember { mutableStateOf(Offset.Zero) }
    var miniOffsetX by remember { mutableFloatStateOf(0f) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    fun animateMiniX(to: Float, after: () -> Unit = {}) {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            animate(initialValue = miniOffsetX, targetValue = to) { v, _ -> miniOffsetX = v }
            after()
        }
    }

    fun dismissMini() {
        ui.dismissedUri = stateHolder.value.uri
        remote.pause()
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        val density = LocalDensity.current
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val fullWidthDp = maxWidth
        val fullHeightDp = maxHeight

        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val fbMargin = with(density) { 8.dp.toPx() }
        val fbBottom = with(density) { (bottomInset + 8.dp).toPx() }
        val miniHpx = with(density) { MiniHeight.toPx() }
        fun miniRect(): Rect {
            val b = anchor.bounds
            return if (b != null) {
                Rect(b.left - origin.x, b.top - origin.y, b.right - origin.x, b.bottom - origin.y)
            } else {
                Rect(fbMargin, fullH - fbBottom - miniHpx, fullW - fbMargin, fullH - fbBottom)
            }
        }
        val miniNow = miniRect()
        val miniWidthDp = with(density) { miniNow.width.toDp() }

        val layoutDirection = LocalLayoutDirection.current
        val sideInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val leftInset = with(density) { sideInsets.getLeft(density, layoutDirection).toDp() }
        val rightInset = with(density) { sideInsets.getRight(density, layoutDirection).toDp() }
        val landscape = fullW > fullH

        val coverTop: Dp
        val coverSize: Dp
        val coverLeft: Dp
        if (landscape) {
            val availH = maxHeight - topInset - bottomInset
            coverSize = maxOf(120.dp, minOf(availH - 24.dp, maxWidth * 0.42f))
            coverTop = topInset + (availH - coverSize) / 2f
            coverLeft = leftInset + 32.dp
        } else {
            coverTop = topInset + 32.dp + SpCoverDownShift
            coverSize = maxOf(160.dp, minOf(maxWidth - SpScreenSideMargin * 2f, maxHeight - coverTop - bottomInset - SpBelowCoverBlock))
            coverLeft = (maxWidth - coverSize) / 2f
        }
        val coverTopPx = with(density) { coverTop.toPx() }
        val bigX = with(density) { coverLeft.toPx() }
        val belowCoverStart = coverTop + coverSize + 20.dp
        val panelStart = coverLeft + coverSize + 32.dp

        val miniCornerPx = with(density) { SpMiniCorner.toPx() }
        val shadowPx = with(density) { 3.dp.toPx() }
        val miniColor = MaterialTheme.colorScheme.secondaryContainer
        val fullColor = MaterialTheme.colorScheme.surface

        // Запоминаем в момент расстановки, стоит ли плеер на измеренном месте: до этого он невидим
        val placedOnAnchor = remember { mutableStateOf(false) }
        val miniVisible by remember { derivedStateOf { progress.floatValue < 0.999f } }
        val fullVisible by remember { derivedStateOf { progress.floatValue > 0.001f } }

        val dragState = rememberDraggableState { delta ->
            val travel = miniRect().top.coerceAtLeast(1f)
            progress.floatValue = (progress.floatValue - delta / travel).coerceIn(0f, 1f)
        }

        Box(
            modifier = Modifier
                .offset {
                    val m = miniRect()
                    val p = progress.floatValue
                    IntOffset(lerp(m.left, 0f, p).roundToInt(), lerp(m.top, 0f, p).roundToInt())
                }
                .layout { measurable, _ ->
                    placedOnAnchor.value = anchor.bounds != null
                    val m = miniRect()
                    val p = progress.floatValue
                    val w = lerp(m.width, fullW, p).roundToInt().coerceAtLeast(0)
                    val h = lerp(m.height, fullH, p).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val p = progress.floatValue
                    translationX = miniOffsetX
                    // Пока место под мини-плеер не измерено, не рисуем его: иначе он мелькает поверх нижней панели
                    alpha = if (!placedOnAnchor.value && p < 0.01f) 0f else 1f
                    shape = RoundedCornerShape(CornerSize(lerp(miniCornerPx, 0f, p)))
                    clip = true
                    shadowElevation = lerp(shadowPx, 0f, p)
                }
                .drawBehind { drawRect(lerpColor(miniColor, fullColor, progress.floatValue)) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        animJob?.cancel()
                        settling[0] = -1f
                    },
                    onDragStopped = { velocity ->
                        val p = progress.floatValue
                        val travel = miniRect().top.coerceAtLeast(1f)
                        val target = when {
                            velocity > 1500f -> 0f
                            velocity < -1500f -> 1f
                            p < 0.5f -> 0f
                            else -> 1f
                        }
                        settle(target, -velocity / travel)
                        ui.expanded = target == 1f
                    },
                )
                .then(
                    if (expanded) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dismissJob?.cancel() },
                                    onDragEnd = {
                                        val m = miniRect()
                                        if (abs(miniOffsetX) > m.width * 0.35f) {
                                            animateMiniX(if (miniOffsetX > 0f) fullW else -fullW) {
                                                dismissMini()
                                                miniOffsetX = 0f
                                            }
                                        } else {
                                            animateMiniX(0f)
                                        }
                                    },
                                    onDragCancel = { animateMiniX(0f) },
                                    onHorizontalDrag = { _, dx -> miniOffsetX += dx },
                                )
                            }
                            .clickable { ui.expanded = true }
                    }
                )
        ) {
            if (miniVisible) {
                SpMiniContent(remote, held, { progress.floatValue }, miniWidthDp) { dismissMini() }
            }
            if (fullVisible) {
                SpFullContent(
                    remote = remote,
                    stateHolder = held,
                    expandProgress = { progress.floatValue },
                    width = fullWidthDp,
                    height = fullHeightDp,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    belowCoverStart = belowCoverStart,
                    landscape = landscape,
                    panelStart = panelStart,
                    rightInset = rightInset,
                )
            }
            SpSharedCover(
                stateHolder = held,
                progress = { progress.floatValue },
                coverSize = coverSize,
                bigX = bigX,
                bigY = coverTopPx,
            )
        }
    }
}

/** Полный логотип Spotify на чёрной плашке, без изменений. */
@Composable
private fun SpotifyLogoChip(logoHeight: Dp, modifier: Modifier = Modifier) {
    Image(
        spotifyMonoLogo(MaterialTheme.colorScheme.surface),
        contentDescription = "Spotify",
        modifier = modifier.height(logoHeight),
    )
}

/** Иконка Spotify без надписи на чёрной плашке: в мини-плеере не хватает места под полный логотип. */
@Composable
private fun SpotifyIconChip(iconSize: Dp, modifier: Modifier = Modifier) {
    Image(
        spotifyMonoIcon(MaterialTheme.colorScheme.secondaryContainer),
        contentDescription = "Spotify",
        modifier = modifier.size(iconSize),
    )
}

@Composable
private fun SpMiniContent(
    remote: SpotifyRemote,
    stateHolder: State<SpotifyState>,
    progress: () -> Float,
    width: Dp,
    onClose: () -> Unit,
) {
    val s by stateHolder
    val title = s.title ?: return

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = width, height = MiniHeight)
                .graphicsLayer { alpha = (1f - progress() / 0.25f).coerceIn(0f, 1f) }
                .padding(SpMiniInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(SpMiniCoverSize))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                s.artist?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            SpotifyIconChip(24.dp)
            Spacer(Modifier.width(20.dp))
            IconButton(
                onClick = { if (s.isPaused) remote.resume() else remote.pause() },
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            ) {
                PlayPauseIcon(!s.isPaused, Modifier.size(22.dp), MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                CloseIcon(Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Общая обложка: морфинг 48 dp -> большая. Без наклона, блика, значков и блюра, углы 4 dp, без обрезки. */
@Composable
private fun SpSharedCover(
    stateHolder: State<SpotifyState>,
    progress: () -> Float,
    coverSize: Dp,
    bigX: Float,
    bigY: Float,
) {
    val density = LocalDensity.current
    val cover by remember { derivedStateOf { stateHolder.value.cover } }

    val sizePx = with(density) { coverSize.toPx() }
    val miniSizePx = with(density) { SpMiniCoverSize.toPx() }
    val miniXY = with(density) { SpMiniInset.toPx() }
    val cornerPx = with(density) { SpCoverCorner.toPx() }

    Box(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(coverSize)
            .graphicsLayer {
                val p = progress()
                val sc = lerp(miniSizePx / sizePx, 1f, p)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = sc
                scaleY = sc
                translationX = lerp(miniXY, bigX, p)
                translationY = lerp(miniXY, bigY, p)
                // Правила Spotify: обложку не анимируем, поэтому в пути она скрыта и появляется в крайних положениях
                alpha = if (p < 0.02f || p > 0.98f) 1f else 0f
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 4 dp на экране при любом масштабе
                    val sc = lerp(miniSizePx / sizePx, 1f, progress())
                    shape = RoundedCornerShape(CornerSize(cornerPx / sc))
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            cover?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }
}

@Composable
private fun SpFullContent(
    remote: SpotifyRemote,
    stateHolder: State<SpotifyState>,
    expandProgress: () -> Float,
    width: Dp,
    height: Dp,
    topInset: Dp,
    bottomInset: Dp,
    belowCoverStart: Dp,
    landscape: Boolean,
    panelStart: Dp,
    rightInset: Dp,
) {
    val s by stateHolder
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    // Сообщение при лайке по правилам Spotify; пропадает само
    var likeNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(likeNote) { if (likeNote != null) { delay(2000); likeNote = null } }

    val trackProgress = if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
    val shownProgress = dragValue ?: trackProgress
    val shownPositionMs = dragValue?.let { (it * s.durationMs).toLong() } ?: s.positionMs
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = width, height = height)
                .graphicsLayer { alpha = ((expandProgress() - 0.3f) / 0.5f).coerceIn(0f, 1f) }
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
            // Логотип Spotify на экране с его контентом
            // В портрете он стоит над обложкой; в альбоме он первой строкой колонки (см. ниже), чтобы не налезать на текст
            if (!landscape) {
                SpotifyLogoChip(24.dp, Modifier.align(Alignment.TopCenter).padding(top = topInset + 28.dp))
            }

            Column(
                modifier = if (landscape) {
                    Modifier
                        .fillMaxSize()
                        .padding(start = panelStart, end = rightInset + 32.dp, top = topInset + 16.dp, bottom = bottomInset + 8.dp)
                } else {
                    Modifier.fillMaxSize().padding(horizontal = SpScreenSideMargin).padding(bottom = bottomInset)
                },
                verticalArrangement = if (landscape) Arrangement.Center else Arrangement.Top,
            ) {
                if (!landscape) Spacer(Modifier.height(belowCoverStart))
                // Логотип первой строкой колонки: двигается вместе с текстом и не может на него наложиться
                if (landscape) {
                    SpotifyLogoChip(24.dp)
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    s.title ?: "",
                    style = if (landscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    maxLines = if (landscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    s.artist ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                s.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showDetails = true }) { MoreIcon() }
                    // Ссылка ведёт на текущий трек в Spotify
                    SpotifyOpenButton({ s.uri?.let { remote.openInSpotify(it) } ?: remote.openSpotifyApp() })
                    // «+» кладёт трек в Liked Songs самого Spotify, у нас ничего не хранится
                    IconButton(onClick = {
                        likeNote = if (!s.saved) "Added to Liked Songs" else "Removed from Liked Songs"
                        remote.toggleSaved()
                    }) {
                        Image(
                            painterResource(if (s.saved) Res.drawable.spotify_liked else Res.drawable.spotify_like),
                            contentDescription = if (s.saved) "Liked" else "Like",
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (!s.saved && MaterialTheme.colorScheme.surface.luminance() >= 0.179f) Color.Black else Color.Transparent),
                        )
                    }
                }

                // Место под подпись зарезервировано всегда: полоса воспроизведения не прыгает
                Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterEnd) {
                    likeNote?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = active)
                    }
                }
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    LinearWavyProgressIndicator(
                        progress = { shownProgress },
                        amplitude = { if (!s.isPaused) 1f else 0f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                    Slider(
                        value = shownProgress,
                        onValueChange = { dragValue = it },
                        onValueChangeFinished = {
                            dragValue?.let { remote.seekTo((it * s.durationMs).toLong()) }
                            dragValue = null
                        },
                        enabled = s.durationMs > 0,
                        modifier = Modifier.fillMaxWidth().alpha(0f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(spTime(shownPositionMs), style = MaterialTheme.typography.labelMedium)
                    Text(spTime(s.durationMs), style = MaterialTheme.typography.labelMedium)
                }

                if (landscape) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(1f))

                Row(
                    if (landscape) Modifier.fillMaxWidth().padding(top = 8.dp)
                    else Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = remote::previous, modifier = Modifier.size(60.dp)) {
                        SkipIcon(next = false, modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(
                        onClick = { if (s.isPaused) remote.resume() else remote.pause() },
                        modifier = Modifier.size(86.dp),
                    ) { PlayPauseIcon(!s.isPaused, Modifier.size(48.dp)) }
                    IconButton(onClick = remote::next, modifier = Modifier.size(60.dp)) {
                        SkipIcon(next = true, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Закрыть") } },
            title = { Text(s.title ?: "") },
            text = { Text("Исполнитель: ${s.artist ?: "—"}\nИсточник: Spotify") },
        )
    }
}

private fun spTime(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
