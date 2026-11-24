package com.buddylabs.pinglide.ui.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState

@Composable
fun PairingScreen(state: PairingEntryState, model: PairingEntryViewModel, onLeave: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.foreground()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (state.page == PairingEntryPage.Progress || state.page == PairingEntryPage.Success) {
        PairingResultScreen(state, model, onLeave)
        return
    }
    val lifecycleState by lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    BackHandler(onBack = model::close)
    if (state.page == PairingEntryPage.Scanner) {
        PairingScannerScreen(state, model, resumed)
        return
    }
    PairingConsentScreen(
        state = state,
        onBack = model::back,
        onCancel = model::close,
        onAliasChange = model::editAlias,
        onConfirm = model::confirm,
        onRescan = model::freshScan
    )
}

@Composable
internal fun PairingProblem(problem: PairingEntryProblem, onRescan: () -> Unit) {
    Text(
        stringResource(pairingProblemText(problem)),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    Button(onClick = onRescan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.pairing_rescan))
    }
}

internal fun pairingProblemText(problem: PairingEntryProblem): Int = when (problem) {
    PairingEntryProblem.InvalidQr -> R.string.pairing_invalid_qr
    PairingEntryProblem.Closed -> R.string.pairing_session_closed
    PairingEntryProblem.Unsupported -> R.string.pairing_unsupported
    PairingEntryProblem.Capacity -> R.string.pairing_capacity
    PairingEntryProblem.Unavailable -> R.string.pairing_unavailable
    PairingEntryProblem.Unconfirmed -> R.string.pairing_unconfirmed
    PairingEntryProblem.Camera -> R.string.pairing_camera_unavailable
}
