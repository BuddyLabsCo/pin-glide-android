package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun PairingResultLayout(
    title: String,
    bodyBottomPadding: Dp,
    body: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    feedback: @Composable (bottomOffset: Dp?) -> Unit = {}
) {
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            val needsScroll = scroll.maxValue > 0
            var actionsHeight by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxWidth().verticalScroll(scroll)
                    .heightIn(min = maxHeight).height(IntrinsicSize.Min)
                    .semantics { paneTitle = title }
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .padding(top = 12.dp, bottom = bodyBottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    body()
                }
                Column(
                    Modifier.onSizeChanged { actionsHeight = it.height },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (needsScroll) feedback(null)
                    actions()
                }
            }
            if (!needsScroll) {
                feedback(with(LocalDensity.current) { actionsHeight.toDp() } + 36.dp)
            }
        }
    }
}
