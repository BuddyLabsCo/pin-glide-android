package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TargetRow(target: TargetDevice, onOpen: () -> Unit, onSelect: (Boolean) -> Unit) {
    val name = target.alias.ifBlank { stringResource(R.string.home_target_device) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 80.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(
                1f
            ).clickable(onClick = onOpen).padding(vertical = 16.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(targetPresence(target), style = MaterialTheme.typography.bodySmall)
            if (!target.keyUsable) {
                Text(
                    stringResource(R.string.home_key_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        val switchLabel = stringResource(R.string.home_receive, name)
        Switch(
            checked = target.enabled,
            onCheckedChange = onSelect,
            enabled = target.keyUsable,
            modifier = Modifier.semantics { contentDescription = switchLabel }
        )
    }
}

@Composable
internal fun targetPresence(target: TargetDevice): String {
    if (target.presence == Presence.Unknown || target.stateChangedAt == null) {
        return stringResource(R.string.home_awaiting_update)
    }
    val status = stringResource(
        if (target.presence == Presence.Online) R.string.home_online else R.string.home_offline
    )
    val changed = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault()).format(Instant.parse(target.stateChangedAt))
    return stringResource(R.string.home_presence, status, changed)
}
