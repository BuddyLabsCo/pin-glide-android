package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeState

internal suspend fun LazyListState.revealTarget(
    index: Int,
    pairingId: String?,
    clearance: Int = 0
) {
    if (index < 0) return
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.find { it.key == pairingId }
    if (item != null && item.offset >= layout.viewportStartOffset + clearance &&
        item.offset + item.size <= layout.viewportEndOffset - clearance
    ) {
        return
    }
    scrollToItem(index + 1, -clearance)
    val revealed = layoutInfo
    val target = revealed.visibleItemsInfo.find { it.key == pairingId } ?: return
    val hiddenBottom = target.offset + target.size + clearance - revealed.viewportEndOffset
    if (hiddenBottom > 0) scrollBy(hiddenBottom.toFloat())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeRefreshContainer(
    state: HomeState,
    showListProgress: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable (refreshHint: @Composable () -> Unit) -> Unit
) {
    val pull = rememberPullToRefreshState()
    val viewport = remember { LayoutBoundsHolder() }
    var refreshLabelVisible by remember { mutableStateOf(false) }
    val refreshLabel = stringResource(R.string.home_refresh)
    val refreshingLabel = stringResource(R.string.home_refreshing)
    val listLabel = stringResource(R.string.home_list_accessibility)
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pull,
        modifier = modifier.fillMaxWidth().layoutBounds(viewport).semantics {
            contentDescription = listLabel
            if (state.refreshing && !showListProgress && !refreshLabelVisible) {
                stateDescription = refreshingLabel
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                liveRegion = LiveRegionMode.Polite
            }
            customActions = listOf(
                CustomAccessibilityAction(refreshLabel) {
                    onRefresh()
                    true
                }
            )
        }.onKeyEvent {
            if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                listState.requestScrollToItem(0)
                onRefresh()
                true
            } else {
                false
            }
        }.focusable(),
        indicator = {}
    ) {
        content {
            val distanceFraction = pull.distanceFraction
            val hint = when {
                showListProgress -> null
                state.refreshing -> R.string.home_refreshing
                pull.isAnimating -> null
                distanceFraction >= 1f -> R.string.home_pull_release
                distanceFraction > 0f -> R.string.home_pull_down
                else -> null
            }
            if (hint != null) {
                val visibility = if (state.refreshing) {
                    Modifier.onVisibilityChanged(viewportBounds = viewport) { visible ->
                        refreshLabelVisible = visible
                    }
                } else {
                    Modifier
                }
                HomeRefreshHint(
                    hint,
                    state.refreshing,
                    distanceFraction,
                    refreshLabelVisible,
                    visibility
                )
            }
        }
    }
}

@Composable
private fun HomeRefreshHint(
    label: Int,
    refreshing: Boolean,
    distanceFraction: Float,
    refreshLabelVisible: Boolean,
    labelModifier: Modifier
) {
    val dimensions = if (refreshing) {
        Modifier.heightIn(min = 48.dp)
    } else {
        Modifier.height(64.dp * distanceFraction.coerceIn(0f, 1f))
            .clipToBounds().wrapContentHeight(unbounded = true)
    }
    val status = when {
        !refreshing -> Modifier.semantics { liveRegion = LiveRegionMode.Polite }

        refreshLabelVisible -> Modifier.progressSemantics()
            .semantics { liveRegion = LiveRegionMode.Polite }

        else -> Modifier.semantics { hideFromAccessibility() }
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().then(dimensions).semantics(mergeDescendants = true) {}.then(status),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (refreshing) {
            HomeLoadingSpinner(color)
        } else {
            Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(16.dp), tint = color)
        }
        Text(
            stringResource(label),
            modifier = labelModifier,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.sp)
        )
    }
}
