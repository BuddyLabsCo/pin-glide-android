package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.ui.components.PairingTestAction
import com.buddylabs.pinglide.ui.components.TestMessageAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class DetailsPage { Details, Rename, Remove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetDetailsSheet(
    target: TargetDevice,
    presenceLabel: String,
    otherAliases: List<String>,
    action: TargetAction,
    testMessage: TestMessageAction,
    onSelect: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onClearAction: () -> Unit,
    onDismiss: () -> Unit
) {
    var page by rememberSaveable(target.pairingId) { mutableStateOf(DetailsPage.Details) }
    var restoreRemovalFocus by rememberSaveable(target.pairingId) { mutableStateOf(false) }
    val removalFocus = remember { FocusRequester() }
    LaunchedEffect(page, target.pairingId) {
        testMessage.enter(target.pairingId.takeIf { page == DetailsPage.Details })
    }
    var draft by rememberSaveable(target.pairingId) { mutableStateOf(target.alias) }
    val pending by rememberUpdatedState(action.pending)
    val scope = rememberCoroutineScope()
    val returnToDetails: () -> Unit = {
        draft = target.alias
        onClearAction()
        page = DetailsPage.Details
    }
    val name = target.alias.ifBlank { stringResource(R.string.home_target_device) }
    if (page == DetailsPage.Remove) {
        PairingRemovalDialog(name, action, target.enabled, onRemove) {
            restoreRemovalFocus = true
            returnToDetails()
        }
        return
    }
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !pending }
    )
    LaunchedEffect(action.result) {
        if (action.result == PairingChange.Renamed) page = DetailsPage.Details
    }
    val title = if (page == DetailsPage.Rename) stringResource(R.string.home_rename) else name
    val showingDetails = page == DetailsPage.Details
    val colors = MaterialTheme.colorScheme
    val sheetShadow = if (showingDetails) {
        Modifier
    } else {
        Modifier.dropShadow(
            BottomSheetDefaults.ExpandedShape,
            Shadow(42.dp, Color.Black, offset = DpOffset(0.dp, (-14).dp), alpha = 0.18f)
        )
    }
    ModalBottomSheet(
        onDismissRequest = {
            if (!pending) {
                if (page == DetailsPage.Rename) {
                    returnToDetails()
                    scope.launch { sheet.show() }
                } else {
                    onDismiss()
                }
            }
        },
        sheetState = sheet,
        sheetGesturesEnabled = !pending,
        containerColor = colors.surface,
        scrimColor = detailsScrim(),
        dragHandle = {
            val label = stringResource(R.string.home_details_drag_handle)
            Box(
                Modifier.padding(top = 11.dp, bottom = 18.dp).size(36.dp, 4.dp)
                    .background(colors.outlineVariant, CircleShape)
                    .semantics { contentDescription = label }
            )
        },
        contentWindowInsets = { WindowInsets.safeDrawing },
        modifier = Modifier.imePadding().then(sheetShadow).semantics { paneTitle = title }
    ) {
        if (showingDetails) {
            Box {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    TargetDetailsHeader(name, presenceLabel)
                    if (!target.keyUsable) KeyFailureNotice()
                    TargetDetailsActions(
                        target,
                        name,
                        testMessage,
                        onSelect,
                        removalFocus,
                        onRename = {
                            draft = target.alias
                            testMessage.leave()
                            onClearAction()
                            page = DetailsPage.Rename
                        },
                        onRemove = {
                            testMessage.leave()
                            onClearAction()
                            page = DetailsPage.Remove
                        }
                    )
                    val window = LocalWindowInfo.current
                    LaunchedEffect(window, restoreRemovalFocus) {
                        if (restoreRemovalFocus) {
                            snapshotFlow { window.isWindowFocused }.first { it }
                            removalFocus.requestFocus()
                            restoreRemovalFocus = false
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !pending,
                        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.34f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
                    }
                }
                if (action.result == PairingChange.Renamed) {
                    Text(
                        stringResource(R.string.home_renamed),
                        color = colors.surface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight(650),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 164.dp)
                            .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            return@ModalBottomSheet
        }
        TargetRenameContent(
            draft,
            otherAliases,
            action,
            {
                draft = it
                onClearAction()
            },
            onRename,
            returnToDetails
        )
    }
}

@Composable
private fun detailsScrim(): Color =
    if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.64f) else Color(0x73081815)

@Composable
internal fun TargetDetailsHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier.padding(bottom = 14.dp)
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight(750),
                letterSpacing = (-0.96).sp,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TargetDeviceBadge()
    }
}

@Composable
private fun TargetDetailsActions(
    target: TargetDevice,
    name: String,
    testMessage: TestMessageAction,
    onSelect: (Boolean) -> Unit,
    removalFocus: FocusRequester,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val divider = colors.onSurface.copy(alpha = 0.17f)
    val danger = colorResource(R.color.error_emblem)
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainer)) {
        TargetDetailsRow(
            R.drawable.ic_sms,
            stringResource(R.string.home_receiving),
            subtitle = stringResource(R.string.home_receiving_copy)
        ) {
            TargetReceiveSwitch(
                checked = target.enabled,
                enabled = target.keyUsable,
                label = stringResource(R.string.home_receive, name),
                onSelect = onSelect
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = divider)
        PairingTestAction(
            testMessage,
            enabled = target.keyUsable,
            feedbackModifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) { label, enabled, onClick ->
            TargetDetailsRow(
                R.drawable.ic_send,
                label,
                Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                iconTint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = divider)
        TargetDetailsRow(
            R.drawable.ic_edit,
            stringResource(R.string.home_rename),
            Modifier.clickable(role = Role.Button, onClick = onRename)
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron),
                null,
                Modifier.size(20.dp),
                colors.onSurfaceVariant
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = divider)
        TargetDetailsRow(
            R.drawable.ic_delete,
            stringResource(R.string.home_remove),
            Modifier.focusRequester(removalFocus).clickable(role = Role.Button, onClick = onRemove),
            color = danger,
            iconTint = danger
        ) {
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp), danger)
        }
    }
}

@Composable
private fun TargetDetailsRow(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), iconTint)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                color = color,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        trailing()
    }
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
