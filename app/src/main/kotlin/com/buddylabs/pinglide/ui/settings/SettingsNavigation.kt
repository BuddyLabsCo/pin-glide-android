package com.buddylabs.pinglide.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PrivacyScreen
import com.buddylabs.pinglide.ui.components.ScreenLayout

internal enum class SettingsDestination { Root, Language, Privacy, Account, DeviceAccess }

@Composable
fun SettingsNavigation(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onBatterySettings: () -> Boolean,
    installedVersion: String,
    onDismiss: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Root) }
    val stateHolder = rememberSaveableStateHolder()
    val back = {
        if (destination == SettingsDestination.Root) {
            onDismiss()
        } else {
            destination = SettingsDestination.Root
        }
    }
    BackHandler(onBack = back)
    Dialog(
        onDismissRequest = back,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        stateHolder.SaveableStateProvider(destination.name) {
            SettingsDestinationContent(
                destination,
                language,
                installedVersion,
                onBatterySettings,
                back,
                onNavigate = { destination = it },
                onLanguage = {
                    destination = SettingsDestination.Root
                    onLanguage(it)
                }
            )
        }
    }
}

@Composable
private fun SettingsDestinationContent(
    destination: SettingsDestination,
    language: AppLanguage,
    installedVersion: String,
    onBatterySettings: () -> Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onLanguage: (AppLanguage) -> Unit
) {
    if (destination == SettingsDestination.Root) {
        SettingsScreen(language, installedVersion, onBatterySettings, onNavigate, onBack)
        return
    }
    if (destination == SettingsDestination.Privacy) {
        PrivacyScreen(onDismiss = onBack)
        return
    }
    val title = when (destination) {
        SettingsDestination.Language -> R.string.settings_language
        SettingsDestination.Account -> R.string.settings_account
        SettingsDestination.DeviceAccess -> R.string.settings_device_access
    }
    SettingsPage(stringResource(title), onBack) {
        when (destination) {
            SettingsDestination.Language -> LanguageScreen(language, onLanguage)

            SettingsDestination.Account -> Text(stringResource(R.string.settings_account))

            SettingsDestination.DeviceAccess -> Text(
                stringResource(R.string.settings_device_access_explanation)
            )
        }
    }
}

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    backModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ScreenLayout {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { paneTitle = title },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = backModifier) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.setup_back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}
