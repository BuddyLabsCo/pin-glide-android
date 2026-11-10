package com.buddylabs.pinglide.ui.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.platform.scanner.PairingCamera
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun PairingScreen(state: PairingEntryState, model: PairingEntryViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.foreground()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = model::close)
    val scanning = state.page == PairingEntryPage.Scanner
    val title = stringResource(
        if (scanning) R.string.pairing_scan_title else R.string.pairing_consent_title
    )
    ScreenLayout {
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState())
                .semantics { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = if (scanning) model::close else model::back,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painterResource(
                            if (scanning) R.drawable.ic_close else R.drawable.ic_back
                        ),
                        contentDescription = stringResource(
                            if (scanning) R.string.pairing_cancel else R.string.setup_back
                        )
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
            }
            if (scanning) {
                ScannerContent(state, model, resumed)
            } else {
                ConsentContent(state, model)
            }
            if (state.problem != null &&
                (state.problem != PairingEntryProblem.InvalidQr || !scanning)
            ) {
                PairingProblem(state.problem, model::freshScan)
            }
            TextButton(
                onClick = model::close,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.pairing_cancel))
            }
        }
    }
}

@Composable
private fun ScannerContent(
    state: PairingEntryState,
    model: PairingEntryViewModel,
    resumed: Boolean
) {
    Text(
        stringResource(R.string.pairing_scan_instruction),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(stringResource(R.string.pairing_scan_description))
    val defaultAlias = stringResource(R.string.pairing_default_alias)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resumed && !state.pending &&
            (state.problem == null || state.problem == PairingEntryProblem.InvalidQr)
        ) {
            PairingCamera(
                Modifier.matchParentSize(),
                onCode = { model.scan(it, defaultAlias) },
                onFailure = model::cameraFailed
            )
        }
        if (state.pending) CircularProgressIndicator()
    }
    if (state.problem == PairingEntryProblem.InvalidQr) {
        Text(
            stringResource(R.string.pairing_invalid_qr),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    if (state.pending) Text(stringResource(R.string.pairing_validating))
}

@Composable
private fun ConsentContent(state: PairingEntryState, model: PairingEntryViewModel) {
    Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(72.dp))
    val title = if (state.host == PairingHost.Onboarding) {
        R.string.pairing_connect_title
    } else {
        R.string.pairing_add_title
    }
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() }
    )
    Text(stringResource(R.string.pairing_consent_description))
    val aliasHelp = when (state.aliasError) {
        PairingAliasError.Blank -> R.string.home_alias_blank
        PairingAliasError.TooLong -> R.string.home_alias_too_long
        PairingAliasError.InvalidCharacters -> R.string.home_alias_invalid_characters
        PairingAliasError.Duplicate -> R.string.home_alias_duplicate
        null -> R.string.home_alias_help
    }
    OutlinedTextField(
        value = state.alias,
        onValueChange = model::editAlias,
        enabled = !state.pending && !state.confirmed && state.problem == null,
        isError = state.aliasError != null,
        label = { Text(stringResource(R.string.home_alias_label)) },
        supportingText = { Text(stringResource(aliasHelp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(
            R.string.pairing_remaining,
            state.remainingSeconds / 60,
            state.remainingSeconds % 60
        ),
        modifier = Modifier.focusable()
    )
    if (state.confirmed) {
        Text(
            stringResource(R.string.pairing_confirmed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    val aliasValid = state.aliasError == null && state.problem == null
    Button(
        onClick = model::confirm,
        enabled = !state.pending && !state.confirmed && aliasValid,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        val label = if (state.pending) R.string.pairing_validating else R.string.pairing_confirm
        Text(stringResource(label))
    }
}

@Composable
private fun PairingProblem(problem: PairingEntryProblem, onRescan: () -> Unit) {
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
    PairingEntryProblem.Camera -> R.string.pairing_camera_unavailable
}
