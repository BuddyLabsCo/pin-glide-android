package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R

@Composable
fun FirstDeviceScreen(onLeave: () -> Unit) {
    SetupStepScreen(
        step = 4,
        icon = R.drawable.ic_devices,
        title = R.string.setup_first_device_title,
        description = R.string.setup_first_device_description,
        onBack = onLeave
    ) {
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.setup_later))
        }
    }
}
