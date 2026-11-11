package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.SenderInventoryState
import kotlinx.coroutines.launch

@Composable
internal fun SenderInventoryScreen(inventory: AccountSenders) {
    val state by inventory.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(inventory) { inventory.open() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            stringResource(R.string.settings_device_access_explanation),
            Modifier.padding(16.dp)
        )
    }
    SenderInventoryStatus(state, inventory::refresh)
    state.senders?.let { senders ->
        val current = senders.filter { it.senderId == state.owner?.senderId }
        val others = senders.filterNot { it.senderId == state.owner?.senderId }
        SenderGroup(stringResource(R.string.senders_this_device), current, state) { sender ->
            scope.launch { inventory.select(state, sender.senderId) }
        }
        SenderGroup(stringResource(R.string.senders_other_devices), others, state) { sender ->
            scope.launch { inventory.select(state, sender.senderId) }
        }
    }
    state.selection?.let { selection ->
        val cancelFocus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = inventory::clearSelection,
            title = { Text(stringResource(R.string.senders_revoke_title, selection.deviceName)) },
            text = {
                Text(
                    stringResource(R.string.settings_device_access_explanation),
                    Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(
                    onClick = inventory::clearSelection,
                    modifier = Modifier.heightIn(min = 48.dp).focusRequester(cancelFocus)
                ) { Text(stringResource(R.string.setup_cancel)) }
            }
        )
        LaunchedEffect(selection) { cancelFocus.requestFocus() }
    }
}

@Composable
private fun SenderInventoryStatus(state: SenderInventoryState, onRetry: () -> Unit) {
    if (state.loading || state.failed) {
        Text(
            stringResource(if (state.failed) R.string.senders_error else R.string.senders_loading),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (state.failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
    if (state.failed) {
        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.senders_retry))
        }
    }
}

@Composable
private fun SenderGroup(
    title: String,
    senders: List<AccountSender>,
    state: SenderInventoryState,
    onSelect: (AccountSender) -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() }
    )
    Card(Modifier.fillMaxWidth()) {
        if (senders.isEmpty()) {
            Text(stringResource(R.string.senders_no_others), Modifier.padding(16.dp))
        }
        senders.forEach { sender ->
            key(sender.senderId) {
                val actionFocus = remember(sender.senderId) { FocusRequester() }
                var selected by remember { mutableStateOf(false) }
                val windowFocused = LocalWindowInfo.current.isWindowFocused
                val actionDescription =
                    stringResource(R.string.senders_revoke_description, sender.deviceName)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.ic_devices), contentDescription = null)
                    Text(
                        sender.deviceName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(
                        onClick = { onSelect(sender) },
                        enabled = state.canSelect,
                        modifier = Modifier.heightIn(
                            min = 48.dp
                        ).focusRequester(actionFocus).semantics {
                            contentDescription = actionDescription
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.senders_revoke)) }
                }
                LaunchedEffect(state.selection, windowFocused, state.canSelect) {
                    if (state.selection?.senderId == sender.senderId) {
                        selected = true
                    } else if (selected && windowFocused && state.canSelect) {
                        selected = false
                        actionFocus.requestFocus()
                    }
                }
            }
        }
    }
}
