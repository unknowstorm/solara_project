package com.unknowstorm.musicapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.floor
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.unknowstorm.musicapp.model.Service

/**
 * Page order as in the bar: ♡ (null), "All", then the services from the "Island" settings (hidden ones are absent).
 * Spotify is outside the pager: entry via "+". TopBar takes its chips from this same list, so the chip index = the page index.
 * Reads IslandSettings (snapshot state): called in composition, it re-reads after "Save" in the editor.
 */
fun pagerOrder(hasLikes: Boolean, visible: List<Service> = IslandSettings.visible()): List<Service?> =
    (if (hasLikes) listOf<Service?>(null) else emptyList<Service?>()) + Service.ALL + visible

/**
 * Source pages under the fixed bar. Neighboring pages are drawn during the gesture.
 * page(service, active): service == null is ♡; active = the page has stopped (is not just flashing by).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServicePager(
    current: Service,
    likesSelected: Boolean,
    hasLikes: Boolean,
    swipeEnabled: Boolean,
    onSelect: (Service) -> Unit,
    onLikes: () -> Unit,
    modifier: Modifier = Modifier,
    // Continuous position: page + fraction of the offset. Changes both on swipe and on a chip tap
    onPosition: (Float) -> Unit = {},
    page: @Composable (service: Service?, active: Boolean) -> Unit,
) {
    val visible = IslandSettings.visible()
    val pages = remember(hasLikes, visible) { pagerOrder(hasLikes, visible) }
    // The order changed: the pager is rebuilt on the page of the current service, otherwise for a moment
    // the old index would point to another service and the "settled page" effect would select it
    key(pages) {
        ServicePagerBody(pages, current, likesSelected, hasLikes, swipeEnabled, onSelect, onLikes, modifier, onPosition, page)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServicePagerBody(
    pages: List<Service?>,
    current: Service,
    likesSelected: Boolean,
    hasLikes: Boolean,
    swipeEnabled: Boolean,
    onSelect: (Service) -> Unit,
    onLikes: () -> Unit,
    modifier: Modifier,
    onPosition: (Float) -> Unit,
    page: @Composable (service: Service?, active: Boolean) -> Unit,
) {
    val target = if (likesSelected && hasLikes) 0
    else pages.indexOf(current).let { if (it >= 0) it else pages.indexOf(Service.ALL) }
    val state = rememberPagerState(initialPage = target) { pages.size }

    val curNow by rememberUpdatedState(current)
    val likesNow by rememberUpdatedState(likesSelected)
    val select by rememberUpdatedState(onSelect)
    val openLikes by rememberUpdatedState(onLikes)
    val positionNow by rememberUpdatedState(onPosition)
    // A chip tap moves along a spring from the theme's MotionScheme (expressive)
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(state) {
        snapshotFlow { state.currentPage + state.currentPageOffsetFraction }
            .collect { positionNow(it) }
    }

    // A chip was tapped: the pager moves to the page
    LaunchedEffect(target) {
        if (state.currentPage != target) state.animateScrollToPage(target, animationSpec = motion)
    }
    // The page has stopped (swipe): update the selected chip
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
