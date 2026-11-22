package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R

@Composable
internal fun RingedEmblem(shape: Shape, error: Boolean = false, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ring = colors.primary.copy(alpha = 0.22f)
    Box(
        modifier = Modifier.size(222.dp).border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(186.dp).border(1.dp, ring, CircleShape))
        Box(
            modifier = Modifier.size(148.dp).background(
                if (error) {
                    SolidColor(colorResource(R.color.error_emblem_container))
                } else {
                    Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer))
                },
                shape
            ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
