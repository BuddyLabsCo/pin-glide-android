package com.buddylabs.pinglide.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R

@Composable
internal fun HomeEmptyCard(onAdd: () -> Unit) {
    HomeIllustratedCard(
        R.drawable.ic_computer,
        R.string.home_no_devices,
        R.string.home_add_instructions
    ) {
        FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_qr), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.home_add_first),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeIllustratedCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes copy: Int,
    modifier: Modifier = Modifier,
    copyTracking: TextUnit = MaterialTheme.typography.bodySmall.letterSpacing,
    action: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().dashedOutline().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).background(colors.primaryContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                null,
                Modifier.size(30.dp),
                colors.primary
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight(730)
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(copy),
            modifier = Modifier.widthIn(max = 240.dp),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
                letterSpacing = copyTracking
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        action()
    }
}

@Composable
internal fun HomeLoadingCard() {
    val colors = MaterialTheme.colorScheme
    val rotation = rememberInfiniteTransition(label = "Device list").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "Loading arc rotation"
    )
    Column(
        Modifier.fillMaxWidth().heightIn(min = 280.dp).dashedOutline().padding(28.dp)
            .progressSemantics().semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(20.dp)) {
            val width = 2.dp.toPx()
            drawArc(
                color = colors.onSurface,
                startAngle = 45f + rotation.value,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width)
            )
        }
        Text(
            stringResource(R.string.home_loading_region),
            modifier = Modifier.widthIn(max = 240.dp).padding(top = 8.dp, bottom = 20.dp),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun HomeListUnavailable() {
    HomeIllustratedCard(
        R.drawable.ic_cloud_off,
        R.string.home_load_error_region_title,
        R.string.home_load_error_region,
        Modifier.heightIn(min = 280.dp).semantics { liveRegion = LiveRegionMode.Polite },
        copyTracking = 0.sp
    )
}

@Composable
private fun Modifier.dashedOutline(): Modifier {
    val color = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (isSystemInDarkTheme()) 0.32f else 0.34f
    )
    return drawWithCache {
        val strokeWidth = 1.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
        )
        val inset = strokeWidth / 2
        onDrawBehind {
            drawRoundRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(28.dp.toPx() - inset),
                style = stroke
            )
        }
    }
}
