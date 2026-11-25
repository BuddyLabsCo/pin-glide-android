package com.buddylabs.pinglide.ui.components

import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import kotlin.math.roundToInt

private const val TEST_NOTICE_MILLIS = 4_000L

@Composable
internal fun PairingTestAction(
    action: TestMessageAction,
    content: @Composable (button: @Composable () -> Unit, notice: @Composable () -> Unit) -> Unit
) {
    val state by action.state.collectAsState()
    val context = LocalContext.current
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(state.announceReceived) {
        if (action.consumeSuccess()) {
            Toast.makeText(context, R.string.pairing_test_received, Toast.LENGTH_SHORT).show()
        }
    }
    content(
        {
            PairingTestButton(state.pending) {
                action.send(
                    accessibility?.calculateRecommendedTimeoutMillis(
                        TEST_NOTICE_MILLIS,
                        containsText = true
                    ) ?: TEST_NOTICE_MILLIS,
                    announceSuccess = true
                )
            }
        },
        {
            state.outcome?.let { delivery ->
                PairingTestNotice(
                    message = testMessageFeedbackText(delivery),
                    anchor = null,
                    headerBottom = 0f,
                    alignBottom = false,
                    bottomOffset = 140.dp,
                    modifier = Modifier.fillMaxSize(),
                    accessible = true
                )
            }
        }
    )
}

@Composable
private fun PairingTestButton(pending: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !pending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        },
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
    ) {
        Icon(painterResource(R.drawable.ic_send), null, Modifier.size(22.dp))
        Spacer(Modifier.size(8.dp))
        Text(testMessageButtonLabel(pending), fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun testMessageButtonLabel(pending: Boolean): String =
    stringResource(if (pending) R.string.pairing_test_sending else R.string.pairing_test_send)

@Composable
internal fun PairingTestFeedback(outcome: TestMessageOutcome?, modifier: Modifier = Modifier) {
    outcome?.let { delivery ->
        Text(
            testMessageFeedbackText(delivery),
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun testMessageFeedbackText(delivery: TestMessageOutcome): String {
    val label = testMessageLabels.getValue(delivery.result)
    return if (delivery.result == TestMessageResult.Received || delivery.targetAlias.isBlank()) {
        stringResource(label)
    } else {
        stringResource(
            R.string.pairing_test_target_result,
            delivery.targetAlias,
            stringResource(label)
        )
    }
}

@Composable
internal fun AnchoredPairingTestAction(
    action: TestMessageAction,
    accessibility: AccessibilityManager?,
    active: Boolean,
    headerBottom: Float,
    modifier: Modifier = Modifier,
    content: @Composable (
        state: TestMessageState,
        send: () -> Unit,
        feedbackAnchor: Modifier
    ) -> Unit
) {
    val state by action.state.collectAsState()
    val notice = state.outcome?.takeIf { active }
    val label = notice?.detailsNoticeLabel
    val removed = notice?.pairingRemoved == true
    val alignNoticeBottom = notice?.result == TestMessageResult.Offline ||
        notice?.result == TestMessageResult.AckTimeout
    val uncertain = notice?.result == TestMessageResult.Uncertain
    val message = if (label == null) "" else stringResource(label)
    val status = if (removed) {
        val alias = notice.targetAlias.ifBlank { stringResource(R.string.home_target_device) }
        stringResource(R.string.pairing_test_target_result, alias, message)
    } else {
        message
    }
    var dividerTop by remember { mutableFloatStateOf(0f) }
    var dividerVisible by remember { mutableStateOf(false) }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    Box(modifier.onGloballyPositioned { viewportTop = it.positionInRoot().y }) {
        content(
            state,
            {
                action.send(
                    accessibility?.calculateRecommendedTimeoutMillis(
                        TEST_NOTICE_MILLIS,
                        containsText = true
                    ) ?: TEST_NOTICE_MILLIS
                )
            },
            Modifier.onGloballyPositioned {
                dividerTop = it.positionInRoot().y
                dividerVisible = it.boundsInRoot().height > 0f
            }
        )
        PairingTestStatus(
            message = status,
            modifier = Modifier.align(Alignment.Center)
        )
        if (label != null && (dividerVisible || removed || alignNoticeBottom || uncertain)) {
            PairingTestNotice(
                message = message,
                anchor = (dividerTop - viewportTop).takeIf { dividerVisible || alignNoticeBottom },
                headerBottom = if (removed) headerBottom - viewportTop else 0f,
                alignBottom = alignNoticeBottom,
                bottomOffset = 164.dp.takeIf { uncertain },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun PairingTestStatus(message: String, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                text = message
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setTextColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier.size(width = 120.dp, height = 24.dp),
        update = { if (it.text.toString() != message) it.text = message }
    )
}

@Composable
private fun PairingTestNotice(
    message: String,
    anchor: Float?,
    headerBottom: Float,
    alignBottom: Boolean,
    bottomOffset: Dp?,
    modifier: Modifier,
    accessible: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val shadowColor = if (isSystemInDarkTheme()) Color(0x61000000) else Color(0x260A1210)
    Layout(
        modifier = modifier,
        content = {
            Text(
                message,
                Modifier.padding(horizontal = 24.dp)
                    .widthIn(max = 280.dp)
                    .dropShadow(
                        CircleShape,
                        Shadow(24.dp, shadowColor, offset = DpOffset(0.dp, 8.dp))
                    )
                    .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
                    .semantics {
                        if (accessible) {
                            liveRegion = LiveRegionMode.Polite
                        } else {
                            hideFromAccessibility()
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = colors.surface,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight(650),
                    textAlign = TextAlign.Center
                )
            )
        }
    ) { measurables, constraints ->
        val notice = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val maxTop = constraints.maxHeight - notice.height
            val minTop = headerBottom.roundToInt().coerceIn(0, maxTop)
            val top = when {
                bottomOffset != null -> maxTop - bottomOffset.roundToPx()
                anchor == null -> (minTop + maxTop) / 2
                alignBottom -> (anchor + 29.dp.toPx() - notice.height).roundToInt()
                else -> (anchor + 8.dp.toPx() - notice.height / 2f).roundToInt()
            }
            val visibleTop = top.coerceIn(minTop, maxTop)
            notice.placeRelative((constraints.maxWidth - notice.width) / 2, visibleTop)
        }
    }
}

private val testMessageLabels = mapOf(
    TestMessageResult.Received to R.string.pairing_test_received,
    TestMessageResult.Offline to R.string.pairing_test_offline,
    TestMessageResult.Rejected to R.string.pairing_test_rejected,
    TestMessageResult.SendFailed to R.string.pairing_test_send_failed,
    TestMessageResult.AckTimeout to R.string.pairing_test_ack_timeout,
    TestMessageResult.Interrupted to R.string.pairing_test_interrupted,
    TestMessageResult.RequestError to R.string.pairing_test_request_error,
    TestMessageResult.InvalidPairing to R.string.pairing_test_invalid_pairing,
    TestMessageResult.InvalidPayload to R.string.pairing_test_invalid_payload,
    TestMessageResult.UnsupportedEncryption to R.string.pairing_test_unsupported_encryption,
    TestMessageResult.DuplicateTarget to R.string.pairing_test_duplicate_target,
    TestMessageResult.Uncertain to R.string.pairing_test_uncertain,
    TestMessageResult.NoKey to R.string.pairing_test_no_key,
    TestMessageResult.LoginRequired to R.string.pairing_test_login
)
