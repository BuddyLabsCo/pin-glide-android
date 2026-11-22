package com.buddylabs.pinglide.ui.home

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.ui.components.WarningInfoCard

@Composable
internal fun HomeSmsRecovery(state: HomeState, pending: Boolean, onRestore: () -> Unit) {
    val hint = if (state.listed && state.isReadyWithSmsPermission &&
        state.hasConfirmedOnlineTarget
    ) {
        R.string.home_sms_resume_hint
    } else {
        R.string.home_sms_hint
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WarningInfoCard(hint, icon = R.drawable.ic_warning)
        Button(
            onClick = onRestore,
            enabled = !pending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            HomeActionLabel(R.drawable.ic_settings_filled, R.string.home_sms_restore)
        }
    }
}

@Composable
internal fun HomeActionLabel(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun SmsRecoveryNotice(
    state: HomeState,
    smsGranted: Boolean,
    recovery: SmsRecovery,
    onDismiss: (SmsRecovery) -> Unit
) {
    val messageId = when (recovery) {
        SmsRecovery.Granted, SmsRecovery.Denied -> when {
            !smsGranted -> R.string.home_sms_denied
            !state.refreshing && state.isReadyWithSmsPermission -> R.string.home_sms_restored
            else -> R.string.home_sms_enabled
        }

        SmsRecovery.SettingsUnavailable -> R.string.home_sms_settings_error

        SmsRecovery.Idle, SmsRecovery.Pending -> return
    }
    val message = stringResource(messageId)
    val context = LocalContext.current
    LaunchedEffect(recovery, message, state.refreshing) {
        if (recovery != SmsRecovery.Granted || !state.refreshing) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onDismiss(recovery)
        }
    }
}

private val HomeState.isReadyWithSmsPermission: Boolean
    get() = readiness(smsGranted = true) == HomeReadiness.Ready && !failed

private val HomeState.hasConfirmedOnlineTarget: Boolean
    get() = targets.any { target ->
        val presence = if (refreshing) target.lastConfirmedPresence else target.presence
        target.enabled && target.keyUsable && presence == Presence.Online
    }
