package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.OwnerProofNotice
import com.buddylabs.pinglide.domain.identity.RevocationStep
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.identity.SenderRevocationState
import com.buddylabs.pinglide.ui.components.BrandIcon
import com.buddylabs.pinglide.ui.components.RingedEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun SenderRevocationScreen(state: SenderRevocationState, revocation: SenderRevocation) {
    val pending = state.step == RevocationStep.Submitting || state.step == RevocationStep.Retrying
    val proof = state.step == RevocationStep.Proof || state.step == RevocationStep.Verifying
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val headingModifier = Modifier.focusRequester(focus).focusable().semantics {
        heading()
        liveRegion = LiveRegionMode.Polite
    }
    LaunchedEffect(state.step, state.notice, windowFocused) {
        if (windowFocused) focus.requestFocus()
    }
    if (proof) {
        OwnerProofScreen(state, revocation, headingModifier)
        return
    }
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
        stringResource(R.string.settings_device_access),
        { revocation.dismiss(state) },
        backEnabled = !pending
    ) {
        Icon(painterResource(R.drawable.ic_devices), contentDescription = null)
        Text(
            stringResource(title, state.selection.deviceName),
            headingModifier,
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
                Text(stringResource(R.string.senders_return))
            }
        }
    }
}

@Composable
private fun OwnerProofScreen(
    state: SenderRevocationState,
    revocation: SenderRevocation,
    headingModifier: Modifier
) {
    val bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    ScreenLayout {
        SettingsPageHeader(
            stringResource(R.string.senders_proof_title),
            { revocation.dismiss(state) },
            rootLayout = true
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(bottom = (22.dp - bottomInset).coerceAtLeast(0.dp))
            ) {
                Spacer(Modifier.weight(1f))
                OwnerProofMessage(state, headingModifier)
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SenderRevocationAction(state, revocation)
                    TextButton(
                        onClick = { revocation.dismiss(state) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(R.string.setup_cancel),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerProofMessage(state: SenderRevocationState, headingModifier: Modifier) {
    Column(
        Modifier.fillMaxWidth().padding(top = 21.dp, bottom = 63.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RingedEmblem(CircleShape) {
            BrandIcon(Modifier.size(92.dp), RoundedCornerShape(28.dp))
        }
        Row(
            Modifier.padding(top = 21.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(
                Modifier.size(
                    18.dp,
                    2.dp
                ).background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Text(
                stringResource(R.string.senders_proof_eyebrow),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(800),
                letterSpacing = 1.21.sp
            )
        }
        Text(
            stringResource(R.string.senders_proof_heading),
            headingModifier,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-1.17).sp
            ),
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.senders_proof_description, state.selection.deviceName),
            Modifier.padding(top = 8.dp).widthIn(max = 305.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp
            ),
            textAlign = TextAlign.Center
        )
        OwnerProofFeedback(state.notice)
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
        if (state.step == RevocationStep.Proof || state.step == RevocationStep.Verifying) {
            Icon(painterResource(R.drawable.ic_account_circle), null, Modifier.size(22.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(label), fontWeight = FontWeight.Bold, letterSpacing = 0.14.sp)
        } else {
            Text(stringResource(label))
        }
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
