package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R

@Composable
internal fun BatterySetupScreen(onSettings: () -> Boolean, onNext: () -> Unit) {
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }
    CompactSetupStepScreen(
        step = 2,
        title = R.string.setup_battery_title,
        description = R.string.setup_battery_description,
        emblem = {
            RingedSetupEmblem(RoundedCornerShape(44.dp)) {
                Icon(
                    painterResource(R.drawable.ic_battery),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsError(settingsUnavailable)
            CompactSetupButton(
                R.string.setup_open_battery_settings,
                R.drawable.ic_settings_filled,
                onClick = { settingsUnavailable = !onSettings() }
            )
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.setup_next),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.14.sp
                )
            }
        }
    }
}
