package com.buddylabs.pinglide.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult

private const val RECEIVED_NOTICE_MILLIS = 4_000L

@Composable
internal fun PairingTestAction(
    action: TestMessageAction,
    enabled: Boolean = true,
    feedbackModifier: Modifier = Modifier,
    overlayReceived: Boolean = false,
    button: @Composable (
        label: String,
        available: Boolean,
        sending: Boolean,
        onClick: () -> Unit
    ) -> Unit = { label, available, _, onClick ->
        Button(
            onClick = onClick,
            enabled = available,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                liveRegion = LiveRegionMode.Polite
            }
        ) { Text(label) }
    }
) {
    val state by action.state.collectAsState()
    val context = LocalContext.current
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(state.announceReceived) {
        if (action.consumeSuccess()) {
            Toast.makeText(context, R.string.pairing_test_received, Toast.LENGTH_SHORT).show()
        }
    }
    button(
        stringResource(
            if (state.pending) R.string.pairing_test_sending else R.string.pairing_test_send
        ),
        enabled && !state.pending,
        state.pending,
        {
            val noticeMillis = if (overlayReceived) {
                accessibility?.calculateRecommendedTimeoutMillis(
                    RECEIVED_NOTICE_MILLIS,
                    containsText = true
                ) ?: RECEIVED_NOTICE_MILLIS
            } else {
                null
            }
            action.send(noticeMillis)
        }
    )
    val inlineOutcome = state.outcome?.takeUnless {
        overlayReceived && it.result == TestMessageResult.Received
    }
    inlineOutcome?.let { delivery ->
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
            modifier = feedbackModifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
internal fun PairingTestReceivedNotice(action: TestMessageAction, modifier: Modifier = Modifier) {
    val state by action.state.collectAsState()
    if (state.outcome?.result != TestMessageResult.Received) return
    val colors = MaterialTheme.colorScheme
    val shadowColor = if (isSystemInDarkTheme()) Color(0x61000000) else Color(0x260A1210)
    Text(
        stringResource(R.string.pairing_test_received),
        modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 280.dp)
            .dropShadow(CircleShape, Shadow(24.dp, shadowColor, offset = DpOffset(0.dp, 8.dp)))
            .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
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
