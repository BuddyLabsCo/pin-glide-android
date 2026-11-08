package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun SetupEntryScreen() {
    val title = stringResource(R.string.setup_sms_title)
    ScreenLayout {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .semantics { paneTitle = title }.padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(stringResource(R.string.setup_step), style = MaterialTheme.typography.labelLarge)
            Icon(
                painterResource(R.drawable.ic_sms),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            Text(
                stringResource(R.string.setup_sms_description),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
