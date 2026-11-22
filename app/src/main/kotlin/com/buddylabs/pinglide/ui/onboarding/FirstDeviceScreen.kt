package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.RingedEmblem

@Composable
fun FirstDeviceScreen(onLeave: () -> Unit, onScan: () -> Unit) {
    CompactSetupStepScreen(
        step = 4,
        title = R.string.setup_first_device_title,
        description = R.string.setup_first_device_description,
        emblem = {
            RingedEmblem(RoundedCornerShape(44.dp)) {
                Icon(
                    painterResource(R.drawable.ic_qr),
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactSetupButton(R.string.pairing_scan_title, R.drawable.ic_qr, onScan)
            TextButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.setup_later),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.14.sp
                )
            }
        }
    }
}
