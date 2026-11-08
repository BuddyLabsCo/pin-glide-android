package com.buddylabs.pinglide.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.ui.components.PrivacyScreen
import com.buddylabs.pinglide.ui.onboarding.SetupEntryScreen
import com.buddylabs.pinglide.ui.onboarding.WelcomeScreen

@Composable
fun SetupNavigation(progress: SetupProgress, onBackground: () -> Unit) {
    var started by remember { mutableStateOf(progress.started) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBackground)
    if (started) {
        SetupEntryScreen()
    } else {
        WelcomeScreen(
            onStartSetup = {
                progress.start()
                started = true
            },
            onPrivacy = { privacyOpen = true }
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
