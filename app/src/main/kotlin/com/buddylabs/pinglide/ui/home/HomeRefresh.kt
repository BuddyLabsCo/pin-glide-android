package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
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
        indicator = {
            val hint = when {
                state.refreshing -> R.string.home_refreshing
                pull.distanceFraction >= 1f -> R.string.home_pull_release
                pull.distanceFraction > 0f -> R.string.home_pull_down
                else -> null
            }
            if (hint != null) {
                Surface(Modifier.align(Alignment.TopCenter), shadowElevation = 4.dp) {
                    Text(
                        stringResource(hint),
                        modifier = Modifier.padding(8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
    ) { content() }
}
