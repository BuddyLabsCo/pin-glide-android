package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R

@Composable
internal fun BrandIcon(
    modifier: Modifier,
    tileShape: RoundedCornerShape = RoundedCornerShape(11.dp)
) {
    Image(
        painterResource(R.mipmap.ic_launcher),
        contentDescription = null,
        modifier = modifier.dropShadow(
            shape = tileShape,
            shadow = Shadow(
                radius = 12.dp,
                color = Color(0xFF0A1210),
                offset = DpOffset(0.dp, 5.dp),
                alpha = 0.12f
            )
        ).clip(tileShape)
    )
}
