package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FeedbackPill(
    message: String,
    accessible: Boolean,
    modifier: Modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 280.dp)
) {
    val colors = MaterialTheme.colorScheme
    val shadowColor = if (isSystemInDarkTheme()) Color(0x61000000) else Color(0x260A1210)
    Text(
        message,
        modifier.dropShadow(
            CircleShape,
            Shadow(24.dp, shadowColor, offset = DpOffset(0.dp, 8.dp))
        )
            .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
            .semantics {
                if (accessible) {
                    liveRegion = LiveRegionMode.Polite
                } else {
                    hideFromAccessibility()
                }
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = colors.surface,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight(650),
            textAlign = TextAlign.Center
        )
    )
}
