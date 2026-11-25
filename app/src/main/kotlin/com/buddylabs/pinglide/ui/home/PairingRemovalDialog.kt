package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.ui.components.ConfirmationDialog
import com.buddylabs.pinglide.ui.components.ConfirmationDialogEmblem
import com.buddylabs.pinglide.ui.components.ConfirmationDialogHeading

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
    ConfirmationDialog(
        pane = stringResource(R.string.home_remove_title, name),
        description = stringResource(R.string.home_remove_copy),
        onDismiss = onCancel,
        dismissEnabled = !pending,
        emblem = {
            ConfirmationDialogEmblem(
                painterResource(R.drawable.ic_trash),
                colorResource(R.color.error_emblem_container),
                colorResource(R.color.error_emblem),
                22.dp
            )
        },
        heading = { RemovalHeading(name, pending) },
        feedback = { RemovalFeedback(pending, failed, receivingEnabled) }
    ) {
        RemovalActions(pending, failed, onCancel, onRemove)
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
    ConfirmationDialogHeading(
        if (pending) {
            stringResource(R.string.home_removing)
        } else {
            stringResource(R.string.home_remove_title, name)
        },
        modifier = outline.focusRequester(focus).focusable(interactionSource = interactions)
            .semantics { traversalIndex = -1f }
    )
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
