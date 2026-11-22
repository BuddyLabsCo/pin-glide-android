package com.buddylabs.pinglide.ui.home

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import kotlinx.coroutines.delay

internal val HomeAddTop = 12.dp
internal val HomeAddBottom = 24.dp
internal val HomeAddHeight = 56.dp

@Composable
internal fun rememberDuplicateNotice(
    highlightedPairingId: String?,
    owner: Pair<String, String>?
): Boolean {
    val accessibility = LocalAccessibilityManager.current
    var deadline by rememberSaveable(owner) { mutableLongStateOf(0L) }
    LaunchedEffect(highlightedPairingId, owner) {
        val now = SystemClock.elapsedRealtime()
        if (highlightedPairingId != null && deadline <= now) {
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                3_000L,
                containsText = true
            ) ?: 3_000L
            deadline = now + timeout.coerceAtMost(Long.MAX_VALUE - now)
        }
    }
    LaunchedEffect(deadline, owner) {
        if (deadline != 0L) {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            deadline = 0L
        }
    }
    return highlightedPairingId != null || deadline != 0L
}

@Composable
internal fun ColumnScope.HomeFooterNotice(
    showDuplicate: Boolean,
    showRemoval: Boolean,
    showAdd: Boolean,
    viewportHeight: Dp
) {
    val message = when {
        showDuplicate -> R.string.pairing_already_added
        showRemoval -> R.string.home_removed
        else -> return
    }
    val bottomGap = when {
        showDuplicate -> {
            val addSpace = if (showAdd) HomeAddTop + HomeAddHeight + HomeAddBottom else 0.dp
            (minOf(140.dp, viewportHeight / 5) - addSpace).coerceAtLeast(0.dp)
        }

        showAdd -> minOf(48.dp, viewportHeight / 16)

        else -> minOf(140.dp, viewportHeight / 3)
    }
    HomeNotice(
        message,
        Modifier.align(Alignment.CenterHorizontally)
            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = bottomGap)
    )
}

@Composable
private fun HomeNotice(@StringRes message: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(message),
        modifier = modifier.widthIn(max = 280.dp)
            .shadow(8.dp, CircleShape)
            .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = colors.surface,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight(650),
        textAlign = TextAlign.Center
    )
}
