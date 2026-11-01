package com.unknowstorm.musicapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.unknowstorm.musicapp.provider.youtube.YouTubeVideo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Минимум по правилам YouTube (Required Minimum Functionality): окно плеера не меньше 200x200
private val MINI_SIZE = 200.dp
private val STRIP_HEIGHT = 36.dp
private val EDGE = 8.dp
private val MINI_CORNER = 12.dp // подберите под скругление мини-плеера аудио
private val HEADER_ESTIMATE = 56.dp // высота шапки экрана YouTube, пока реальное место плеера неизвестно
private val FLICK_VELOCITY = 400.dp // скорость рывка в dp/с (раньше 1200: приходилось дёргать слишком резко)
private val FULL_SIDE_MARGIN = 24.dp // поля большого экрана
private val FULL_VIDEO_TOP = 72.dp // отступ видео от верха (под статус-баром)
private val MINI_BOTTOM_RESERVE = 80.dp // отступ мини-окна от низа: над нижней панелью навигации
private val FULL_HANDLE_AREA = 28.dp
private val FULL_INFO_HEIGHT = 120.dp // название + канал (+ ошибка), фиксированная высота
private val FULL_LINKS_HEIGHT = 56.dp // ряд ссылок под информацией
private val FULL_TIMELINE_HEIGHT = 64.dp // таймлайн + время
private val FULL_CONTROLS_HEIGHT = 126.dp // 16 сверху + кнопка 86 + 24 снизу
private val FULL_BLOCK_GAP = 20.dp
private val YT_TRAVEL_CAP = 200.dp // путь пальца, за который окно полностью разворачивается (было почти пол-экрана)
private val RAIL_SIZE = 112.dp // боковая (или нижняя) панель кнопок в полноэкранном режиме

/**
 * Текущее видео YouTube на уровне всего приложения.
 *
 * progress: 0 = мини-окно, 1 = видео развёрнуто на экране YouTube. Им управляет палец (dragBy),
 * а после отпускания его довершает пружина (settle), как у ExpandablePlayer.
 * docked: куда движемся (true = развернуть). queue: список для кнопок «предыдущее/следующее».
 */
class YouTubeSession(private val scope: CoroutineScope) {
    var video by mutableStateOf<YouTubeVideo?>(null)
        private set
    var queue by mutableStateOf<List<YouTubeVideo>>(emptyList())
        private set
    var docked by mutableStateOf(false)
    /** Последнее известное место большого плеера в координатах окна (его сообщает экран YouTube). */
    var dockBounds by mutableStateOf<Rect?>(null)
    /** Область экрана YouTube в координатах окна: до неё растёт контейнер. Её сообщает YouTubeHome. */
    var screenBounds by mutableStateOf<Rect?>(null)
    var errorCode by mutableStateOf<Int?>(null)
    /** Полноэкранный режим: видео на всё окно, кнопки рядом с ним (не поверх). */
    var fullscreen by mutableStateOf(false)
    /** Состояние плеера по IFrame API: 1 играет, 2 пауза, 3 буферизация, 0 конец, -1 нет данных. */
    var playerState by mutableStateOf(-1)
    /** Позиция и длительность текущего видео в секундах (0 у прямых трансляций). */
    var positionSec by mutableStateOf(0.0)
    var durationSec by mutableStateOf(0.0)

    var progress by mutableFloatStateOf(0f)
        private set

    /** Путь пальца, за который progress проходит от 0 до 1. Обновляет оверлей. */
    var travelPx = 800f
    var flickPx = 2500f

    private var dragging = false
    private var settleTarget = -1f
    private var animJob: Job? = null

    private fun currentIndex(): Int = queue.indexOfFirst { it.id == video?.id }

    val hasPrev: Boolean get() = currentIndex() > 0
    val hasNext: Boolean get() = currentIndex().let { it >= 0 && it < queue.size - 1 }

    /** Видео из списка всегда стартует в мини-окне. То же видео, уже играющее в окне, разворачивается. */
    fun open(v: YouTubeVideo, list: List<YouTubeVideo> = listOf(v)) {
        val same = video?.id == v.id
        if (!same) errorCode = null
        queue = if (list.any { it.id == v.id }) list else listOf(v)
        video = v
        docked = same
    }

    /** Только по тапу пользователя. Автоматического перехода в конце ролика нет (правила YouTube). */
    fun next() {
        val i = currentIndex()
        if (i >= 0 && i < queue.size - 1) {
            video = queue[i + 1]
            errorCode = null
        }
    }

    fun previous() {
        val i = currentIndex()
        if (i > 0) {
            video = queue[i - 1]
            errorCode = null
        }
    }

    fun minimize() {
        fullscreen = false
        if (video != null) docked = false
    }

    fun close() {
        animJob?.cancel()
        settleTarget = -1f
        dragging = false
        video = null
        queue = emptyList()
        docked = false
        fullscreen = false
        progress = 0f
        errorCode = null
    }

    private fun settle(target: Float, velocity: Float = 0f) {
        settleTarget = target
        animJob?.cancel()
        animJob = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { v, _ -> progress = v.coerceIn(0f, 1f) }
            settleTarget = -1f
        }
    }

    /** Смена docked без жеста (кнопки, «назад», уход с экрана): просто едем к цели. */
    fun syncTo(expanded: Boolean) {
        val target = if (expanded) 1f else 0f
        if (dragging || video == null) return
        if (settleTarget != target && progress != target) settle(target)
    }

    fun beginDrag() {
        dragging = true
        animJob?.cancel()
        settleTarget = -1f
    }

    /** dy > 0: палец вниз (сворачиваем), dy < 0: вверх (разворачиваем). */
    fun dragBy(dy: Float) {
        progress = (progress - dy / travelPx).coerceIn(0f, 1f)
    }

    /** Возвращает true, если решено развернуть. Дальше пружина довершает движение. */
    fun endDrag(velocityY: Float): Boolean {
        dragging = false
        if (video == null) return false
        val expand = when {
            velocityY < -flickPx -> true
            velocityY > flickPx -> false
            else -> progress > 0.35f
        }
        docked = expand
        settle(if (expand) 1f else 0f, -velocityY / travelPx)
        return expand
    }
}

/**
 * Единственный плеер YouTube в приложении. Он всегда рисуется здесь, в одном и том же месте
 * дерева, поэтому при сворачивании видео не перезапускается.
 *
 * Как у ExpandablePlayer: один контейнер растёт от мини-окна до экрана YouTube (progress 0..1),
 * цвет и скругления перетекают, видео едет внутри него, а название, таймлайн и кнопки открываются
 * внутри контейнера. Размер и положение считаются в layout-фазе, без рекомпозиции на каждый кадр.
 */
@Composable
fun YouTubeOverlay(
    session: YouTubeSession,
    containerOrigin: Offset,
    containerSize: IntSize,
    onExpand: () -> Unit,
) {
    val video = session.video ?: return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val openLink = rememberExternalLinkOpener()

    LaunchedEffect(session.docked) { session.syncTo(session.docked) }
    // Полный экран: прячем системные панели и (на Android) поворачиваем в альбом. «Назад» сначала выходит из него.
    FullscreenWindowEffect(session.fullscreen)
    PlatformBackHandler(enabled = session.fullscreen, onBack = { session.fullscreen = false })
    DisposableEffect(session) {
        YouTubeControl.onState = {
            session.playerState = it
            if (it == -1) {
                session.positionSec = 0.0
                session.durationSec = 0.0
            }
        }
        YouTubeControl.onTime = { pos, dur ->
            session.positionSec = if (pos.isFinite()) pos else 0.0
            session.durationSec = if (dur.isFinite()) dur else 0.0
        }
        onDispose {
            YouTubeControl.onState = null
            YouTubeControl.onTime = null
        }
    }
    val playing = session.playerState == 1 || session.playerState == 3

    val miniPx = with(density) { MINI_SIZE.toPx() }
    val stripPx = with(density) { STRIP_HEIGHT.toPx() }
    val edgePx = with(density) { EDGE.toPx() }
    val cornerPx = with(density) { MINI_CORNER.toPx() }
    val shadowPx = with(density) { 8.dp.toPx() }
    val reservePx = with(density) { MINI_BOTTOM_RESERVE.toPx() }
    val sidePx = with(density) { FULL_SIDE_MARGIN.toPx() }
    val handlePx = with(density) { FULL_HANDLE_AREA.toPx() }
    val gapPx = with(density) { FULL_BLOCK_GAP.toPx() }
    val infoPx = with(density) { FULL_INFO_HEIGHT.toPx() }
    val linksPx = with(density) { FULL_LINKS_HEIGHT.toPx() }
    val timelinePx = with(density) { FULL_TIMELINE_HEIGHT.toPx() }
    val controlsPx = with(density) { FULL_CONTROLS_HEIGHT.toPx() }
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val layoutDirection = LocalLayoutDirection.current
    val sideInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val leftInsetPx = sideInsets.getLeft(density, layoutDirection).toFloat()
    val rightInsetPx = sideInsets.getRight(density, layoutDirection).toFloat()
    val bottomInsetPx = sideInsets.getBottom(density).toFloat()
    val fullscreen = session.fullscreen
    val railPx = with(density) { RAIL_SIZE.toPx() }
    val roomPx = with(density) { 8.dp.toPx() }
    val travelCapPx = with(density) { YT_TRAVEL_CAP.toPx() }

    // Куда растёт контейнер: область экрана YouTube (её сообщает YouTubeHome), а пока её не знаем, весь контейнер.
    // В полноэкранном режиме контейнер занимает всё окно, в том числе поверх нижней панели.
    val rootRect = Rect(0f, 0f, containerSize.width.toFloat(), containerSize.height.toFloat())
    // Нижняя панель при большом плеере прячется: контейнер растёт до низа окна.
    val full: Rect = if (fullscreen) rootRect else (
        session.screenBounds?.translate(-containerOrigin)
            ?.let { Rect(it.left, it.top, it.right, rootRect.bottom) } ?: rootRect
        )
    val topPadPx = (topInset - full.top).coerceAtLeast(0f)
    val landscape = full.width >= full.height

    // Место видео внутри большого контейнера (в его координатах)
    val lay: YtLayout = when {
        // Полный экран в альбоме: видео по центру, справа панель кнопок
        fullscreen && landscape -> {
            val availW = (full.width - leftInsetPx - rightInsetPx - railPx).coerceAtLeast(1f)
            val availH = full.height
            val w = min(availW, availH * 16f / 9f)
            YtLayout(leftInsetPx + (availW - w) / 2f, (availH - w * 9f / 16f) / 2f, w, w * 9f / 16f)
        }
        // Полный экран в портрете (например, окно на ПК): видео сверху, панель кнопок снизу
        fullscreen -> {
            val availH = (full.height - railPx - topPadPx - bottomInsetPx).coerceAtLeast(1f)
            val w = min(full.width, availH * 16f / 9f)
            YtLayout((full.width - w) / 2f, topPadPx + (availH - w * 9f / 16f) / 2f, w, w * 9f / 16f)
        }
        // Альбом: видео слева, название, таймлайн и кнопки справа
        landscape -> {
            val areaTop = topPadPx + roomPx
            val areaH = (full.height - areaTop - roomPx * 2f - bottomInsetPx).coerceAtLeast(1f)
            val maxW = (full.width - leftInsetPx - rightInsetPx - sidePx * 3f) * 0.55f
            val h = min(areaH, maxW * 9f / 16f).coerceAtLeast(1f)
            val w = h * 16f / 9f
            val left = leftInsetPx + sidePx
            YtLayout(
                left, areaTop + (areaH - h) / 2f, w, h,
                panelLeft = left + w + sidePx,
                panelRight = full.width - rightInsetPx - sidePx,
            )
        }
        // Портрет: блок «видео + информация + ссылки» по центру свободной области между ручкой и таймлайном
        else -> {
            val w = (full.width - sidePx * 2f).coerceAtLeast(1f)
            val h = w * 9f / 16f
            val areaTop = topPadPx + handlePx
            val groupH = h + gapPx + infoPx + linksPx
            val freeH = full.height - areaTop - timelinePx - controlsPx - groupH - bottomInsetPx
            YtLayout(sidePx, areaTop + (freeH / 2f).coerceAtLeast(0f), w, h)
        }
    }
    val dockW = lay.w
    val dockH = lay.h
    val dockLeft = lay.left
    val dockTop = lay.top

    val minX = edgePx
    val minY = topInset + edgePx
    val maxX = (containerSize.width - miniPx - edgePx).coerceAtLeast(minX)
    val maxY = (containerSize.height - miniPx - stripPx - edgePx).coerceAtLeast(minY)
    fun clamp(o: Offset) = Offset(o.x.coerceIn(minX, maxX), o.y.coerceIn(minY, maxY))

    var floatPos by remember { mutableStateOf<Offset?>(null) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    val pos = clamp(floatPos ?: Offset(maxX, maxY - reservePx))

    SideEffect {
        // Короткий путь пальца: окно разворачивается, как основной плеер, а не только у самого верха экрана
        session.travelPx = min(abs(full.top - pos.y), travelCapPx).coerceAtLeast(1f)
        session.flickPx = with(density) { FLICK_VELOCITY.toPx() }
    }

    val stripVisible by remember { derivedStateOf { session.progress < 0.999f } }
    val fullVisible by remember { derivedStateOf { session.progress > 0.001f } }

    val miniColor = MaterialTheme.colorScheme.surfaceVariant
    val fullColor = MaterialTheme.colorScheme.surface

    val outer: Modifier = Modifier
        .offset {
            val p = session.progress
            IntOffset(
                lerp(pos.x, full.left, p).roundToInt(),
                lerp(pos.y, full.top, p).roundToInt(),
            )
        }
        .layout { measurable, _ ->
            val p = session.progress
            val w = lerp(miniPx, full.width, p).roundToInt().coerceAtLeast(0)
            val h = lerp(miniPx + stripPx, full.height, p).roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val p = session.progress
            shadowElevation = lerp(shadowPx, 0f, p)
            val r = lerp(cornerPx, 0f, p)
            // нижние углы не скругляем: там край самого плеера
            shape = RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = 0f, bottomStart = 0f)
            clip = true
        }
        // цвет: surfaceVariant -> surface, как у контейнера основного плеера
        .drawBehind { drawRect(lerpColor(miniColor, fullColor, session.progress)) }

    // Жест на плашке: вверх тянет окно к большому видео (за пальцем), в стороны и вниз двигает окно
    val onBegin: (Boolean) -> Unit = { expand ->
        if (expand) {
            session.beginDrag()
        } else {
            snapJob?.cancel()
            if (floatPos == null) floatPos = pos
        }
    }
    val onMove: (Offset, Boolean) -> Unit = { delta, expand ->
        if (expand) {
            session.dragBy(delta.y)
        } else {
            floatPos = clamp((floatPos ?: pos) + delta)
        }
    }
    val onEnd: (Float, Boolean) -> Unit = { velocityY, expand ->
        if (expand) {
            if (session.endDrag(velocityY)) onExpand()
        } else {
            val cur = floatPos ?: pos
            val target = if (cur.x + miniPx / 2f < containerSize.width / 2f) minX else maxX
            snapJob?.cancel()
            snapJob = scope.launch {
                Animatable(cur.x).animateTo(
                    target,
                    spring(stiffness = Spring.StiffnessMediumLow),
                ) {
                    floatPos = Offset(value, (floatPos ?: cur).y)
                }
            }
        }
    }

    Box(outer) {
        // Нижний слой: название, ссылки, таймлайн и кнопки большого экрана. Раскрываются вместе с контейнером.
        if (fullVisible) {
            YouTubeFullContent(
                session = session,
                video = video,
                openLink = openLink,
                width = with(density) { full.width.toDp() },
                height = with(density) { full.height.toDp() },
                topPad = with(density) { topPadPx.toDp() },
                belowVideoStart = with(density) { (dockTop + dockH + gapPx).toDp() },
                landscape = landscape,
                fullscreen = fullscreen,
                panelLeft = with(density) { lay.panelLeft.toDp() },
                panelRight = with(density) { (full.width - lay.panelRight).coerceAtLeast(0f).toDp() },
                rightInset = with(density) { rightInsetPx.toDp() },
                bottomInset = with(density) { bottomInsetPx.toDp() },
            )
        }

        if (stripVisible) {
            // Плашка схлопывается по высоте и гаснет по мере разворачивания
            Box(
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val h = (placeable.height * (1f - session.progress)).roundToInt()
                        layout(placeable.width, h) { placeable.place(0, 0) }
                    }
                    .clipToBounds()
                    .graphicsLayer { alpha = (1f - session.progress / 0.4f).coerceIn(0f, 1f) },
            ) {
                MiniStrip(
                    playing = playing,
                    onPlayPause = { YouTubeControl.send?.invoke(if (playing) "pause" else "play") },
                    onBegin = onBegin,
                    onMove = onMove,
                    onEnd = onEnd,
                    onLogo = { openLink("https://www.youtube.com/watch?v=${video.id}") },
                    onExpand = onExpand,
                    onClose = { session.close() },
                )
            }
        }

        // Видео: едет внутри контейнера из мини-окна на своё место в большом экране. Ничем не перекрыто.
        Box(
            Modifier
                .offset {
                    val p = session.progress
                    IntOffset(
                        lerp(0f, dockLeft, p).roundToInt(),
                        lerp(stripPx, dockTop, p).roundToInt(),
                    )
                }
                .layout { measurable, _ ->
                    val p = session.progress
                    val w = lerp(miniPx, dockW, p).roundToInt().coerceAtLeast(1)
                    val h = lerp(miniPx, dockH, p).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .clipToBounds()
                .background(Color.Black),
        ) {
            // Пока окно в движении, плеер держит большой размер и только масштабируется:
            // перестраивать страницу плеера на каждом кадре дорого
            YouTubePlayerView(
                videoId = video.id,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val moving = session.progress in 0.002f..0.998f
                        val placeable = if (moving) {
                            measurable.measure(
                                Constraints.fixed(
                                    dockW.roundToInt().coerceAtLeast(1),
                                    dockH.roundToInt().coerceAtLeast(1),
                                )
                            )
                        } else {
                            measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
                        }
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        if (session.progress in 0.002f..0.998f && dockW > 0f) {
                            val k = size.width / dockW
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationY = (size.height - dockH * k) / 2f
                        }
                    },
                onError = { session.errorCode = it },
            )
        }
    }
}

/** Положение видео (и, в альбоме, правой панели) внутри большого контейнера, в пикселях. */
private class YtLayout(
    val left: Float,
    val top: Float,
    val w: Float,
    val h: Float,
    val panelLeft: Float = 0f,
    val panelRight: Float = 0f,
)

/**
 * Содержимое большого экрана (FULL-CONTENT-V3). Три раскладки:
 *  - портрет: видео сверху, под ним название, ссылки, пояснение, таймлайн и кнопки;
 *  - альбом: видео слева, справа название, ссылки, таймлайн и кнопки;
 *  - полный экран: видео по центру, кнопки в боковой (или нижней) панели рядом с ним, не поверх.
 * Рисуется внутри растущего контейнера в полный размер и обрезается им; видео сюда не входит.
 * Плеер без родной панели (controls=0): таймлайн и play/pause у нас, команды идут через IFrame API.
 */
@Composable
private fun YouTubeFullContent(
    session: YouTubeSession,
    video: YouTubeVideo,
    openLink: (String) -> Unit,
    width: Dp,
    height: Dp,
    topPad: Dp,
    belowVideoStart: Dp,
    landscape: Boolean,
    fullscreen: Boolean,
    panelLeft: Dp,
    panelRight: Dp,
    rightInset: Dp,
    bottomInset: Dp,
) {
    val playing = session.playerState == 1 || session.playerState == 3
    val blocker = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = width, height = height)
                // содержимое проявляется во второй половине разворачивания
                .graphicsLayer { alpha = ((session.progress - 0.3f) / 0.5f).coerceIn(0f, 1f) }
                // не пускаем нажатия сквозь контейнер в ленту под ним
                .clickable(interactionSource = blocker, indication = null) {}
                // Свайп вниз сворачивает видео за пальцем (в полном экране выходим кнопкой). Плеер лежит выше и жестов не получает.
                .then(
                    if (fullscreen) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            val tracker = VelocityTracker()
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    total = 0f
                                    tracker.resetTracking()
                                    session.beginDrag()
                                },
                                onDragEnd = { session.endDrag(tracker.calculateVelocity().y) },
                                onDragCancel = { session.endDrag(0f) },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    total += amount
                                    tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                                    session.dragBy(amount)
                                },
                            )
                        }
                    }
                ),
        ) {
            when {
                fullscreen -> {
                    YtRail(
                        session = session,
                        video = video,
                        playing = playing,
                        vertical = landscape,
                        openLink = openLink,
                        modifier = if (landscape) {
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = rightInset)
                                .width(RAIL_SIZE)
                                .fillMaxHeight()
                                .padding(vertical = 16.dp)
                        } else {
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomInset)
                                .height(RAIL_SIZE)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        },
                    )
                }

                landscape -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(start = panelLeft, end = panelRight, top = topPad + 8.dp, bottom = 8.dp + bottomInset),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        YtTitleBlock(video, session, compact = true)
                        Spacer(Modifier.height(4.dp))
                        YtLinksRow(session, video, openLink, rowHeight = 48.dp, showMinimize = true)
                        YtTimeline(session, playing)
                        YtControls(session, playing, compact = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }

                else -> {
                    // Ручка: показывает, что экран сворачивается, и сворачивает по тапу
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPad)
                            .fillMaxWidth()
                            .height(FULL_HANDLE_AREA)
                            .clickable { session.minimize() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = FULL_SIDE_MARGIN)
                            .padding(bottom = 24.dp + bottomInset),
                    ) {
                        // Здесь сверху лежит видео (его рисует сам оверлей)
                        Spacer(Modifier.height(belowVideoStart))

                        Column(Modifier.fillMaxWidth().height(FULL_INFO_HEIGHT)) {
                            YtTitleBlock(video, session, compact = false)
                        }

                        YtLinksRow(session, video, openLink, rowHeight = FULL_LINKS_HEIGHT, showMinimize = false)

                        // Свободное место: пояснение. Если места мало, оно просто обрезается и ничего не сдвигает.
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Text(
                                "Видео играет, пока оно видно на экране. Если свернуть приложение или выключить экран, " +
                                    "оно встаёт на паузу (правила YouTube). Фоновое воспроизведение работает в " +
                                    "официальных приложениях YouTube и YouTube Music.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        YtTimeline(session, playing)
                        YtControls(session, playing, compact = false, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    }
                }
            }
        }
    }
}

/** Название видео, канал и ошибка плеера (если есть). */
@Composable
private fun YtTitleBlock(video: YouTubeVideo, session: YouTubeSession, compact: Boolean) {
    Text(
        video.title,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        maxLines = if (compact) 1 else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        video.channel,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    session.errorCode?.let {
        Text(playerErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Ссылки: официальные логотипы ведут на это видео. Справа: полный экран, (в альбоме) свернуть, закрыть.
 */
@Composable
private fun YtLinksRow(
    session: YouTubeSession,
    video: YouTubeVideo,
    openLink: (String) -> Unit,
    rowHeight: Dp,
    showMinimize: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeLogo(onClick = { openLink("https://www.youtube.com/watch?v=${video.id}") })
        Spacer(Modifier.width(12.dp))
        YouTubeMusicLink(onClick = { openLink("https://music.youtube.com/watch?v=${video.id}") })
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { session.fullscreen = true }) {
            FullscreenGlyph(exit = false, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showMinimize) {
            IconButton(onClick = { session.minimize() }) {
                ChevronDownIcon(Modifier.size(22.dp))
            }
        }
    }
}

/** Таймлайн: волна и невидимый Slider для перемотки, как у основного плеера. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YtTimeline(session: YouTubeSession, playing: Boolean) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val durationSec = session.durationSec
    val trackProgress = if (durationSec > 0.0) (session.positionSec / durationSec).toFloat().coerceIn(0f, 1f) else 0f
    val shownProgress = dragValue ?: trackProgress
    val shownSec = dragValue?.let { it * durationSec } ?: session.positionSec

    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        LinearWavyProgressIndicator(
            progress = { shownProgress },
            amplitude = { if (playing) 1f else 0f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Slider(
            value = shownProgress,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let {
                    val sec = it * durationSec
                    YouTubeControl.send?.invoke("seek:$sec")
                    session.positionSec = sec
                }
                dragValue = null
            },
            enabled = durationSec > 0.0,
            modifier = Modifier.fillMaxWidth().alpha(0f),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatYtDuration((shownSec * 1000).toLong()), style = MaterialTheme.typography.labelMedium)
        Text(
            if (durationSec > 0.0) formatYtDuration((durationSec * 1000).toLong()) else "",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Предыдущее, play/pause, следующее. compact: меньшие кнопки для альбома. */
@Composable
private fun YtControls(session: YouTubeSession, playing: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val side = if (compact) 52.dp else 60.dp
    val sideIcon = if (compact) 30.dp else 36.dp
    val mid = if (compact) 64.dp else 86.dp
    val midIcon = if (compact) 36.dp else 48.dp
    Row(
        modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { session.previous() }, enabled = session.hasPrev, modifier = Modifier.size(side)) {
            SkipIcon(next = false, modifier = Modifier.size(sideIcon))
        }
        FilledIconButton(
            onClick = { YouTubeControl.send?.invoke(if (playing) "pause" else "play") },
            modifier = Modifier.size(mid),
        ) { PlayPauseIcon(playing, Modifier.size(midIcon)) }
        IconButton(onClick = { session.next() }, enabled = session.hasNext, modifier = Modifier.size(side)) {
            SkipIcon(next = true, modifier = Modifier.size(sideIcon))
        }
    }
}

/**
 * Панель кнопок полноэкранного режима. Стоит рядом с видео, а не на нём (правила YouTube запрещают
 * что-либо поверх плеера). Логотип YouTube остаётся и ведёт на это видео.
 */
@Composable
private fun YtRail(
    session: YouTubeSession,
    video: YouTubeVideo,
    playing: Boolean,
    vertical: Boolean,
    openLink: (String) -> Unit,
    modifier: Modifier,
) {
    val items: @Composable () -> Unit = {
        YouTubeLogo(onClick = { openLink("https://www.youtube.com/watch?v=${video.id}") })
        IconButton(onClick = { session.previous() }, enabled = session.hasPrev, modifier = Modifier.size(48.dp)) {
            SkipIcon(next = false, modifier = Modifier.size(28.dp))
        }
        FilledIconButton(
            onClick = { YouTubeControl.send?.invoke(if (playing) "pause" else "play") },
            modifier = Modifier.size(60.dp),
        ) { PlayPauseIcon(playing, Modifier.size(34.dp)) }
        IconButton(onClick = { session.next() }, enabled = session.hasNext, modifier = Modifier.size(48.dp)) {
            SkipIcon(next = true, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = { session.fullscreen = false }, modifier = Modifier.size(48.dp)) {
            FullscreenGlyph(exit = true, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) { items() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { items() }
    }
}

/** Значок «во весь экран» (четыре уголка наружу) и «выйти» (уголки внутрь). */
@Composable
private fun FullscreenGlyph(exit: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        fun corner(x: Float, y: Float, dx: Float, dy: Float, arm: Float) {
            drawLine(tint, Offset(x, y), Offset(x + dx * arm, y), strokeWidth = stroke)
            drawLine(tint, Offset(x, y), Offset(x, y + dy * arm), strokeWidth = stroke)
        }
        if (!exit) {
            val p = w * 0.1f
            val arm = w * 0.3f
            corner(p, p, 1f, 1f, arm)
            corner(w - p, p, -1f, 1f, arm)
            corner(p, h - p, 1f, -1f, arm)
            corner(w - p, h - p, -1f, -1f, arm)
        } else {
            val a = w * 0.36f
            val arm = w * 0.26f
            corner(a, a, -1f, -1f, arm)
            corner(w - a, a, 1f, -1f, arm)
            corner(a, h - a, -1f, 1f, arm)
            corner(w - a, h - a, 1f, 1f, arm)
        }
    }
}

/**
 * Жест на нашей плашке (на плеер жесты не вешаем: правило Mouseovers).
 * fixedMode: 1 = окно едет за пальцем в любую сторону, 2 = палец вверх тянет окно к большому видео.
 */
@Composable
private fun Modifier.dragHandle(
    fixedMode: Int,
    onBegin: (expand: Boolean) -> Unit,
    onMove: (delta: Offset, expand: Boolean) -> Unit,
    onEnd: (velocityY: Float, expand: Boolean) -> Unit,
): Modifier {
    val begin by rememberUpdatedState(onBegin)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(fixedMode) {
        val tracker = VelocityTracker()
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = {
                tracker.resetTracking()
                total = Offset.Zero
                begin(fixedMode == 2)
            },
            onDragEnd = { end(tracker.calculateVelocity().y, fixedMode == 2) },
            onDragCancel = { end(0f, fixedMode == 2) },
            onDrag = { change, amount ->
                change.consume()
                total += amount
                tracker.addPosition(change.uptimeMillis, total)
                move(amount, fixedMode == 2)
            },
        )
    }
}

/**
 * Полоса над видео: логотип YouTube (обязателен по правилам бренда), play/pause, развернуть, закрыть.
 * Окно двигают за логотип; тап или свайп вверх по остальной плашке разворачивает его.
 * Плеер без родной панели, поэтому пауза на нашей плашке, а не поверх видео.
 */
@Composable
private fun MiniStrip(
    playing: Boolean,
    onPlayPause: () -> Unit,
    onBegin: (Boolean) -> Unit,
    onMove: (Offset, Boolean) -> Unit,
    onEnd: (Float, Boolean) -> Unit,
    onLogo: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .dragHandle(2, onBegin, onMove, onEnd)
            .padding(start = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YouTubeLogo(modifier = Modifier.dragHandle(1, onBegin, onMove, onEnd), onClick = onLogo)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(32.dp).clickable(onClick = onPlayPause), contentAlignment = Alignment.Center) {
            PlayPauseIcon(playing, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(32.dp).clickable(onClick = onExpand), contentAlignment = Alignment.Center) {
            ChevronDownIcon(Modifier.size(20.dp).rotate(180f))
        }
        Box(Modifier.size(32.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            CloseIcon(Modifier.size(20.dp))
        }
    }
}

/** Значок «предыдущее/следующее»: треугольник с чёрточкой. */
@Composable
internal fun YtSkipGlyph(
    next: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bar = w * 0.16f
        val tri = Path().apply {
            if (next) {
                moveTo(0f, 0f)
                lineTo(w - bar * 1.5f, h / 2f)
                lineTo(0f, h)
            } else {
                moveTo(w, 0f)
                lineTo(bar * 1.5f, h / 2f)
                lineTo(w, h)
            }
            close()
        }
        drawPath(tri, tint)
        drawRect(tint, topLeft = Offset(if (next) w - bar else 0f, 0f), size = Size(bar, h))
    }
}

// --- YT-MUSIC-LINK-BEGIN ---
/** Пока нет официального логотипа (drawable/youtube_music_logo): текстовая кнопка. */
@Composable
private fun YouTubeMusicLink(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("YouTube Music") }
}
// --- YT-MUSIC-LINK-END ---
