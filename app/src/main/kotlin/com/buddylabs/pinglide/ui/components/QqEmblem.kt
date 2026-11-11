package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R

@Composable
internal fun QqEmblem(size: Dp = 92.dp) {
    val shape = RoundedCornerShape(percent = 31)
    Image(
        painterResource(R.drawable.ic_qq),
        contentDescription = null,
        modifier = Modifier.size(size).shadow(10.dp, shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1597DE), Color(0xFF075E9D))),
                shape
            ).padding(size * 0.18f)
    )
}

@Composable
internal fun QqLoadingEmblem() {
    var motionEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }.collect {
            motionEnabled = it
        }
    }
    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        val progressModifier = Modifier.matchParentSize().semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
        if (motionEnabled) {
            CircularProgressIndicator(progressModifier, strokeWidth = 6.dp)
        } else {
            CircularProgressIndicator(progress = { 0.25f }, progressModifier, strokeWidth = 6.dp)
        }
        QqEmblem()
    }
}
