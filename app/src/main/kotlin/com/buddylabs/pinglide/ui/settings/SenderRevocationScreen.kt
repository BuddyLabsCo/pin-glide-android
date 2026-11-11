package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.OwnerProofNotice
import com.buddylabs.pinglide.domain.identity.RevocationStep
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.identity.SenderRevocationState
import com.buddylabs.pinglide.ui.components.QqEmblem

@Composable
internal fun SenderRevocationScreen(state: SenderRevocationState, revocation: SenderRevocation) {
    val pending = state.step == RevocationStep.Submitting || state.step == RevocationStep.Retrying
    val proof = state.step == RevocationStep.Proof || state.step == RevocationStep.Verifying
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val (title, description) = when (state.step) {
        RevocationStep.Proof, RevocationStep.Verifying ->
            R.string.senders_proof_heading to R.string.senders_proof_description

        RevocationStep.Submitting ->
            R.string.senders_submitting to R.string.senders_submitting_description

        RevocationStep.Unconfirmed, RevocationStep.Retrying ->
            R.string.senders_unconfirmed to R.string.senders_unconfirmed_description

        RevocationStep.Revoked ->
            R.string.senders_revoked to R.string.senders_revoked_description

        RevocationStep.Confirmation -> error("Confirmation belongs to inventory")
    }
    SettingsPage(
        stringResource(
            if (proof) R.string.senders_proof_title else R.string.settings_device_access
        ),
        { revocation.dismiss(state) },
        backEnabled = !pending
    ) {
        if (proof) {
            QqEmblem()
        } else {
            Icon(painterResource(R.drawable.ic_devices), contentDescription = null)
        }
        Text(
            stringResource(title, state.selection.deviceName),
            Modifier.focusRequester(focus).focusable().semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Text(stringResource(description, state.selection.deviceName))
        OwnerProofFeedback(state.notice)
        SenderRevocationAction(state, revocation)
        if (state.step != RevocationStep.Submitting) {
            TextButton(
                onClick = { revocation.dismiss(state) },
                enabled = !pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (proof) R.string.setup_cancel else R.string.senders_return))
            }
        }
    }
    LaunchedEffect(state.step, state.notice, windowFocused) {
        if (windowFocused) focus.requestFocus()
    }
}

@Composable
private fun SenderRevocationAction(state: SenderRevocationState, revocation: SenderRevocation) {
    val label = when (state.step) {
        RevocationStep.Proof -> R.string.senders_verify_revoke
        RevocationStep.Verifying -> R.string.senders_verifying
        RevocationStep.Unconfirmed -> R.string.senders_revoke_retry
        RevocationStep.Retrying -> R.string.senders_revoke_retrying
        else -> return
    }
    Button(
        onClick = {
            if (state.step == RevocationStep.Proof) {
                revocation.verify(state)
            } else {
                revocation.retry(state)
            }
        },
        enabled = state.step == RevocationStep.Proof || state.step == RevocationStep.Unconfirmed,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(stringResource(label))
    }
}

@Composable
private fun OwnerProofFeedback(notice: OwnerProofNotice) {
    val message = when (notice) {
        OwnerProofNotice.None -> return
        OwnerProofNotice.Cancelled -> R.string.senders_proof_cancelled
        OwnerProofNotice.Failed -> R.string.senders_proof_failed
        OwnerProofNotice.WrongAccount -> R.string.senders_proof_wrong_account
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            stringResource(message),
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive }
        )
    }
}
