package com.buddylabs.pinglide.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun QqEmblem(size: Dp = 92.dp) {
    val shape = RoundedCornerShape(percent = 31)
    val side = with(LocalDensity.current) { size.toPx() }
    val angle = Math.toRadians(145.0)
    val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
    val center = Offset(side / 2, side / 2)
    val halfGradient = direction * (side * (direction.x + direction.y) / 2)
    Image(
        painterResource(R.drawable.ic_qq),
        contentDescription = null,
        modifier = Modifier.size(size).dropShadow(
            shape,
            Shadow(
                radius = 24.dp,
                color = Color(0xFF075E9D),
                offset = DpOffset(0.dp, 10.dp),
                alpha = 0.22f
            )
        )
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1597DE), Color(0xFF075E9D)),
                    start = center - halfGradient,
                    end = center + halfGradient
                ),
                shape
            ).padding(size * 0.18f)
    )
}

@Composable
internal fun QqLoadingEmblem(progressModifier: Modifier = Modifier.size(168.dp)) {
    LoadingEmblem(progressModifier) { QqEmblem() }
}

@Composable
internal fun LoadingEmblem(
    progressModifier: Modifier = Modifier.size(168.dp),
    emblem: @Composable () -> Unit
) {
    var motionEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }.collect {
            motionEnabled = it
        }
    }
    val rotation = if (motionEnabled) {
        rememberInfiniteTransition(label = "Progress").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "Progress arc rotation"
        )
    } else {
        null
    }
    val arc = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(contentAlignment = Alignment.Center) {
        Canvas(progressModifier.progressSemantics()) {
            val stroke = Stroke(6.dp.toPx())
            val diameter = (size.minDimension - stroke.width).coerceAtLeast(0f)
            val radius = diameter / 2f
            drawCircle(track, radius = radius, style = stroke)
            drawArc(
                color = arc,
                startAngle = -135f + (rotation?.value ?: 0f),
                sweepAngle = 90f,
                useCenter = false,
                topLeft = center - Offset(radius, radius),
                size = Size(diameter, diameter),
                style = stroke
            )
        }
        emblem()
    }
}
