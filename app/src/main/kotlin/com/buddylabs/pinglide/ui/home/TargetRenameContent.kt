package com.buddylabs.pinglide.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.pairingAliasError
import com.buddylabs.pinglide.ui.components.PairingAliasField
import kotlinx.coroutines.flow.first

@Composable
internal fun TargetRenameContent(
    draft: String,
    otherAliases: List<String>,
    action: TargetAction,
    onDraft: (String) -> Unit,
    onRename: (String) -> Unit,
    onCancel: () -> Unit
) {
    val validation = pairingAliasError(draft, otherAliases)
    val failed = action.result == PairingChange.Unavailable
    RenameAliasField(
        draft,
        validation,
        action.result == PairingChange.InvalidAlias,
        action.pending,
        onDraft
    )
    if (failed) RenameFailureNotice()
    Spacer(Modifier.height(18.dp))
    RenameActions(action.pending, validation == null, failed, { onRename(draft) }, onCancel)
}

@Composable
private fun RenameAliasField(
    draft: String,
    validation: PairingAliasError?,
    rejected: Boolean,
    pending: Boolean,
    onDraft: (String) -> Unit
) {
    val guidance = stringResource(
        when (validation) {
            PairingAliasError.Blank -> R.string.home_rename_blank
            PairingAliasError.TooLong -> R.string.home_rename_too_long
            PairingAliasError.InvalidCharacters -> R.string.home_alias_invalid_characters
            PairingAliasError.Duplicate -> R.string.home_rename_duplicate
            null -> if (rejected) R.string.home_rename_rejected else R.string.home_rename_help
        }
    )
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    LaunchedEffect(window) {
        if (!pending) {
            snapshotFlow { window.isWindowFocused }.first { it }
            focus.requestFocus()
        }
    }
    PairingAliasField(
        alias = draft,
        guidance = guidance,
        invalid = validation != null || rejected,
        enabled = !pending,
        onAliasChange = onDraft,
        modifier = Modifier.testTag("renameAliasGroup"),
        inputModifier = Modifier.focusRequester(focus)
    )
}

@Composable
private fun RenameFailureNotice() {
    val danger = colorResource(R.color.error_emblem)
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth()
            .background(colorResource(R.color.error_emblem_container), RoundedCornerShape(16.dp))
            .padding(13.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(R.drawable.ic_error_filled), null, Modifier.size(18.dp), danger)
        Text(
            stringResource(R.string.home_rename_error),
            color = danger,
            style = MaterialTheme.typography.bodySmall.copy(
                letterSpacing = 0.sp,
                lineHeight = 18.sp
            )
        )
    }
}

@Composable
private fun RenameActions(
    pending: Boolean,
    valid: Boolean,
    failed: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val saveEnabled = valid && !pending
    val actionPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val label = when {
        pending -> R.string.home_saving
        failed -> R.string.home_rename_retry
        else -> R.string.home_save
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !pending,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .alpha(if (pending) 0.38f else 1f),
            border = BorderStroke(1.dp, colors.onSurface.copy(alpha = renameOutlineAlpha())),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.primary,
                disabledContentColor = colors.primary
            ),
            contentPadding = actionPadding
        ) {
            Text(stringResource(R.string.setup_cancel), fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .alpha(if (saveEnabled) 1f else 0.38f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = colors.primary,
                disabledContentColor = colors.onPrimary
            ),
            contentPadding = actionPadding
        ) {
            if (pending) {
                RenameSavingSpinner()
                Spacer(Modifier.size(8.dp))
            }
            Text(stringResource(label), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RenameSavingSpinner() {
    val color = MaterialTheme.colorScheme.onPrimary
    val rotation by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing))
    )
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = 45f + rotation,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke)
        )
    }
}

@Composable
private fun renameOutlineAlpha(): Float = if (isSystemInDarkTheme()) 0.32f else 0.34f
