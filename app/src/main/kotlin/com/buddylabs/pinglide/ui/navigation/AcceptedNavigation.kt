package com.buddylabs.pinglide.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.ui.home.HomeScreen
import com.buddylabs.pinglide.ui.home.HomeViewModel
import com.buddylabs.pinglide.ui.onboarding.FirstDeviceScreen

@Composable
internal fun AcceptedNavigation(
    progress: SetupProgress,
    smsGranted: Boolean,
    onCheckSms: () -> Boolean,
    loginState: LoginState,
    home: HomeViewModel,
    onBackground: () -> Unit,
    setup: @Composable () -> Unit
) {
    if (loginState != LoginState.FirstDevice) {
        setup()
        return
    }
    val state by home.state.collectAsState()
    var completed by remember { mutableStateOf(progress.state.completed) }
    var pairingEntryOpen by remember { mutableStateOf(false) }
    val revoked = state.authorization == AuthorizationState.RegistrationRevoked
    LaunchedEffect(Unit) { home.refresh() }
    if (!completed && !smsGranted && !revoked) {
        setup()
        return
    }
    val leaveEntry = {
        if (completed) {
            pairingEntryOpen = false
        } else if (onCheckSms()) {
            progress.complete()
            completed = true
        }
    }
    if ((!completed || pairingEntryOpen) && !revoked) {
        BackHandler(onBack = leaveEntry)
        FirstDeviceScreen(onLeave = leaveEntry)
    } else {
        BackHandler(onBack = onBackground)
        HomeScreen(
            state,
            smsGranted,
            onRefresh = home::refresh,
            onSelect = { pairingId, enabled -> home.select(state, pairingId, enabled) },
            onAdd = { pairingEntryOpen = true }
        )
    }
}
