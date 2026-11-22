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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable (refreshHint: @Composable () -> Unit) -> Unit
) {
    val pull = rememberPullToRefreshState()
    val refreshLabel = stringResource(R.string.home_refresh)
    val listLabel = stringResource(R.string.home_list_accessibility)
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pull,
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = listLabel
            customActions = listOf(
                CustomAccessibilityAction(refreshLabel) {
                    onRefresh()
                    true
                }
            )
        }.onKeyEvent {
            if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                onRefresh()
                true
            } else {
                false
            }
        }.focusable(),
        indicator = {}
    ) {
        content {
            val distance = pull.distanceFraction
            val hint = when {
                showListProgress -> null
                state.refreshing -> R.string.home_refreshing
                pull.isAnimating -> null
                distance >= 1f -> R.string.home_pull_release
                distance > 0f -> R.string.home_pull_down
                else -> null
            }
            if (hint != null) {
                HomeRefreshHint(hint, state.refreshing, distance)
            }
        }
    }
}

@Composable
private fun HomeRefreshHint(label: Int, refreshing: Boolean, distance: Float) {
    val dimensions = if (refreshing) {
        Modifier.heightIn(min = 48.dp).progressSemantics()
    } else {
        Modifier.height(48.dp * distance.coerceIn(0f, 1f))
            .clipToBounds().wrapContentHeight(unbounded = true)
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().then(dimensions).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
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
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, letterSpacing = 0.sp)
        )
    }
}
