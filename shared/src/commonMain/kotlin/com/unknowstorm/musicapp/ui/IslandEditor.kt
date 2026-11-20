package com.unknowstorm.musicapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.unknowstorm.musicapp.i18n.tr
import com.unknowstorm.musicapp.model.Service
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ключ в текстовом хранилище, под которым лежит порядок сервисов в островке. */
const val ISLAND_KEY = "island_v1"

/**
 * Какие сервисы показаны в главном островке (верхней панели) и в каком порядке.
 * Глобальное состояние, как PaletteSettings: панель читает его при каждой перерисовке.
 * Сервисы, которых нет в сохранённой строке (например, добавленные в новой версии), показываются в конце.
 */
object IslandSettings {
    private var savedVisible: List<String>? by mutableStateOf(null)
    private var savedHidden: Set<String> by mutableStateOf(emptySet())

    /** Сервисы, которые можно переставлять и скрывать. Библиотека, Spotify и «+» остаются на своих местах. */
    val editableServices: List<Service>
        get() = Service.entries.filter { it.ready && it != Service.SPOTIFY && it != Service.ALL }

    /** Видимые сервисы в сохранённом порядке. */
    fun visible(): List<Service> {
        val all = editableServices
        val order = savedVisible ?: return all.filter { it.name !in savedHidden }
        val known = order.mapNotNull { name -> all.firstOrNull { it.name == name } }
        val rest = all.filter { it.name !in order && it.name !in savedHidden }
        return known + rest
    }

    /** Вызывается один раз при старте с сохранённой строкой (или null). */
    fun restore(raw: String?) {
        if (raw.isNullOrBlank()) {
            savedVisible = null
            savedHidden = emptySet()
            return
        }
        var visible: List<String>? = null
        var hidden: Set<String> = emptySet()
        raw.split(';').forEach { part ->
            val pieces = part.split('=', limit = 2)
            val names = pieces.getOrElse(1) { "" }.split(',').filter { it.isNotBlank() }
            when (pieces[0]) {
                "v" -> visible = names
                "h" -> hidden = names.toSet()
            }
        }
        savedVisible = visible
        savedHidden = hidden
    }

    /** Применяет новый порядок сразу и сохраняет его через [persist]. */
    fun update(order: List<Service>, persist: (String) -> Unit) {
        val names = order.map { it.name }
        val hidden = editableServices.map { it.name }.filter { it !in names }.toSet()
        savedVisible = names
        savedHidden = hidden
        persist("v=" + names.joinToString(",") + ";h=" + hidden.joinToString(","))
    }
}

/**
 * Шторка редактора островка. Сверху сам островок (пилюля), под ним зона скрытых сервисов.
 * Сервис зажимают и перетягивают: внутри пилюли меняется порядок, из пилюли вниз в зону он скрывается,
 * из зоны вверх на пилюлю добавляется. Кнопок «Скрыть» и «Добавить» нет.
 * Изменения применяются только по «Сохранить».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSheet(
    onSave: (List<Service>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editable = remember { IslandSettings.editableServices }
    var visible by remember { mutableStateOf(IslandSettings.visible()) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                tr("Island", "Острівець"),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            IslandEditorArea(
                editable = editable,
                // Читаем состояние напрямую: во время перетаскивания события приходят чаще, чем перерисовка
                visibleProvider = { visible },
                onVisibleChange = { visible = it },
            )

            Text(
                tr(
                    "Hold a service and drag: reorder it, drag down to hide, drag up to add",
                    "Утримуйте сервіс і перетягуйте: змінюйте порядок, вниз — сховати, вгору — додати",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { onSave(visible); close() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(tr("Save", "Зберегти")) }
        }
    }
}

/**
 * Геометрия области редактора в пикселях. Все сервисы живут в одной системе координат, поэтому
 * карточку можно тащить между пилюлей и зоной скрытых. Положение считается по центрам.
 */
private class IslandGeometry(
    val widthPx: Float,
    val itemPx: Float,
    val gapPx: Float,
    val pillHeightPx: Float,
    val trayTopPx: Float,
    val trayHeightPx: Float,
    val chipMaxPx: Float,
    val chipGapPx: Float,
    val trayPadPx: Float,
) {
    private val stepPx get() = itemPx + gapPx

    val heightPx get() = trayTopPx + trayHeightPx
    val pillCenterY get() = pillHeightPx / 2f
    val trayCenterY get() = trayTopPx + trayHeightPx / 2f

    /** Ниже этой линии перетаскиваемый сервис считается скрытым, выше — лежащим в пилюле. */
    val boundaryY get() = (pillHeightPx + trayTopPx) / 2f

    private fun pillStartX(n: Int) =
        (widthPx - (itemPx * n + gapPx * (n - 1).coerceAtLeast(0))) / 2f

    fun iconCenterX(index: Int, n: Int) = pillStartX(n) + itemPx / 2f + index * stepPx

    /** Номер места в пилюле из [n] иконок, ближайшего к горизонтальной позиции [x]. */
    fun indexAt(x: Float, n: Int): Int =
        ((x - pillStartX(n) - itemPx / 2f) / stepPx).roundToInt().coerceIn(0, (n - 1).coerceAtLeast(0))

    fun chipWidth(n: Int): Float {
        if (n <= 0) return chipMaxPx
        val room = (widthPx - trayPadPx * 2 - chipGapPx * (n - 1)) / n
        return minOf(chipMaxPx, room).coerceAtLeast(0f)
    }

    fun chipCenterX(j: Int, n: Int): Float {
        val w = chipWidth(n)
        val start = (widthPx - (w * n + chipGapPx * (n - 1).coerceAtLeast(0))) / 2f
        return start + w / 2f + j * (w + chipGapPx)
    }
}

/**
 * Островок и зона скрытых сервисов. Сервис в пилюле выглядит иконкой, в зоне скрытых — чипом;
 * при перетаскивании через границу он на лету меняет вид, а остальные плавно съезжают на свободные места.
 * Последний видимый сервис скрыть нельзя: островок не должен остаться пустым.
 */
@Composable
private fun IslandEditorArea(
    editable: List<Service>,
    visibleProvider: () -> List<Service>,
    onVisibleChange: (List<Service>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme

    val itemSize = 48.dp
    val gap = 14.dp
    val pillPad = 20.dp
    val pillHeight = 68.dp
    val trayTop = 88.dp
    val trayHeight = 56.dp
    val chipHeight = 40.dp
    val chipMax = 112.dp
    val chipGap = 8.dp
    val trayPad = 12.dp

    val visible = visibleProvider()
    val hidden = editable.filter { it !in visible }

    var dragging by remember { mutableStateOf<Service?>(null) }
    var dragCenter by remember { mutableStateOf(Offset.Zero) }

    // Те же цвета, что у настоящей панели: чисто белые или чёрные логотипы по правилам брендов
    val darkTheme = cs.background.luminance() < 0.179f
    val idle = if (darkTheme) Color.White else Color.Black
    val onPrimary = if (cs.primary.luminance() > 0.179f) Color.Black else Color.White

    val draggingVisible = dragging?.let { it in visible } == true
    val draggingHidden = dragging?.let { it in hidden } == true

    val pillWidth by animateDpAsState(
        itemSize * visible.size + gap * (visible.size - 1).coerceAtLeast(0) + pillPad * 2,
        spring(0.8f, Spring.StiffnessMediumLow),
        label = "pillWidth",
    )
    val pillColor by animateColorAsState(
        cs.surfaceVariant.copy(alpha = if (draggingVisible) 0.85f else 0.5f),
        label = "pillColor",
    )
    val trayColor by animateColorAsState(
        if (draggingHidden) cs.secondaryContainer.copy(alpha = 0.6f) else cs.surfaceContainerLow,
        label = "trayColor",
    )
    val placeholderAlpha by animateFloatAsState(if (hidden.isEmpty()) 1f else 0f, label = "trayHint")

    BoxWithConstraints(Modifier.fillMaxWidth().height(trayTop + trayHeight)) {
        val geo = with(density) {
            IslandGeometry(
                widthPx = maxWidth.toPx(),
                itemPx = itemSize.toPx(),
                gapPx = gap.toPx(),
                pillHeightPx = pillHeight.toPx(),
                trayTopPx = trayTop.toPx(),
                trayHeightPx = trayHeight.toPx(),
                chipMaxPx = chipMax.toPx(),
                chipGapPx = chipGap.toPx(),
                trayPadPx = trayPad.toPx(),
            )
        }
        val geoNow by rememberUpdatedState(geo)

        // Зона скрытых сервисов: сюда уводят сервис, чтобы убрать его из островка
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = trayTop)
                .fillMaxWidth()
                .height(trayHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(trayColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tr("Drag here to hide", "Перетягніть сюди, щоб сховати"),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.alpha(placeholderAlpha),
            )
        }

        // Пилюля островка
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(pillWidth)
                .height(pillHeight)
                .clip(RoundedCornerShape(32.dp))
                .background(pillColor),
        )

        // Перетаскиваемый сервис переходит между пилюлей и зоной скрытых сразу, как только его центр пересёк границу
        val handleMove: (Service, Offset) -> Unit = { service, center ->
            val g = geoNow
            val list = visibleProvider()
            val index = list.indexOf(service)
            if (center.y < g.boundaryY) {
                if (index >= 0) {
                    val target = g.indexAt(center.x, list.size)
                    if (target != index) {
                        onVisibleChange(list.toMutableList().apply { add(target, removeAt(index)) })
                    }
                } else {
                    val target = g.indexAt(center.x, list.size + 1)
                    onVisibleChange(list.toMutableList().apply { add(target, service) })
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } else if (index >= 0 && list.size > 1) {
                onVisibleChange(list - service)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        val handleMoveNow by rememberUpdatedState(handleMove)

        // Отпустили: сервис плавно садится на своё место, и только потом «перетаскивание» заканчивается
        val release: (Service) -> Unit = { service ->
            scope.launch {
                val g = geoNow
                val list = visibleProvider()
                val index = list.indexOf(service)
                val hiddenList = editable.filter { it !in list }
                val target = if (index >= 0) {
                    Offset(g.iconCenterX(index, list.size), g.pillCenterY)
                } else {
                    Offset(g.chipCenterX(hiddenList.indexOf(service), hiddenList.size), g.trayCenterY)
                }
                val from = dragCenter
                animate(0f, 1f, animationSpec = spring(0.7f, Spring.StiffnessMediumLow)) { p, _ ->
                    dragCenter = Offset(from.x + (target.x - from.x) * p, from.y + (target.y - from.y) * p)
                }
                dragging = null
            }
        }
        val releaseNow by rememberUpdatedState(release)

        editable.forEach { service ->
            key(service) {
                val index = visible.indexOf(service)
                val isOn = index >= 0
                val hiddenIndex = hidden.indexOf(service)
                val isDragging = dragging == service

                val targetCx = if (isOn) geo.iconCenterX(index, visible.size)
                else geo.chipCenterX(hiddenIndex, hidden.size)
                val targetCy = if (isOn) geo.pillCenterY else geo.trayCenterY
                val animCx by animateFloatAsState(targetCx, spring(0.75f, Spring.StiffnessMedium), label = "islandX")
                val animCy by animateFloatAsState(targetCy, spring(0.75f, Spring.StiffnessMedium), label = "islandY")

                val chipWidthDp = with(density) { geo.chipWidth(hidden.size).toDp() }
                val width by animateDpAsState(
                    if (isOn) itemSize else chipWidthDp,
                    spring(0.8f, Spring.StiffnessMediumLow),
                    label = "islandW",
                )
                val height by animateDpAsState(
                    if (isOn) itemSize else chipHeight,
                    spring(0.8f, Spring.StiffnessMediumLow),
                    label = "islandH",
                )
                val corner by animateDpAsState(
                    if (isOn) 24.dp else 12.dp,
                    spring(0.8f, Spring.StiffnessMediumLow),
                    label = "islandCorner",
                )
                val chipAlpha by animateFloatAsState(
                    if (isOn) 0f else 1f,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "islandChip",
                )
                val scaleValue by animateFloatAsState(
                    if (isDragging) 1.15f else 1f,
                    spring(0.6f, Spring.StiffnessMedium),
                    label = "islandScale",
                )
                val highlighted = isDragging && isOn
                val background by animateColorAsState(
                    when {
                        highlighted -> cs.primary
                        !isOn -> cs.surface
                        else -> Color.Transparent
                    },
                    label = "islandBg",
                )
                val tint by animateColorAsState(if (highlighted) onPrimary else idle, label = "islandTint")
                val itemShape = RoundedCornerShape(corner)

                Box(
                    Modifier
                        .offset {
                            val cx = if (isDragging) dragCenter.x else animCx
                            val cy = if (isDragging) dragCenter.y else animCy
                            IntOffset(
                                (cx - width.toPx() / 2f).roundToInt(),
                                (cy - height.toPx() / 2f).roundToInt(),
                            )
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .size(width, height)
                        .graphicsLayer {
                            scaleX = scaleValue
                            scaleY = scaleValue
                            shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                            shape = itemShape
                            clip = false
                        }
                        .background(background, itemShape)
                        .border(1.dp, cs.outlineVariant.copy(alpha = chipAlpha), itemShape)
                        .clip(itemShape)
                        .pointerInput(service) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    if (dragging == null) {
                                        dragging = service
                                        // Сразу берём текущий центр: карточка не прыгает, а идёт от своего места
                                        dragCenter = Offset(animCx, animCy)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (dragging == service) {
                                        val g = geoNow
                                        dragCenter = Offset(
                                            (dragCenter.x + amount.x).coerceIn(0f, g.widthPx),
                                            (dragCenter.y + amount.y).coerceIn(0f, g.heightPx),
                                        )
                                        handleMoveNow(service, dragCenter)
                                    }
                                },
                                onDragEnd = { if (dragging == service) releaseNow(service) },
                                onDragCancel = { if (dragging == service) releaseNow(service) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Иконка в пилюле и чип в зоне скрытых перетекают друг в друга по прозрачности
                    ServiceLogo(service, Modifier.size(24.dp).alpha(1f - chipAlpha), tint)
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp).alpha(chipAlpha),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ServiceLogo(service, Modifier.size(18.dp), tint)
                        Text(
                            service.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}