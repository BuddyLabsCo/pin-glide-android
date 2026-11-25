package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.RevocationStep
import com.buddylabs.pinglide.domain.identity.SenderInventoryState
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.identity.SenderRevocationState
import com.buddylabs.pinglide.ui.components.ConfirmationDialog
import com.buddylabs.pinglide.ui.components.ConfirmationDialogEmblem
import com.buddylabs.pinglide.ui.components.ConfirmationDialogHeading
import com.buddylabs.pinglide.ui.components.WarningInfoCard
import kotlinx.coroutines.launch

@Composable
internal fun SenderInventoryScreen(inventory: AccountSenders, revocation: SenderRevocation) {
    val state by inventory.state.collectAsState()
    val operation by revocation.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(inventory) { inventory.open() }
    WarningInfoCard(R.string.settings_device_access_explanation, icon = R.drawable.ic_warning)
    SenderInventoryStatus(state, inventory::refresh)
    state.senders?.let { senders ->
        val current = senders.filter { it.senderId == state.owner?.senderId }
        val others = senders.filterNot { it.senderId == state.owner?.senderId }
        SenderGroup(stringResource(R.string.senders_this_device), current, state) { sender ->
            scope.launch { inventory.select(state, sender.senderId)?.let(revocation::select) }
        }
        SenderGroup(stringResource(R.string.senders_other_devices), others, state) { sender ->
            scope.launch { inventory.select(state, sender.senderId)?.let(revocation::select) }
        }
    }
    operation?.takeIf { it.step == RevocationStep.Confirmation }?.let { confirmation ->
        SenderRevocationConfirmation(confirmation, revocation)
    }
}

@Composable
private fun SenderRevocationConfirmation(
    confirmation: SenderRevocationState,
    revocation: SenderRevocation
) {
    val selection = confirmation.selection
    val cancelFocus = remember { FocusRequester() }
    val title = stringResource(R.string.senders_revoke_title, selection.deviceName)
    val padding = PaddingValues(horizontal = 22.dp)
    ConfirmationDialog(
        pane = title,
        description = stringResource(R.string.senders_revoke_consequences),
        onDismiss = { revocation.dismiss(confirmation) },
        emblem = {
            ConfirmationDialogEmblem(
                painterResource(R.drawable.ic_revoke_lock),
                colorResource(R.color.error_emblem_container),
                colorResource(R.color.error_emblem),
                22.dp
            )
        },
        heading = { ConfirmationDialogHeading(title) }
    ) {
        TextButton(
            onClick = { revocation.dismiss(confirmation) },
            modifier = Modifier.heightIn(min = 48.dp).focusRequester(cancelFocus),
            contentPadding = padding
        ) { Text(stringResource(R.string.setup_cancel), fontWeight = FontWeight.Bold) }
        Button(
            onClick = { revocation.confirm(confirmation) },
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.error_emblem_container),
                contentColor = colorResource(R.color.on_error_emblem_container)
            )
        ) {
            Text(stringResource(R.string.senders_confirm_proof), fontWeight = FontWeight.Bold)
        }
    }
    LaunchedEffect(selection) { cancelFocus.requestFocus() }
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
    SettingsGroup(title) {
        if (senders.isEmpty()) {
            Text(stringResource(R.string.senders_no_others), Modifier.padding(16.dp))
        }
        senders.forEachIndexed { index, sender ->
            key(sender.senderId) {
                SenderRow(sender, state, onSelect)
            }
            if (index < senders.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SenderRow(
    sender: AccountSender,
    state: SenderInventoryState,
    onSelect: (AccountSender) -> Unit
) {
    val actionFocus = remember(sender.senderId) { FocusRequester() }
    var selected by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val actionDescription =
        stringResource(R.string.senders_revoke_description, sender.deviceName)
    SettingsListRow(
        title = sender.deviceName,
        leading = {
            Icon(
                painterResource(R.drawable.ic_smartphone),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Button(
            onClick = { onSelect(sender) },
            enabled = state.canSelect,
            modifier = Modifier.heightIn(
                min = 48.dp
            ).focusRequester(actionFocus).semantics {
                contentDescription = actionDescription
            },
            contentPadding = PaddingValues(horizontal = 22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.error_emblem_container),
                contentColor = colorResource(R.color.on_error_emblem_container)
            )
        ) {
            Text(
                stringResource(R.string.senders_revoke),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.14.sp
            )
        }
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
