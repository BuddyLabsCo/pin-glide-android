package com.buddylabs.pinglide.ui.pairing

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.pairing.PairingStage
import com.buddylabs.pinglide.ui.components.PairingTestAction
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun PairingResultScreen(
    state: PairingEntryState,
    model: PairingEntryViewModel,
    onLeave: () -> Unit
) {
    val test by model.testState.collectAsState()
    val success = state.page == PairingEntryPage.Success
    val unconfirmed = state.problem == PairingEntryProblem.Unconfirmed
    val context = LocalContext.current
    LaunchedEffect(test.announceReceived) {
        if (model.consumeTestSuccess()) {
            Toast.makeText(context, R.string.pairing_test_received, Toast.LENGTH_SHORT).show()
        }
    }
    val title = stringResource(
        when {
            success -> R.string.pairing_success
            unconfirmed -> R.string.pairing_unconfirmed_title
            state.problem != null -> R.string.pairing_failed_title
            else -> R.string.pairing_progress_title
        }
    )
    BackHandler(onBack = model::close)
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
            when {
                success -> {
                    PairingTestAction(test.pending, test.outcome, model::testMessage)
                    TextButton(
                        onClick = model::close,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.pairing_home))
                    }
                }

                state.problem != null -> {
                    Text(
                        stringResource(pairingProblemText(state.problem)),
                        modifier = Modifier.semantics {
                            liveRegion =
                                LiveRegionMode.Polite
                        }
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
                                if (unconfirmed) {
                                    R.string.pairing_recheck
                                } else {
                                    R.string.pairing_rescan
                                }
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

                else -> {
                    CircularProgressIndicator()
                    Text(
                        stringResource(state.stage.label()),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    PairingStage.entries.forEach { stage ->
                        Text(
                            stringResource(stage.label()),
                            color = if (stage ==
                                state.stage
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
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
