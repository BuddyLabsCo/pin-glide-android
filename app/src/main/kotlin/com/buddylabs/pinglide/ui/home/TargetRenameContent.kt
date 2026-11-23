package com.buddylabs.pinglide.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.pairingAliasError
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
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        TargetDetailsHeader(
            stringResource(R.string.home_rename),
            stringResource(R.string.home_alias_scope),
            Modifier.padding(bottom = 18.dp)
        )
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
}

@Composable
private fun RenameAliasField(
    draft: String,
    validation: PairingAliasError?,
    rejected: Boolean,
    pending: Boolean,
    onDraft: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.home_alias_label)
    val guidance = stringResource(
        when (validation) {
            PairingAliasError.Blank -> R.string.home_rename_blank
            PairingAliasError.TooLong -> R.string.home_rename_too_long
            PairingAliasError.InvalidCharacters -> R.string.home_alias_invalid_characters
            PairingAliasError.Duplicate -> R.string.home_rename_duplicate
            null -> if (rejected) R.string.home_rename_choose_another else R.string.home_rename_help
        }
    )
    val hint = if (rejected) stringResource(R.string.home_rename_rejected, guidance) else guidance
    val invalid = validation != null || rejected
    val danger = colorResource(R.color.error_emblem)
    val smallText = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.sp,
        lineHeight = 15.4.sp,
        letterSpacing = 0.sp
    )
    Column(
        Modifier.fillMaxWidth().testTag("renameAliasGroup")
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(start = 15.dp, top = 14.dp, end = 15.dp, bottom = 12.dp)
    ) {
        Text(
            label,
            color = colors.onSurfaceVariant,
            style = smallText,
            fontWeight = FontWeight(650)
        )
        Spacer(Modifier.height(7.dp))
        RenameAliasInput(draft, label, hint.takeIf { invalid }, pending, onDraft)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            color = if (invalid) danger else colors.onSurfaceVariant,
            style = smallText,
            fontWeight = if (invalid) FontWeight(650) else FontWeight.Normal,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun RenameAliasInput(
    draft: String,
    label: String,
    validationError: String?,
    pending: Boolean,
    onDraft: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    LaunchedEffect(window) {
        if (!pending) {
            snapshotFlow { window.isWindowFocused }.first { it }
            focus.requestFocus()
        }
    }
    var focused by remember { mutableStateOf(false) }
    val active = focused && !pending
    val border = when {
        validationError != null -> colorResource(R.color.error_emblem)
        active -> colors.primary
        else -> colors.onSurface.copy(alpha = renameOutlineAlpha())
    }
    val halo = when {
        validationError != null -> 0.16f
        active -> 0.18f
        else -> 0f
    }
    val fieldShape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = draft,
        onValueChange = onDraft,
        enabled = !pending,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (pending) colors.onSurfaceVariant else colors.onSurface,
            letterSpacing = 0.sp
        ),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = label
                if (validationError != null) error(validationError)
            },
        decorationBox = { input ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .drawBehind {
                        if (halo > 0f) {
                            val outset = 2.dp.toPx()
                            drawRoundRect(
                                border.copy(alpha = halo),
                                topLeft = Offset(-outset, -outset),
                                size = Size(size.width + 2 * outset, size.height + 2 * outset),
                                cornerRadius = CornerRadius(14.dp.toPx())
                            )
                        }
                    }
                    .background(
                        if (pending) colors.surfaceContainerHigh else colors.surface,
                        fieldShape
                    )
                    .border(1.dp, border, fieldShape)
                    .padding(horizontal = 15.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) { input() }
        }
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
