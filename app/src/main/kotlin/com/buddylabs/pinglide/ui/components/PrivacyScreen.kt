package com.buddylabs.pinglide.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R

@Composable
fun PrivacyScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val title = stringResource(R.string.privacy_title)
    ScreenLayout {
        Row(
            modifier = Modifier.fillMaxWidth().semantics {
                paneTitle = title
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                }
            )
        }
        Column(
            modifier = Modifier.weight(
                1f
            ).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PrivacyContent()
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 56.dp)
        ) {
            Text(stringResource(R.string.privacy_dismiss))
        }
    }
}

@Composable
fun PrivacyContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.privacy_heading),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
    }
    PrivacySection(
        R.drawable.ic_sms,
        R.string.privacy_processing_title,
        R.string.privacy_processing_body
    )
    PrivacySection(R.drawable.ic_key, R.string.privacy_keys_title, R.string.privacy_keys_body)
    PrivacySection(
        R.drawable.ic_devices,
        R.string.privacy_routing_title,
        R.string.privacy_routing_body
    )
    PrivacySection(
        R.drawable.ic_info,
        R.string.privacy_diagnostics_title,
        R.string.privacy_diagnostics_body
    )
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            PrivacySection(
                R.drawable.ic_shield,
                R.string.privacy_backup_title,
                R.string.privacy_backup_body
            )
        }
    }
}

@Composable
private fun PrivacySection(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
