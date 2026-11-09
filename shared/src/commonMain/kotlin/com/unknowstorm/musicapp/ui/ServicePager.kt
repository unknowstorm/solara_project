package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.unknowstorm.musicapp.model.Service

/** Порядок страниц как в панели: ♡ (null), затем чипы слева направо. Spotify вне пейджера: вход через «+». */
fun pagerOrder(hasLikes: Boolean): List<Service?> =
    (if (hasLikes) listOf<Service?>(null) else emptyList<Service?>()) +
        Service.entries.filter { it.ready && it != Service.SPOTIFY }

/**
 * Страницы-источники под неподвижной панелью. Соседние страницы рисуются во время жеста.
 * page(service, active): service == null это ♡; active = страница остановилась (не просто мелькает).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServicePager(
    current: Service,
    likesSelected: Boolean,
    hasLikes: Boolean,
    swipeEnabled: Boolean,
    onSelect: (Service) -> Unit,
    onLikes: () -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (service: Service?, active: Boolean) -> Unit,
) {
    val pages = remember(hasLikes) { pagerOrder(hasLikes) }
    val target = if (likesSelected && hasLikes) 0
    else pages.indexOf(current).let { if (it >= 0) it else pages.indexOf(Service.ALL) }
    val state = rememberPagerState(initialPage = target) { pages.size }

    val curNow by rememberUpdatedState(current)
    val likesNow by rememberUpdatedState(likesSelected)
    val select by rememberUpdatedState(onSelect)
    val openLikes by rememberUpdatedState(onLikes)

    // Чип нажали: пейджер едет к странице
    LaunchedEffect(target) {
        if (state.currentPage != target) state.animateScrollToPage(target)
    }
    // Страница остановилась (свайп): обновляем выбранный чип
    LaunchedEffect(state, pages) {
        snapshotFlow { state.settledPage }.collect { idx ->
            if (idx !in pages.indices) return@collect
            val p = pages[idx]
            if (p == null) {
                if (!likesNow) openLikes()
            } else if (likesNow || p != curNow) {
                select(p)
            }
        }
    }

    HorizontalPager(
        state = state,
        modifier = modifier,
        userScrollEnabled = swipeEnabled,
        key = { pages[it]?.name ?: "likes" },
    ) { index -> page(pages[index], state.settledPage == index) }
}
