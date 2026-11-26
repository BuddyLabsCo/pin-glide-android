package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
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
import com.buddylabs.pinglide.ui.components.WarningInfoCard

@Composable
internal fun SenderRevocationScreen(state: SenderRevocationState, revocation: SenderRevocation) {
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val focusModifier = Modifier.focusRequester(focus).focusable()
    val headingModifier = focusModifier.semantics {
        heading()
        liveRegion = LiveRegionMode.Polite
    }
    LaunchedEffect(state.step, state.notice, windowFocused) {
        if (windowFocused) focus.requestFocus()
    }
    when (state.step) {
        RevocationStep.Proof, RevocationStep.Verifying ->
            OwnerProofScreen(state, revocation, headingModifier)

        RevocationStep.Submitting -> RevocationSubmittingScreen(state, focusModifier)

        RevocationStep.Unconfirmed, RevocationStep.Retrying ->
            RevocationResultScreen(
                state,
                revocation,
                headingModifier,
                R.string.senders_unconfirmed,
                R.string.senders_unconfirmed_description
            )

        RevocationStep.Revoked ->
            RevocationResultScreen(
                state,
                revocation,
                headingModifier,
                R.string.senders_revoked,
                R.string.senders_revoked_description
            )

        RevocationStep.Confirmation -> error("Confirmation belongs to inventory")
    }
}

@Composable
private fun RevocationResultScreen(
    state: SenderRevocationState,
    revocation: SenderRevocation,
    headingModifier: Modifier,
    title: Int,
    description: Int
) {
    val pending = state.step == RevocationStep.Retrying
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
        TextButton(
            onClick = { revocation.dismiss(state) },
            enabled = !pending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.senders_return))
        }
    }
}

@Composable
private fun RevocationSubmittingScreen(state: SenderRevocationState, focusModifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RingedEmblem(RoundedCornerShape(44.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_revoke_lock),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = colors.onPrimaryContainer
                    )
                }
                Column(
                    focusModifier.semantics(mergeDescendants = true) {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.senders_submitting),
                        Modifier.fillMaxWidth().padding(top = 7.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 26.sp,
                            lineHeight = 29.4.sp,
                            fontWeight = FontWeight(750),
                            letterSpacing = (-1.17).sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(
                            R.string.senders_submitting_description,
                            state.selection.deviceName
                        ),
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.size(26.dp))
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(0.38f),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = colors.primary,
                        disabledContentColor = colors.onPrimary
                    )
                ) {
                    Text(
                        stringResource(R.string.senders_submitting_wait),
                        Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.14.sp,
                        textAlign = TextAlign.Center
                    )
                }
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
    WarningInfoCard(
        message,
        Modifier.padding(top = 14.dp).width(IntrinsicSize.Max)
            .testTag("owner-proof-warning")
            .semantics { liveRegion = LiveRegionMode.Assertive },
        icon = R.drawable.ic_warning
    )
}
