package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult

@Composable
internal fun PairingTestAction(pending: Boolean, outcome: TestMessageOutcome?, onSend: () -> Unit) {
    Button(
        onClick = onSend,
        enabled = !pending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text(
            stringResource(
                if (pending) R.string.pairing_test_sending else R.string.pairing_test_send
            )
        )
    }
    outcome?.let { delivery ->
        val label = when (delivery.result) {
            TestMessageResult.Received -> R.string.pairing_test_received
            TestMessageResult.Offline -> R.string.pairing_test_offline
            TestMessageResult.InvalidPairing -> R.string.pairing_test_invalid_pairing
            TestMessageResult.InvalidPayload -> R.string.pairing_test_invalid_payload
            TestMessageResult.UnsupportedEncryption -> R.string.pairing_test_unsupported_encryption
            TestMessageResult.DuplicateTarget -> R.string.pairing_test_duplicate_target
            TestMessageResult.Uncertain -> R.string.pairing_test_uncertain
            TestMessageResult.NoKey -> R.string.pairing_test_no_key
            TestMessageResult.LoginRequired -> R.string.pairing_test_login
        }
        Text(
            stringResource(
                R.string.pairing_test_target_result,
                delivery.targetAlias,
                stringResource(label)
            ),
            modifier = Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            }
        )
    }
}
