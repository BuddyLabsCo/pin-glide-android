package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState

@Composable
internal fun HomeSmsRecovery(pending: Boolean, onRestore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = colorResource(R.color.home_warning_container),
            contentColor = colorResource(R.color.home_on_warning),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.home_sms_hint), Modifier.padding(16.dp))
        }
        Button(
            onClick = onRestore,
            enabled = !pending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.home_sms_restore))
        }
    }
}

@Composable
internal fun SmsRecoveryNotice(
    state: HomeState,
    smsGranted: Boolean,
    recovery: SmsRecovery,
    onDismiss: (SmsRecovery) -> Unit
) {
    val canForward = state.readiness(smsGranted) == HomeReadiness.Ready &&
        !state.failed && !state.refreshing &&
        state.targets.any { it.enabled && it.keyUsable }
    val messageId = when (recovery) {
        SmsRecovery.Granted, SmsRecovery.Denied -> when {
            !smsGranted -> R.string.home_sms_denied
            canForward -> R.string.home_sms_restored
            else -> R.string.home_sms_enabled
        }

        SmsRecovery.SettingsUnavailable -> R.string.home_sms_settings_error

        SmsRecovery.Idle, SmsRecovery.Pending -> return
    }
    val message = stringResource(messageId)
    val host = remember { SnackbarHostState() }
    LaunchedEffect(recovery, message, state.refreshing) {
        if (recovery != SmsRecovery.Granted || !state.refreshing) {
            host.showSnackbar(message, duration = SnackbarDuration.Long)
            onDismiss(recovery)
        }
    }
    SnackbarHost(host, modifier = Modifier.fillMaxWidth().imePadding())
}
