package com.buddylabs.pinglide.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.RevocationStep
import com.buddylabs.pinglide.domain.identity.SenderRevocation
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
    account: AccountSession,
    senders: AccountSenders,
    revocation: SenderRevocation,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Root) }
    val operation = revocation.state.collectAsState().value
    val activity = LocalActivity.current
    DisposableEffect(revocation, senders, activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                revocation.close()
                senders.close()
            }
        }
    }
    LaunchedEffect(destination) {
        if (destination != SettingsDestination.Account) account.close()
        if (destination != SettingsDestination.DeviceAccess) {
            revocation.close()
            senders.close()
        }
    }
    val stateHolder = rememberSaveableStateHolder()
    val back = {
        if (destination == SettingsDestination.DeviceAccess && operation != null) {
            revocation.dismiss(operation)
        } else if (destination == SettingsDestination.Root) {
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
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        val lightBars = !isSystemInDarkTheme()
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
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
                },
                account = account,
                senders = senders,
                revocation = revocation,
                onLogout = onLogout
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
    onLanguage: (AppLanguage) -> Unit,
    account: AccountSession,
    senders: AccountSenders,
    revocation: SenderRevocation,
    onLogout: () -> Unit
) {
    val operation by revocation.state.collectAsState()
    if (destination == SettingsDestination.DeviceAccess && operation != null &&
        operation?.step != RevocationStep.Confirmation
    ) {
        SenderRevocationScreen(checkNotNull(operation), revocation)
        return
    }
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
            SettingsDestination.Account -> AccountScreen(account, onLogout)
            SettingsDestination.DeviceAccess -> SenderInventoryScreen(senders, revocation)
        }
    }
}

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    backModifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    rootLayout: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    ScreenLayout {
        val headerModifier = if (rootLayout) {
            Modifier.padding(top = 16.dp).heightIn(min = 64.dp)
        } else {
            Modifier.padding(vertical = 8.dp)
        }
        Row(
            Modifier.fillMaxWidth().then(headerModifier).semantics { paneTitle = title },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (rootLayout) 12.dp else 0.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = if (rootLayout) backModifier.size(48.dp) else backModifier,
                enabled = backEnabled
            ) {
                Icon(
                    painterResource(
                        if (rootLayout) R.drawable.ic_back_chevron else R.drawable.ic_back
                    ),
                    stringResource(R.string.setup_back),
                    tint = if (rootLayout) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        LocalContentColor.current
                    }
                )
            }
            Text(
                title,
                style = if (rootLayout) {
                    LocalTextStyle.current.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.7).sp
                    )
                } else {
                    MaterialTheme.typography.titleLarge
                },
                modifier = Modifier.semantics { heading() }
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(
                top = if (rootLayout) 20.dp else 16.dp,
                bottom = if (rootLayout) 28.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (rootLayout) 20.dp else 16.dp),
            content = content
        )
    }
}
