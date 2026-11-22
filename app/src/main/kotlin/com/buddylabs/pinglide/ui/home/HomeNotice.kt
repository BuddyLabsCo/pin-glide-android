package com.buddylabs.pinglide.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeNotice(@StringRes message: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(message),
        modifier = modifier.widthIn(max = 280.dp)
            .shadow(8.dp, CircleShape)
            .background(colors.onSurface.copy(alpha = 0.88f), CircleShape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = colors.surface,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight(650),
        textAlign = TextAlign.Center
    )
}
