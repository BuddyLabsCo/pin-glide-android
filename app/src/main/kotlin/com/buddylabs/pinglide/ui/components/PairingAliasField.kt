package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
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

@Composable
internal fun PairingAliasField(
    alias: String,
    guidance: String,
    invalid: Boolean,
    enabled: Boolean,
    onAliasChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputModifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.home_alias_label)
    val danger = colorResource(R.color.error_emblem)
    val smallText = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.sp,
        lineHeight = 15.4.sp,
        letterSpacing = 0.sp
    )
    Column(
        modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(start = 15.dp, top = 14.dp, end = 15.dp, bottom = 12.dp)
    ) {
        Text(
            label,
            color = colors.onSurfaceVariant,
            style = smallText,
            fontWeight = FontWeight(650)
        )
        Spacer(Modifier.height(7.dp))
        AliasInput(alias, label, guidance.takeIf { invalid }, enabled, onAliasChange, inputModifier)
        Spacer(Modifier.height(8.dp))
        Text(
            guidance,
            color = if (invalid) danger else colors.onSurfaceVariant,
            style = smallText,
            fontWeight = if (invalid) FontWeight(650) else FontWeight.Normal,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun AliasInput(
    alias: String,
    label: String,
    validationError: String?,
    enabled: Boolean,
    onAliasChange: (String) -> Unit,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val active = focused && enabled
    val border = when {
        validationError != null -> colorResource(R.color.error_emblem)
        active -> colors.primary
        else -> colors.onSurface.copy(alpha = if (isSystemInDarkTheme()) .32f else .34f)
    }
    val halo = when {
        validationError != null -> .16f
        active -> .18f
        else -> 0f
    }
    val fieldShape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = alias,
        onValueChange = onAliasChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            letterSpacing = 0.sp
        ),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
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
                        if (enabled) colors.surface else colors.surfaceContainerHigh,
                        fieldShape
                    )
                    .border(1.dp, border, fieldShape)
                    .padding(horizontal = 15.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) { input() }
        }
    )
}
