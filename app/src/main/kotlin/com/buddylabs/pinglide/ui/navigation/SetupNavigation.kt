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
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState
import com.buddylabs.pinglide.ui.components.PrivacyScreen
import com.buddylabs.pinglide.ui.onboarding.BatterySetupScreen
import com.buddylabs.pinglide.ui.onboarding.QqLoginEntryScreen
import com.buddylabs.pinglide.ui.onboarding.SmsPermissionScreen
import com.buddylabs.pinglide.ui.onboarding.WelcomeScreen

private enum class SetupDestination { Welcome, Sms, SmsDenied, Battery, QqLogin }

private fun SetupState.destination(smsGranted: Boolean): SetupDestination = when {
    !started -> SetupDestination.Welcome
    !smsGranted && (smsRequested || smsCompleted) -> SetupDestination.SmsDenied
    !smsGranted -> SetupDestination.Sms
    !batteryAcknowledged -> SetupDestination.Battery
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
    BackHandler(onBack = onBackground)
    when (setup.destination(smsGranted)) {
        SetupDestination.Welcome -> WelcomeScreen(
            onStartSetup = {
                progress.start()
                setup = progress.state
            },
            onPrivacy = { privacyOpen = true }
        )

        SetupDestination.Sms, SetupDestination.SmsDenied -> SmsPermissionScreen(
            denied = setup.destination(smsGranted) == SetupDestination.SmsDenied,
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

        SetupDestination.QqLogin -> QqLoginEntryScreen(onBack = onBackground)
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
