package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingChange

@Composable
internal fun PairingRemovalDialog(
    name: String,
    action: TargetAction,
    receivingEnabled: Boolean,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    val pending = action.pending
    val failed = action.result == PairingChange.Unavailable
    val pane = stringResource(R.string.home_remove_title, name)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val width = (configuration.screenWidthDp.dp - 48.dp).coerceIn(0.dp, 320.dp)
    val height = with(density) {
        (configuration.screenHeightDp.dp - statusBarHeight.toDp() - 48.dp).coerceAtLeast(0.dp)
    }
    val dimAmount = if (isSystemInDarkTheme()) 0.64f else 0.45f
    Dialog(
        onDismissRequest = { if (!pending) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = !pending,
            dismissOnClickOutside = !pending,
            usePlatformDefaultWidth = false
        )
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            window.setDimAmount(dimAmount)
            val attributes = window.attributes
            if (attributes.y != statusBarHeight / 2) {
                attributes.y = statusBarHeight / 2
                window.attributes = attributes
            }
        }
        Surface(
            modifier = Modifier.width(width).heightIn(max = height).semantics {
                paneTitle = pane
                isTraversalGroup = true
            },
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                RemovalEmblem()
                Spacer(Modifier.height(16.dp))
                RemovalHeading(name, pending)
                Spacer(Modifier.height(9.dp))
                Text(
                    stringResource(R.string.home_remove_copy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 19.sp
                    )
                )
                RemovalFeedback(pending, failed, receivingEnabled)
                Spacer(Modifier.height(22.dp))
                RemovalActions(pending, failed, onCancel, onRemove)
            }
        }
    }
}

@Composable
private fun RemovalHeading(name: String, pending: Boolean) {
    val focus = remember { FocusRequester() }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val keyboardMode = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(Unit) { focus.requestFocus() }
    val outline = if (focused && keyboardMode) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }
    Text(
        if (pending) {
            stringResource(R.string.home_removing)
        } else {
            stringResource(R.string.home_remove_title, name)
        },
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            letterSpacing = (-0.77).sp
        ),
        fontWeight = FontWeight.Bold,
        modifier = outline.focusRequester(focus).focusable(interactionSource = interactions)
            .semantics {
                heading()
                traversalIndex = -1f
            }
    )
}

@Composable
private fun RemovalEmblem() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorResource(R.color.error_emblem_container)
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_trash),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colorResource(R.color.error_emblem)
            )
        }
    }
}

@Composable
private fun RemovalFeedback(pending: Boolean, failed: Boolean, receivingEnabled: Boolean) {
    if (!pending && !failed) return
    if (pending) {
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.dp
            )
            Text(
                stringResource(R.string.home_target_pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        Spacer(Modifier.height(14.dp))
        Surface(
            color = colorResource(R.color.error_emblem_container),
            contentColor = colorResource(R.color.error_emblem),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                Modifier.padding(13.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_error_filled),
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp).size(18.dp)
                )
                Text(
                    stringResource(
                        if (receivingEnabled) {
                            R.string.home_target_busy
                        } else {
                            R.string.home_remove_error
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun RemovalActions(
    pending: Boolean,
    failed: Boolean,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val actionModifier = Modifier.heightIn(min = 48.dp).alpha(if (pending) 0.38f else 1f)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onCancel,
            enabled = !pending,
            modifier = actionModifier,
            contentPadding = ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.textButtonColors(disabledContentColor = colors.primary)
        ) { Text(stringResource(R.string.setup_cancel), fontWeight = FontWeight.Bold) }
        Button(
            onClick = onRemove,
            enabled = !pending,
            modifier = actionModifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.error_emblem_container),
                contentColor = colorResource(R.color.on_error_emblem_container),
                disabledContainerColor = colors.surfaceContainerHighest,
                disabledContentColor = colors.onSurfaceVariant
            )
        ) {
            Text(
                stringResource(
                    if (failed) R.string.home_remove_retry else R.string.home_remove_confirm
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
