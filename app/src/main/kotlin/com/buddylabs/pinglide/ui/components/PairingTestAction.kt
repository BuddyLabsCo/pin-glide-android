package com.buddylabs.pinglide.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult

@Composable
internal fun PairingTestAction(action: TestMessageAction, enabled: Boolean = true) {
    val state by action.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.announceReceived) {
        if (action.consumeSuccess()) {
            Toast.makeText(context, R.string.pairing_test_received, Toast.LENGTH_SHORT).show()
        }
    }
    Button(
        onClick = action::send,
        enabled = enabled && !state.pending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        Text(
            stringResource(
                if (state.pending) R.string.pairing_test_sending else R.string.pairing_test_send
            )
        )
    }
    state.outcome?.let { delivery ->
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
            modifier = Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            }
        )
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
