package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun ConfirmationDialog(
    pane: String,
    description: String,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    emblem: @Composable () -> Unit,
    heading: @Composable () -> Unit,
    feedback: @Composable () -> Unit = {},
    actions: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val width = (configuration.screenWidthDp.dp - 48.dp).coerceIn(0.dp, 320.dp)
    val height = with(density) {
        (configuration.screenHeightDp.dp - statusBarHeight.toDp() - 48.dp).coerceAtLeast(0.dp)
    }
    val dimAmount = if (isSystemInDarkTheme()) 0.64f else 0.45f
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissEnabled,
            dismissOnClickOutside = dismissEnabled,
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
                emblem()
                Spacer(Modifier.height(16.dp))
                heading()
                Spacer(Modifier.height(9.dp))
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 19.sp
                    )
                )
                feedback()
                Spacer(Modifier.height(22.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
internal fun ConfirmationDialogHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            letterSpacing = (-0.77).sp
        ),
        fontWeight = FontWeight.Bold,
        modifier = modifier.semantics { heading() }
    )
}

@Composable
internal fun ConfirmationDialogEmblem(
    icon: Painter,
    containerColor: Color,
    tint: Color,
    iconSize: Dp
) {
    Surface(shape = RoundedCornerShape(16.dp), color = containerColor) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
        }
    }
}
