package com.buddylabs.pinglide.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R

@Composable
fun PrivacyScreen(onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val title = stringResource(R.string.privacy_title)
    ScreenLayout {
        Row(
            modifier = Modifier.fillMaxWidth().semantics {
                paneTitle = title
            }.padding(vertical = 8.dp).heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    stringResource(R.string.close),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                modifier = Modifier.semantics {
                    heading()
                }
            )
        }
        Column(
            modifier = Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 14.dp)
        ) {
            PrivacyContent()
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp).heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.privacy_dismiss), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PrivacyContent() {
    Text(
        stringResource(R.string.privacy_heading),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.semantics { heading() }
    )
    Text(
        stringResource(R.string.privacy_intro),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(
        modifier = Modifier.padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
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
    }
    Row(
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().background(
            MaterialTheme.colorScheme.surfaceContainerLow,
            RoundedCornerShape(16.dp)
        ).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_shield),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.privacy_backup_body),
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivacySection(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                stringResource(title),
                style = textStyle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(body), style = textStyle)
        }
    }
}
