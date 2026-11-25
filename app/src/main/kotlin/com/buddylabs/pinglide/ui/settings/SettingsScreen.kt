package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.settings.AppLanguage

@Composable
internal fun SettingsScreen(
    language: AppLanguage,
    installedVersion: String,
    onBatterySettings: () -> Boolean,
    onNavigate: (SettingsDestination) -> Unit,
    onBack: () -> Unit
) {
    var focused by rememberSaveable { mutableStateOf(0) }
    var batteryError by rememberSaveable { mutableStateOf(false) }
    val controlFocus = remember { FocusRequester() }
    val focus: (Int) -> Modifier = { label ->
        Modifier.then(if (focused == label) Modifier.focusRequester(controlFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) focused = label }
    }
    LaunchedEffect(Unit) { if (focused != 0) controlFocus.requestFocus() }
    SettingsPage(
        stringResource(R.string.settings_title),
        onBack,
        focus(R.string.setup_back),
        rootLayout = true
    ) {
        SettingsGroup(stringResource(R.string.settings_account_group), account = true) {
            SettingsAccountRow(focus(R.string.settings_account)) {
                focused = R.string.settings_account
                onNavigate(SettingsDestination.Account)
            }
        }
        SettingsGroup(stringResource(R.string.settings_display)) {
            SettingsRow(
                stringResource(R.string.settings_language),
                R.drawable.ic_language,
                value = languageLabel(language),
                modifier = focus(R.string.settings_language)
            ) {
                focused = R.string.settings_language
                onNavigate(SettingsDestination.Language)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                stringResource(R.string.settings_appearance),
                R.drawable.ic_theme,
                value = stringResource(R.string.settings_follow_system)
            )
        }
        SettingsGroup(stringResource(R.string.settings_background)) {
            SettingsRow(
                stringResource(R.string.settings_battery),
                R.drawable.ic_battery,
                description = stringResource(R.string.settings_battery_subtitle),
                modifier = focus(R.string.settings_battery)
            ) {
                focused = R.string.settings_battery
                batteryError = !onBatterySettings()
            }
        }
        if (batteryError) {
            Text(
                stringResource(R.string.setup_settings_unavailable),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        SettingsGroup(stringResource(R.string.settings_security)) {
            SettingsRow(
                stringResource(R.string.settings_device_access),
                R.drawable.ic_shield,
                modifier = focus(R.string.settings_device_access)
            ) {
                focused = R.string.settings_device_access
                onNavigate(SettingsDestination.DeviceAccess)
            }
        }
        SettingsGroup(stringResource(R.string.settings_about)) {
            SettingsRow(
                stringResource(R.string.app_name),
                R.drawable.ic_info,
                value = installedVersion
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                stringResource(R.string.settings_privacy),
                R.drawable.ic_lock,
                modifier = focus(R.string.settings_privacy)
            ) {
                focused = R.string.settings_privacy
                onNavigate(SettingsDestination.Privacy)
            }
        }
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    account: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (account) 10.dp else 14.dp)) {
        Text(
            title,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp).semantics { heading() }
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("settings-group:$title"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier, content = content)
        }
    }
}

@Composable
private fun SettingsAccountRow(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier.size(46.dp).background(Color(0xFF0B7AC8), RoundedCornerShape(15.dp))
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text("QQ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            stringResource(R.string.settings_account),
            Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Icon(
            painterResource(R.drawable.ic_chevron),
            null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: Int,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    onClick: (() -> Unit)? = null
) {
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val action = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    SettingsListRow(
        title = title,
        modifier = modifier.then(action),
        description = description,
        leading = {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = secondaryColor)
        }
    ) {
        if (value != null) {
            Text(
                value,
                Modifier.widthIn(max = 140.dp),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = secondaryColor
            )
        } else if (onClick != null) {
            Icon(
                painterResource(R.drawable.ic_chevron),
                null,
                Modifier.size(20.dp),
                tint = secondaryColor
            )
        }
    }
}

@Composable
internal fun SettingsListRow(
    title: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 60.dp,
    description: String? = null,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(width = 34.dp, height = 20.dp)) {
            leading()
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
                letterSpacing = 0.sp
            )
            if (description != null) {
                Text(
                    description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}
