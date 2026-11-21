package com.buddylabs.pinglide.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
        compact = !denied,
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.setup_allow_sms),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.14.sp
                )
            }
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
internal fun SetupStepScreen(
    step: Int,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes description: Int,
    onBack: () -> Unit,
    error: Boolean = false,
    compact: Boolean = false,
    emblem: (@Composable () -> Unit)? = null,
    notice: @Composable () -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit
) {
    val pane = stringResource(title)
    ScreenLayout {
        val screenInsets = if (compact) {
            Modifier.windowInsetsPadding(WindowInsets(top = 32.dp, bottom = 22.dp))
        } else {
            Modifier
        }
        BoxWithConstraints(Modifier.weight(1f).then(screenInsets)) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).semantics { paneTitle = pane }
            ) {
                if (compact) CompactSetupStepHeader(step) else SetupStepHeader(step, onBack)
                Spacer(Modifier.weight(1f))
                SetupStepContent(pane, description, icon, error, compact, emblem, notice)
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        vertical = if (compact) 0.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun SetupStepContent(
    title: String,
    @StringRes description: Int,
    @DrawableRes icon: Int,
    error: Boolean,
    compact: Boolean,
    emblem: (@Composable () -> Unit)?,
    notice: @Composable () -> Unit
) {
    val contentPadding = if (compact) {
        PaddingValues(start = 2.dp, top = 12.dp, end = 2.dp, bottom = 62.dp)
    } else {
        PaddingValues(vertical = 32.dp)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 20.dp)
    ) {
        when {
            emblem != null -> emblem()
            compact -> RingedSetupEmblem(icon)
            else -> SetupEmblem(icon, error)
        }
        Text(
            title,
            style = if (compact) {
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight(750),
                    letterSpacing = (-0.045).em
                )
            } else {
                MaterialTheme.typography.headlineLarge
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (compact) 7.dp else 0.dp).semantics { heading() }
        )
        Text(
            stringResource(description),
            style = if (compact) {
                MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.sp)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            textAlign = TextAlign.Center,
            modifier = if (compact) {
                Modifier.padding(top = 12.dp).widthIn(max = 305.dp)
            } else {
                Modifier
            }
        )
        notice()
    }
}

@Composable
private fun CompactSetupStepHeader(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        SetupBrandIcon(Modifier.size(34.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.setup_title),
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight(740),
                letterSpacing = (-0.035).em
            )
            Text(
                stringResource(R.string.setup_step_fraction, step),
                modifier = Modifier.clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(650),
                letterSpacing = 0.08.em
            )
        }
        SetupStepIndicator(step, Modifier.widthIn(max = 130.dp))
    }
}

@Composable
private fun SetupStepHeader(step: Int, onBack: () -> Unit) {
    val setupTitle = stringResource(R.string.setup_title)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.setup_back)
                )
            }
            Image(
                painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(setupTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.setup_step, step),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SetupStepIndicator(step, Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun SetupStepIndicator(step: Int, modifier: Modifier) {
    val label = stringResource(R.string.setup_step, step)
    Row(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..4f, 3)
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(4) { index ->
            Box(
                Modifier.weight(1f).height(4.dp).background(
                    if (index < step) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    CircleShape
                )
            )
        }
    }
}

@Composable
private fun RingedSetupEmblem(@DrawableRes icon: Int) {
    val colors = MaterialTheme.colorScheme
    val ring = colors.primary.copy(alpha = 0.22f)
    Box(
        modifier = Modifier.size(222.dp).border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(186.dp).border(1.dp, ring, CircleShape))
        Box(
            modifier = Modifier.size(148.dp).background(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)),
                RoundedCornerShape(44.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, Modifier.size(54.dp), colors.onPrimaryContainer)
        }
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
