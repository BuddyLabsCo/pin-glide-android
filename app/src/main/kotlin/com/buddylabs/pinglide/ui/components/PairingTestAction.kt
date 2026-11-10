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
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult

@Composable
internal fun PairingTestAction(pending: Boolean, result: TestMessageResult?, onSend: () -> Unit) {
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
    result?.let { outcome ->
        val label = when (outcome) {
            TestMessageResult.Received -> R.string.pairing_test_received
            TestMessageResult.Offline -> R.string.pairing_test_offline
            TestMessageResult.Rejected -> R.string.pairing_test_rejected
            TestMessageResult.Uncertain -> R.string.pairing_test_uncertain
            TestMessageResult.NoKey -> R.string.pairing_test_no_key
            TestMessageResult.LoginRequired -> R.string.pairing_test_login
        }
        Text(
            stringResource(label),
            modifier = Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            }
        )
    }
}
