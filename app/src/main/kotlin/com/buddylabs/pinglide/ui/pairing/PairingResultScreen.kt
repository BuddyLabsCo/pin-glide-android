package com.buddylabs.pinglide.ui.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.pairing.PairingStage
import com.buddylabs.pinglide.ui.components.BrandIcon
import com.buddylabs.pinglide.ui.components.LoadingEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun PairingResultScreen(
    state: PairingEntryState,
    model: PairingEntryViewModel,
    onLeave: () -> Unit
) {
    BackHandler(onBack = model::close)
    if (state.page == PairingEntryPage.Success) {
        PairingSuccessScreen(model.testMessage, model::close)
        return
    }
    val unconfirmed = state.problem == PairingEntryProblem.Unconfirmed
    val title = stringResource(
        when {
            unconfirmed -> R.string.pairing_unconfirmed_title
            state.problem != null -> R.string.pairing_failed_title
            else -> R.string.pairing_progress_title
        }
    )
    if (state.problem == PairingEntryProblem.Unavailable) {
        PairingFailureScreen(state, model::freshScan, onLeave)
        return
    }
    if (state.problem == null) {
        PairingProgressScreen(state.stage)
        return
    }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(title) { titleFocus.requestFocus() }
    ScreenLayout {
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState())
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.focusRequester(titleFocus).focusable().semantics {
                    heading()
                }
            )
            Text(
                stringResource(pairingProblemText(state.problem)),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Button(
                onClick = {
                    if (unconfirmed) model.foreground() else model.freshScan()
                },
                enabled = !state.pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(
                    stringResource(
                        if (unconfirmed) R.string.pairing_recheck else R.string.pairing_rescan
                    )
                )
            }
            TextButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(
                    stringResource(
                        if (state.host == PairingHost.Onboarding) {
                            R.string.pairing_later
                        } else {
                            R.string.pairing_return
                        }
                    )
                )
            }
        }
    }
}

@Composable
internal fun PairingProgressScreen(stage: PairingStage) {
    val title = stringResource(R.string.pairing_progress_title)
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = minOf(maxHeight, 700.dp)).padding(6.dp, 26.dp)
                    .semantics { paneTitle = title },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LoadingEmblem { BrandIcon(Modifier.size(92.dp), RoundedCornerShape(28.dp)) }
                PairingAnnouncedHeading(
                    stringResource(stage.label()),
                    modifier = Modifier.padding(top = 44.dp),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight(750),
                        letterSpacing = (-1.17).sp,
                        textAlign = TextAlign.Center
                    )
                )
                Text(
                    stringResource(R.string.pairing_progress_instruction),
                    modifier = Modifier.padding(top = 10.dp).widthIn(max = 286.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

private fun PairingStage.label(): Int = when (this) {
    PairingStage.Opening -> R.string.pairing_progress_opening
    PairingStage.Identity -> R.string.pairing_progress_identity
    PairingStage.Key -> R.string.pairing_progress_key
    PairingStage.Confirm -> R.string.pairing_progress_confirm
}
