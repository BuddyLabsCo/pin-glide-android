package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.FeedbackPill
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun AccountPage(
    noticeGeneration: Long?,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val bottomInset = WindowInsets.safeDrawing.getBottom(LocalDensity.current)
    val scrollState = rememberScrollState()
    var revealedGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(noticeGeneration) {
        if (noticeGeneration != null && noticeGeneration != revealedGeneration) {
            scrollState.scroll(MutatePriority.PreventUserInput) {
                scrollBy(-scrollState.value.toFloat())
            }
        }
        revealedGeneration = noticeGeneration
    }
    ScreenLayout {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = constraints.maxHeight
            Layout(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                content = {
                    SettingsPageHeader(
                        stringResource(R.string.settings_title),
                        onBack,
                        rootLayout = true
                    )
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 28.dp)) {
                        content()
                    }
                    if (noticeGeneration != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FeedbackPill(
                                stringResource(R.string.account_error),
                                accessible = true,
                                modifier = Modifier.padding(horizontal = 4.dp).widthIn(max = 280.dp)
                            )
                        }
                    }
                }
            ) { measurables, constraints ->
                val loose = constraints.copy(minHeight = 0)
                val header = measurables[0].measure(loose)
                val cards = measurables[1].measure(loose)
                val feedback = measurables.getOrNull(2)?.measure(loose)
                val feedbackHeight = feedback?.height ?: 0
                val preferredTop = viewportHeight - (164.dp.roundToPx() - bottomInset)
                    .coerceAtLeast(0) - feedbackHeight
                val pageHeight = header.height + cards.height
                val fits = feedback == null || preferredTop >= pageHeight
                val noticeFirst = !fits && header.height + feedbackHeight > viewportHeight
                val headerTop = if (noticeFirst) feedbackHeight else 0
                val cardsTop = header.height + if (fits) 0 else feedbackHeight
                val feedbackTop = when {
                    fits -> preferredTop
                    noticeFirst -> 0
                    else -> header.height
                }
                layout(constraints.maxWidth, maxOf(viewportHeight, cardsTop + cards.height)) {
                    header.placeRelative(0, headerTop)
                    cards.placeRelative(0, cardsTop)
                    feedback?.placeRelative(0, feedbackTop)
                }
            }
        }
    }
}
