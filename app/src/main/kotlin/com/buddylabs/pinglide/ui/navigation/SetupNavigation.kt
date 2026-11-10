package com.buddylabs.pinglide.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.identity.LogoutState
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState
import com.buddylabs.pinglide.ui.components.PrivacyScreen
import com.buddylabs.pinglide.ui.onboarding.BatterySetupScreen
import com.buddylabs.pinglide.ui.onboarding.QqLoginScreen
import com.buddylabs.pinglide.ui.onboarding.SmsPermissionScreen
import com.buddylabs.pinglide.ui.onboarding.WelcomeScreen

private enum class SetupDestination { Welcome, Sms, SmsDenied, Battery, QqLogin }

private fun SetupState.destination(
    smsGranted: Boolean,
    smsRequestPending: Boolean,
    identityAccepted: Boolean,
    registrationClosed: Boolean
): SetupDestination = when {
    registrationClosed -> SetupDestination.Welcome
    !started && !identityAccepted -> SetupDestination.Welcome
    !smsGranted && smsRequestPending -> SetupDestination.Sms
    !smsGranted && (smsRequested || smsCompleted || identityAccepted) -> SetupDestination.SmsDenied
    !smsGranted -> SetupDestination.Sms
    !batteryAcknowledged && !identityAccepted -> SetupDestination.Battery
    else -> SetupDestination.QqLogin
}

@Composable
fun SetupNavigation(
    progress: SetupProgress,
    smsGranted: Boolean,
    smsRequestPending: Boolean,
    onRequestSms: () -> Unit,
    onCheckSms: () -> Boolean,
    onApplicationSettings: () -> Boolean,
    onBatterySettings: () -> Boolean,
    loginState: LoginState,
    onLogin: () -> Unit,
    onNewAccount: () -> Unit,
    logoutState: LogoutState,
    onRetryLogout: () -> Unit,
    onCancelLogin: () -> Unit,
    onRetryIdentity: () -> Unit,
    onNewRegistration: () -> Unit,
    onBackground: () -> Unit
) {
    var setup by remember { mutableStateOf(progress.state) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(smsGranted, setup.started) {
        if (smsGranted && setup.started && !setup.smsCompleted) {
            progress.completeSmsPrerequisite()
            setup = progress.state
        }
    }
    val destination = setup.destination(
        smsGranted,
        smsRequestPending,
        loginState == LoginState.FirstDevice,
        loginState == LoginState.Welcome
    )
    val leaveLogin = {
        if (loginState in
            setOf(LoginState.Loading, LoginState.Error, LoginState.RestorationError)
        ) {
            onCancelLogin()
        } else {
            onBackground()
        }
    }
    LaunchedEffect(destination) {
        if (destination != SetupDestination.QqLogin &&
            loginState == LoginState.Loading
        ) {
            onCancelLogin()
        }
    }
    BackHandler(onBack = if (destination == SetupDestination.QqLogin) leaveLogin else onBackground)
    when (destination) {
        SetupDestination.Welcome -> WelcomeScreen(
            onStartSetup = {
                if (logoutState == LogoutState.Ready) {
                    onNewRegistration()
                    progress.start()
                    setup = progress.state
                }
            },
            onPrivacy = { privacyOpen = true },
            logoutState = logoutState,
            onRetryLogout = onRetryLogout
        )

        SetupDestination.Sms, SetupDestination.SmsDenied -> SmsPermissionScreen(
            denied = destination == SetupDestination.SmsDenied,
            requestPending = smsRequestPending,
            onRequest = {
                progress.recordSmsRequest()
                setup = progress.state
                onRequestSms()
            },
            onSettings = onApplicationSettings,
            onCheckAgain = onCheckSms,
            onBack = onBackground
        )

        SetupDestination.Battery -> BatterySetupScreen(
            onSettings = onBatterySettings,
            onNext = {
                onCheckSms()
                progress.acknowledgeBattery()
                setup = progress.state
            },
            onBack = onBackground
        )

        SetupDestination.QqLogin -> QqLoginScreen(
            state = loginState,
            onLogin = onLogin,
            onNewAccount = onNewAccount,
            onCancel = onCancelLogin,
            onRetryStorage = onRetryIdentity,
            onBack = leaveLogin
        )
    }
    if (privacyOpen) {
        Dialog(
            onDismissRequest = { privacyOpen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            PrivacyScreen(onDismiss = { privacyOpen = false })
        }
    }
}
