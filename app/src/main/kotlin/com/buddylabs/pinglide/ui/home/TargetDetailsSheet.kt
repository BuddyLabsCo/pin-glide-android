package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.domain.pairing.pairingAliasError

private enum class DetailsPage { Details, Rename, Remove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetDetailsSheet(
    target: TargetDevice,
    otherAliases: List<String>,
    action: TargetAction,
    onSelect: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onClearAction: () -> Unit,
    onDismiss: () -> Unit
) {
    var page by rememberSaveable(target.pairingId) { mutableStateOf(DetailsPage.Details) }
    var draft by rememberSaveable(target.pairingId) { mutableStateOf(target.alias) }
    val pending by rememberUpdatedState(action.pending)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !pending }
    )
    LaunchedEffect(action.result) {
        if (action.result == PairingChange.Renamed) page = DetailsPage.Details
    }
    val name = target.alias.ifBlank { stringResource(R.string.home_target_device) }
    val title = if (page == DetailsPage.Rename) stringResource(R.string.home_rename) else name
    ModalBottomSheet(
        onDismissRequest = { if (!pending) onDismiss() },
        sheetState = sheet,
        sheetGesturesEnabled = !pending,
        contentWindowInsets = { WindowInsets.safeDrawing },
        modifier = Modifier.imePadding().semantics { paneTitle = title }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            if (page != DetailsPage.Rename) {
                Text(targetPresence(target), style = MaterialTheme.typography.bodySmall)
                if (!target.keyUsable) KeyFailureNotice()
            }
            when (page) {
                DetailsPage.Details -> {
                    Text(
                        stringResource(R.string.home_receiving),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.home_receiving_copy))
                    val label = stringResource(R.string.home_receive, name)
                    Switch(
                        checked = target.enabled,
                        enabled = target.keyUsable,
                        onCheckedChange = onSelect,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription =
                                label
                        }
                    )
                    OutlinedButton(
                        onClick = {
                            draft = target.alias
                            onClearAction()
                            page = DetailsPage.Rename
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.home_rename)) }
                    OutlinedButton(
                        onClick = {
                            onClearAction()
                            page = DetailsPage.Remove
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(R.string.home_remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                DetailsPage.Rename -> RenameTarget(draft, otherAliases, pending, {
                    draft = it
                    onClearAction()
                }, onRename)

                DetailsPage.Remove -> {
                    Text(
                        stringResource(R.string.home_remove_title, name),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(stringResource(R.string.home_remove_copy))
                    Button(
                        onClick = onRemove,
                        enabled = !pending,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.home_remove_confirm)) }
                }
            }
            ActionNotice(action, page, target.enabled)
            if (page != DetailsPage.Details) {
                OutlinedButton(
                    onClick = {
                        draft = target.alias
                        onClearAction()
                        page = DetailsPage.Details
                    },
                    enabled = !pending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.setup_cancel)) }
            }
            OutlinedButton(
                onClick = onDismiss,
                enabled = !pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun RenameTarget(
    draft: String,
    otherAliases: List<String>,
    pending: Boolean,
    onDraft: (String) -> Unit,
    onRename: (String) -> Unit
) {
    val focus = remember { FocusRequester() }
    val validation = pairingAliasError(draft, otherAliases)
    val message = stringResource(
        when (validation) {
            PairingAliasError.Blank -> R.string.home_alias_blank
            PairingAliasError.TooLong -> R.string.home_alias_too_long
            PairingAliasError.InvalidCharacters -> R.string.home_alias_invalid_characters
            PairingAliasError.Duplicate -> R.string.home_alias_duplicate
            null -> R.string.home_alias_help
        }
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
    Text(stringResource(R.string.home_alias_scope), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = draft,
        onValueChange = onDraft,
        enabled = !pending,
        isError = validation != null,
        label = { Text(stringResource(R.string.home_alias_label)) },
        supportingText = {
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
            if (validation != null) error(message)
        }
    )
    Button(
        onClick = { onRename(draft) },
        enabled = validation == null && !pending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) { Text(stringResource(if (pending) R.string.home_saving else R.string.home_save)) }
}

@Composable
private fun KeyFailureNotice() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Text(
            stringResource(R.string.home_key_recovery),
            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun ActionNotice(action: TargetAction, page: DetailsPage, enabled: Boolean) {
    val message = when {
        action.pending -> R.string.home_target_pending

        action.result == PairingChange.InvalidAlias -> R.string.home_alias_help

        action.result == PairingChange.Unavailable && page == DetailsPage.Remove && enabled ->
            R.string.home_target_busy

        action.result == PairingChange.Unavailable -> if (page == DetailsPage.Remove) {
            R.string.home_remove_error
        } else {
            R.string.home_rename_error
        }

        action.result == PairingChange.Renamed -> R.string.home_renamed

        else -> return
    }
    Text(
        stringResource(message),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
