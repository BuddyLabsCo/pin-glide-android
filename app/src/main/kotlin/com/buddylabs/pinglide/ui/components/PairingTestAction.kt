package com.buddylabs.pinglide.ui.components

import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import kotlin.math.roundToInt

private const val RECEIVED_NOTICE_MILLIS = 4_000L

@Composable
internal fun PairingTestAction(action: TestMessageAction) {
    val state by action.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.announceReceived) {
        if (action.consumeSuccess()) {
            Toast.makeText(context, R.string.pairing_test_received, Toast.LENGTH_SHORT).show()
        }
    }
    Button(
        onClick = { action.send(null) },
        enabled = !state.pending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        Text(testMessageButtonLabel(state.pending))
    }
    PairingTestFeedback(state.outcome)
}

@Composable
internal fun testMessageButtonLabel(pending: Boolean): String =
    stringResource(if (pending) R.string.pairing_test_sending else R.string.pairing_test_send)

@Composable
internal fun PairingTestFeedback(outcome: TestMessageOutcome?, modifier: Modifier = Modifier) {
    outcome?.let { delivery ->
        val label = testMessageLabels.getValue(delivery.result)
        Text(
            if (delivery.result == TestMessageResult.Received || delivery.targetAlias.isBlank()) {
                stringResource(label)
            } else {
                stringResource(
                    R.string.pairing_test_target_result,
                    delivery.targetAlias,
                    stringResource(label)
                )
            },
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
internal fun AnchoredPairingTestAction(
    action: TestMessageAction,
    accessibility: AccessibilityManager?,
    active: Boolean,
    content: @Composable (
        state: TestMessageState,
        send: () -> Unit,
        feedbackAnchor: Modifier
    ) -> Unit
) {
    val state by action.state.collectAsState()
    val received = active && state.outcome?.result == TestMessageResult.Received
    val message = stringResource(R.string.pairing_test_received)
    var dividerTop by remember { mutableFloatStateOf(0f) }
    var dividerVisible by remember { mutableStateOf(false) }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    Box(Modifier.onGloballyPositioned { viewportTop = it.positionInRoot().y }) {
        content(
            if (received) state.copy(outcome = null) else state,
            {
                action.send(
                    accessibility?.calculateRecommendedTimeoutMillis(
                        RECEIVED_NOTICE_MILLIS,
                        containsText = true
                    ) ?: RECEIVED_NOTICE_MILLIS
                )
            },
            Modifier.onGloballyPositioned {
                dividerTop = it.positionInRoot().y
                dividerVisible = it.boundsInRoot().height > 0f
            }
        )
        PairingTestStatus(
            message = if (received) message else "",
            modifier = Modifier.align(Alignment.Center)
        )
        if (received && dividerVisible) {
            Layout(
                modifier = Modifier.matchParentSize(),
                content = { PairingTestReceivedNotice() }
            ) { measurables, constraints ->
                val notice = measurables.single().measure(
                    constraints.copy(minWidth = 0, minHeight = 0)
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val center = dividerTop - viewportTop + 8.dp.toPx()
                    val top = (center - notice.height / 2f).roundToInt()
                        .coerceIn(0, constraints.maxHeight - notice.height)
                    notice.placeRelative((constraints.maxWidth - notice.width) / 2, top)
                }
            }
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
private fun PairingTestReceivedNotice() {
    val colors = MaterialTheme.colorScheme
    val shadowColor = if (isSystemInDarkTheme()) Color(0x61000000) else Color(0x260A1210)
    Text(
        stringResource(R.string.pairing_test_received),
        Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 280.dp)
            .dropShadow(CircleShape, Shadow(24.dp, shadowColor, offset = DpOffset(0.dp, 8.dp)))
            .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
            .semantics { hideFromAccessibility() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = colors.surface,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight(650),
            textAlign = TextAlign.Center
        )
    )
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
