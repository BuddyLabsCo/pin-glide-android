package com.buddylabs.pinglide.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.ui.home.HomeLoginScreen
import com.buddylabs.pinglide.ui.home.HomeScreen
import com.buddylabs.pinglide.ui.home.HomeViewModel
import com.buddylabs.pinglide.ui.home.RegistrationRevokedScreen
import com.buddylabs.pinglide.ui.home.SmsRecovery
import com.buddylabs.pinglide.ui.onboarding.FirstDeviceScreen
import com.buddylabs.pinglide.ui.pairing.PairingCameraGate
import com.buddylabs.pinglide.ui.pairing.PairingEntryViewModel
import com.buddylabs.pinglide.ui.pairing.PairingScreen
import com.buddylabs.pinglide.ui.pairing.pairingProblemText

@Composable
internal fun AcceptedNavigation(
    progress: SetupProgress,
    smsGranted: Boolean,
    onCheckSms: () -> Boolean,
    loginState: LoginState,
    home: HomeViewModel,
    pairing: PairingEntryViewModel,
    onRestoreSms: () -> Unit,
    onBackground: () -> Unit,
    onNewRegistration: () -> Unit,
    settings: @Composable (onDismiss: () -> Unit) -> Unit,
    setup: @Composable () -> Unit
) {
    LaunchedEffect(loginState) {
        if (loginState != LoginState.FirstDevice) pairing.abandon()
    }
    if (loginState == LoginState.Revoked) {
        BackHandler(onBack = onBackground)
        RegistrationRevokedScreen {
            progress.start()
            onNewRegistration()
        }
        return
    }
    if (loginState != LoginState.FirstDevice) {
        setup()
        return
    }
    val state by home.state.collectAsState()
    val smsRecovery by home.smsRecovery.collectAsState()
    var completed by remember { mutableStateOf(progress.state.completed) }
    val entry by pairing.state.collectAsState()
    val revoked = state.authorization == AuthorizationState.RegistrationRevoked
    LaunchedEffect(Unit) { home.refresh() }
    val completeEntry = {
        if (onCheckSms()) {
            progress.complete()
            completed = true
        }
    }
    PairingEntryNotices(entry, state, pairing, completeEntry)
    val onboarding = !completed && !revoked
    PairingCompletion(entry, state, completeEntry)
    if (onboarding && !smsGranted) {
        setup()
        return
    }
    val leaveEntry = {
        pairing.close()
        completeEntry()
    }
    val cameraHost = if (onboarding) PairingHost.Onboarding else PairingHost.Home
    key(cameraHost) {
        PairingCameraGate(pairing, state, cameraHost) { onScan ->
            when {
                state.needsPairingLogin(onboarding) -> PairingLoginRecovery(home, onBackground)

                entry.page != PairingEntryPage.Idle && !revoked ->
                    PairingScreen(entry, pairing, onLeave = leaveEntry)

                onboarding -> {
                    BackHandler(onBack = leaveEntry)
                    FirstDeviceScreen(onLeave = leaveEntry, onScan = onScan)
                }

                else -> {
                    BackHandler(onBack = onBackground)
                    HomeDestination(
                        state,
                        smsGranted,
                        smsRecovery,
                        home,
                        onRestoreSms,
                        onAdd = {
                            home.beginPairing()
                            onScan()
                        },
                        highlightedPairingId = entry.duplicatePairingId,
                        settings = settings
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingCompletion(entry: PairingEntryState, state: HomeState, complete: () -> Unit) {
    LaunchedEffect(entry.activatedPairingId, state.targets) {
        if (entry.activatedPairingId != null || state.targets.any { it.keyUsable }) {
            complete()
        }
    }
}

private fun HomeState.needsPairingLogin(onboarding: Boolean): Boolean = onboarding &&
    (
        authorization == AuthorizationState.LoginRequired ||
            authorization == AuthorizationState.RecoveryRequired
        )

@Composable
private fun PairingEntryNotices(
    entry: PairingEntryState,
    state: HomeState,
    pairing: PairingEntryViewModel,
    onDuplicate: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(state.authorization, state.owner, state.generation) {
        if (state.authorization == AuthorizationState.RegistrationRevoked ||
            state.authorization == AuthorizationState.LoginRequired ||
            state.authorization == AuthorizationState.RecoveryRequired
        ) {
            pairing.abandon()
        } else {
            pairing.foreground(state)
        }
    }
    LaunchedEffect(entry.highlightUntil) {
        if (entry.duplicatePairingId != null) {
            onDuplicate()
            Toast.makeText(context, R.string.pairing_already_added, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(entry.page, entry.problem) {
        if (entry.page == PairingEntryPage.Idle && entry.problem != null) {
            Toast.makeText(
                context,
                pairingProblemText(checkNotNull(entry.problem)),
                Toast.LENGTH_LONG
            ).show()
            pairing.dismissNotice()
        }
    }
}

@Composable
private fun HomeDestination(
    state: HomeState,
    smsGranted: Boolean,
    smsRecovery: SmsRecovery,
    home: HomeViewModel,
    onRestoreSms: () -> Unit,
    onAdd: () -> Unit,
    highlightedPairingId: String?,
    settings: @Composable (onDismiss: () -> Unit) -> Unit
) {
    var settingsOpen by rememberSaveable(state.owner) { mutableStateOf(false) }
    val restoration by home.loginRestoration.collectAsState()
    when (restoration) {
        RestorationState.Loading, RestorationState.Cancelled,
        RestorationState.Error, RestorationState.AccountMismatch -> HomeLoginScreen(
            restoration,
            onRetry = home::restoreLogin,
            onCancel = home::cancelLogin,
            onBack = home::leaveLogin
        )

        RestorationState.Idle, RestorationState.Restored -> HomeScreen(
            state,
            smsGranted,
            smsRecovery,
            home = home,
            onRestoreSms = onRestoreSms,
            onAdd = onAdd,
            highlightedPairingId = highlightedPairingId,
            onSettings = { settingsOpen = true }
        )
    }
    if (settingsOpen && state.authorization != AuthorizationState.RegistrationRevoked) {
        settings { settingsOpen = false }
    }
}
