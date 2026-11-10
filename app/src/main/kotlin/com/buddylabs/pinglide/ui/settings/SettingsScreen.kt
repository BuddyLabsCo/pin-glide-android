package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.settings.AppLanguage

@Composable
internal fun SettingsScreen(
    language: AppLanguage,
    installedVersion: String,
    onBatterySettings: () -> Boolean,
    onNavigate: (SettingsDestination) -> Unit
) {
    var focused by rememberSaveable { mutableStateOf(0) }
    var batteryError by rememberSaveable { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val focus: (Int) -> Modifier = { label ->
        Modifier.then(if (focused == label) Modifier.focusRequester(rowFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) focused = label }
    }
    LaunchedEffect(Unit) { if (focused != 0) rowFocus.requestFocus() }
    SettingsGroupTitle(stringResource(R.string.settings_account_group))
    SettingsAction(
        stringResource(R.string.settings_account),
        R.drawable.ic_qq,
        modifier = focus(R.string.settings_account)
    ) {
        focused = R.string.settings_account
        onNavigate(SettingsDestination.Account)
    }
    SettingsGroupTitle(stringResource(R.string.settings_display))
    SettingsAction(
        stringResource(R.string.settings_language),
        R.drawable.ic_language,
        value = languageLabel(language),
        modifier = focus(R.string.settings_language)
    ) {
        focused = R.string.settings_language
        onNavigate(SettingsDestination.Language)
    }
    SettingsInformation(
        stringResource(R.string.settings_appearance),
        stringResource(R.string.settings_follow_system)
    )
    SettingsGroupTitle(stringResource(R.string.settings_background))
    SettingsAction(
        stringResource(R.string.settings_battery),
        R.drawable.ic_battery,
        description = stringResource(R.string.settings_battery_subtitle),
        modifier = focus(R.string.settings_battery)
    ) {
        focused = R.string.settings_battery
        batteryError = !onBatterySettings()
    }
    if (batteryError) {
        Text(
            stringResource(R.string.setup_settings_unavailable),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    SettingsGroupTitle(stringResource(R.string.settings_security))
    SettingsAction(
        stringResource(R.string.settings_device_access),
        R.drawable.ic_shield,
        modifier = focus(R.string.settings_device_access)
    ) {
        focused = R.string.settings_device_access
        onNavigate(SettingsDestination.DeviceAccess)
    }
    SettingsGroupTitle(stringResource(R.string.settings_about))
    SettingsInformation(stringResource(R.string.app_name), installedVersion)
    SettingsAction(
        stringResource(R.string.privacy_title),
        R.drawable.ic_lock,
        modifier = focus(R.string.privacy_title)
    ) {
        focused = R.string.privacy_title
        onNavigate(SettingsDestination.Privacy)
    }
}

@Composable
internal fun SettingsGroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun SettingsAction(
    title: String,
    icon: Int,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    Modifier.widthIn(max = 140.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SettingsInformation(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f))
            Text(
                value,
                Modifier.widthIn(max = 140.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
