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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.ui.components.AnchoredPairingTestAction
import com.buddylabs.pinglide.ui.components.PairingTestFeedback
import com.buddylabs.pinglide.ui.components.TestMessageAction
import com.buddylabs.pinglide.ui.components.TestMessageState
import com.buddylabs.pinglide.ui.components.hasAnchoredNotice
import com.buddylabs.pinglide.ui.components.testMessageButtonLabel
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
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
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
    val subtitle = if (showingDetails) presenceLabel else stringResource(R.string.home_alias_scope)
    val colors = MaterialTheme.colorScheme
    val accessibility = LocalAccessibilityManager.current
    var headerBottom by remember(target.pairingId) { mutableFloatStateOf(0f) }
    val confirmation = stringResource(R.string.home_renamed)
        .takeIf { action.result == PairingChange.Renamed }
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
        val contentModifier = Modifier.fillMaxWidth().verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        AnchoredPairingTestAction(testMessage, accessibility, showingDetails, headerBottom) {
                testState,
                sendTest,
                anchor
            ->
            Column(contentModifier) {
                val header = if (testState.outcome?.pairingRemoved == true) {
                    Modifier.zIndex(1f).offset { IntOffset(0, scroll.value) }
                        .background(colors.surface)
                } else {
                    Modifier
                }
                TargetDetailsHeader(
                    title,
                    subtitle,
                    header.onGloballyPositioned { headerBottom = it.boundsInRoot().bottom }
                        .padding(bottom = if (showingDetails) 14.dp else 18.dp),
                    confirmation.takeIf { showingDetails }
                )
                if (showingDetails) {
                    if (!target.keyUsable) KeyFailureNotice()
                    TargetDetailsActions(
                        target,
                        name,
                        testState,
                        sendTest,
                        anchor,
                        onSelect,
                        removalFocus,
                        renameSaved = action.result == PairingChange.Renamed,
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
                } else {
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
        }
    }
}

@Composable
private fun detailsScrim(): Color =
    if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.64f) else Color(0x73081815)

@Composable
private fun TargetDetailsHeader(
    title: String,
    subtitle: String,
    modifier: Modifier,
    confirmation: String?
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
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                    if (confirmation != null) stateDescription = confirmation
                }
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
    testState: TestMessageState,
    sendTest: () -> Unit,
    feedbackAnchor: Modifier,
    onSelect: (Boolean) -> Unit,
    removalFocus: FocusRequester,
    renameSaved: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val offline = target.presence == Presence.Offline
    val showTestChevron = offline || !target.enabled || !target.keyUsable || renameSaved
    val disabledAlpha = 0.38f
    val divider = colors.onSurface.copy(alpha = 0.17f)
    val dividerThickness = if (offline) 1.dp else 0.5.dp
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
        HorizontalDivider(thickness = dividerThickness, color = divider)
        val testEnabled = target.keyUsable && !testState.pending
        val testAlpha = if (testEnabled) 1f else disabledAlpha
        val testIconTint = colors.onSurfaceVariant.copy(alpha = testAlpha)
        TargetDetailsRow(
            R.drawable.ic_send,
            testMessageButtonLabel(testState.pending),
            Modifier.clickable(enabled = testEnabled, role = Role.Button, onClick = sendTest)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.onSurface.copy(alpha = testAlpha),
            iconTint = testIconTint
        ) {
            if (showTestChevron && !testState.pending) {
                Icon(
                    painterResource(R.drawable.ic_chevron),
                    null,
                    Modifier.size(20.dp),
                    testIconTint
                )
            }
        }
        PairingTestFeedback(
            testState.outcome?.takeUnless { it.hasAnchoredNotice },
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        val testDividerOpacity = if (testState.pending) disabledAlpha else 1f
        HorizontalDivider(
            modifier = feedbackAnchor,
            thickness = if (testState.pending) 1.dp else dividerThickness,
            color = divider.copy(alpha = divider.alpha * testDividerOpacity)
        )
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
        HorizontalDivider(thickness = dividerThickness, color = divider)
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
