package com.buddylabs.pinglide.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun SmsPermissionScreen(
    denied: Boolean,
    requestPending: Boolean,
    onRequest: () -> Unit,
    onSettings: () -> Boolean,
    onCheckAgain: () -> Boolean,
    onBack: () -> Unit
) {
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }
    var permissionStillDenied by rememberSaveable { mutableStateOf(false) }
    SetupStepScreen(
        step = 1,
        icon = R.drawable.ic_sms,
        title = if (denied) R.string.setup_sms_denied_title else R.string.setup_sms_title,
        description = if (denied) {
            R.string.setup_sms_denied_description
        } else {
            R.string.setup_sms_description
        },
        onBack = onBack,
        error = denied,
        notice = {
            if (denied) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                        Text(stringResource(R.string.setup_sms_denied_hint))
                    }
                }
            }
        }
    ) {
        SettingsError(settingsUnavailable)
        if (permissionStillDenied) {
            Text(
                stringResource(R.string.setup_sms_still_denied),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (denied) {
            Button(
                onClick = { settingsUnavailable = !onSettings() },
                enabled = !requestPending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(stringResource(R.string.setup_open_application_settings)) }
            TextButton(
                onClick = {
                    settingsUnavailable = false
                    permissionStillDenied = !onCheckAgain()
                },
                enabled = !requestPending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.setup_check_again)) }
        } else {
            Button(
                onClick = onRequest,
                enabled = !requestPending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(stringResource(R.string.setup_allow_sms)) }
        }
    }
}

@Composable
fun BatterySetupScreen(onSettings: () -> Boolean, onNext: () -> Unit, onBack: () -> Unit) {
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }
    SetupStepScreen(
        step = 2,
        icon = R.drawable.ic_battery,
        title = R.string.setup_battery_title,
        description = R.string.setup_battery_description,
        onBack = onBack
    ) {
        SettingsError(settingsUnavailable)
        Button(
            onClick = { settingsUnavailable = !onSettings() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(stringResource(R.string.setup_open_battery_settings)) }
        OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.setup_next))
        }
    }
}

@Composable
fun QqLoginEntryScreen(onBack: () -> Unit) {
    SetupStepScreen(
        step = 3,
        icon = R.drawable.ic_account,
        title = R.string.setup_qq_title,
        description = R.string.setup_qq_description,
        onBack = onBack
    ) {}
}

@Composable
private fun SettingsError(visible: Boolean) {
    if (visible) {
        Text(
            stringResource(R.string.setup_settings_unavailable),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun SetupStepScreen(
    step: Int,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    onBack: () -> Unit,
    error: Boolean = false,
    notice: @Composable () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit
) {
    val pane = stringResource(title)
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).semantics { paneTitle = pane }
            ) {
                SetupStepHeader(step, onBack)
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SetupEmblem(icon, error)
                    Text(
                        pane,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    notice()
                }
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun SetupStepHeader(step: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.setup_back)
            )
        }
        Text(
            stringResource(R.string.setup_step, step),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(end = 48.dp)
        )
    }
}

@Composable
private fun SetupEmblem(@DrawableRes icon: Int, error: Boolean) {
    Surface(
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = CircleShape
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(24.dp).size(40.dp)
        )
    }
}
