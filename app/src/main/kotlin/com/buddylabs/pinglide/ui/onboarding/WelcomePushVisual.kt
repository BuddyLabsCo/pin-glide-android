package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.buddylabs.pinglide.R

@Composable
internal fun WelcomePushVisual() {
    var motionEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }.collect {
            motionEnabled = it
        }
    }
    val time = if (motionEnabled) {
        rememberInfiniteTransition(label = "Welcome push").animateFloat(
            initialValue = 0f,
            targetValue = 6400f,
            animationSpec = infiniteRepeatable(tween(6400, easing = LinearEasing)),
            label = "Notification arrival and forwarding"
        )
    } else {
        remember { mutableStateOf(3840f) }
    }
    val backdrop = painterResource(R.drawable.welcome_push)
    val palette = PushPalette(
        colorResource(R.color.primary),
        colorResource(R.color.hero_container),
        colorResource(R.color.surface),
        colorResource(R.color.hero_outline),
        colorResource(R.color.hero_white),
        colorResource(R.color.hero_wing)
    )
    Canvas(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        with(backdrop) { draw(size) }
        scale(size.width / 800f, size.height / 600f, pivot = Offset.Zero) {
            sourceNotification(time.value, palette)
            delivery(time.value, UpperDelivery, palette)
            delivery(time.value, LowerDelivery, palette)
        }
    }
}

private val ArrivalEasing = CubicBezierEasing(.4f, 0f, .2f, 1f)
private val ShapeEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
private val RouteEasing = CubicBezierEasing(.38f, 0f, .22f, 1f)
private val BirdTail = path("M36 56 5 83 33 75 57 60Z")
private val BirdWingTail = path("M40 62 15 88 40 79 60 63Z")
private val BirdBody =
    path("M29 58C41 72 64 70 76 52C87 38 76 22 64 28C52 33 47 45 41 47C35 48 31 51 29 58Z")
private val BirdWing = path("M42 57C20 48 18 26 23 10C43 20 59 38 54 51C52 59 48 62 42 57Z")
private val BirdBeak = path("M77 34 91 40 77 44Z")
private val Checkmark = path("M-5 0 -1 4 6-4")
private val UpperDelivery = PushDelivery(
    1180f,
    2260f,
    156f,
    64f,
    Offset(567f, 156f),
    "M190 258 C292 187 375 174 492 187"
)
private val LowerDelivery = PushDelivery(
    1270f,
    2430f,
    174f,
    66f,
    Offset(634f, 388f),
    "M190 258 C330 258 330 444 550 420"
)

private data class PushPalette(
    val primary: Color,
    val container: Color,
    val surface: Color,
    val outline: Color,
    val white: Color,
    val wing: Color
)

private class PushDelivery(
    val departure: Float,
    val arrival: Float,
    val width: Float,
    val height: Float,
    val confirmation: Offset,
    route: String
) {
    val route = PathMeasure().apply { setPath(path(route), false) }
}

private class BannerShape(time: Float, delivery: PushDelivery) {
    private val collapse = ShapeEasing.transform(
        progress(
            time,
            delivery.departure,
            delivery.departure + 220f
        )
    )
    private val expand = ShapeEasing.transform(
        progress(
            time,
            delivery.arrival,
            delivery.arrival + 270f
        )
    )
    private val settle = ShapeEasing.transform(
        progress(
            time,
            delivery.arrival + 270f,
            delivery.arrival + 430f
        )
    )

    fun value(initial: Float, compact: Float, final: Float, overshoot: Float = 0f): Float =
        initial + (compact - initial) * collapse + (final + overshoot - compact) * expand -
            overshoot * settle
}

private fun DrawScope.sourceNotification(time: Float, palette: PushPalette) {
    val alpha = visibility(time, 400f, 660f, 5240f, 5760f)
    val enter = ArrivalEasing.transform(progress(time, 400f, 980f))
    val exit = ArrivalEasing.transform(progress(time, 5240f, 5760f))
    clipRect(123f, 224f, 257f, 465f) {
        translate(190f, 258f - 68f * (1f - enter + exit)) {
            drawRoundRect(
                palette.primary,
                Offset(-59f, -28f),
                Size(118f, 56f),
                CornerRadius(16f),
                alpha = alpha
            )
            bird(Offset(-49f, -19f), palette.white, alpha, palette)
            bars(1f, 40f, 17f, palette.white, palette.container, alpha)
        }
    }
}

private fun DrawScope.delivery(time: Float, delivery: PushDelivery, palette: PushPalette) {
    val alpha = visibility(time, delivery.departure - 10f, delivery.departure + 55f)
    val distance = RouteEasing.transform(progress(time, delivery.departure, delivery.arrival))
    val position = delivery.route.getPosition(delivery.route.length * distance)
    val shape = BannerShape(time, delivery)
    val sourceColor =
        1f - ShapeEasing.transform(progress(time, delivery.arrival + 60f, delivery.arrival + 480f))
    val textAlpha =
        1f - ArrivalEasing.transform(
            progress(time, delivery.departure, delivery.departure + 160f)
        ) +
            ArrivalEasing.transform(progress(time, delivery.arrival + 55f, delivery.arrival + 300f))
    translate(position.x, position.y) {
        drawRoundRect(
            lerp(palette.container, palette.primary, sourceColor),
            Offset(
                shape.value(-59f, -26f, -delivery.width / 2f, -1.5f),
                shape.value(
                    -28f,
                    -26f,
                    -delivery.height / 2f,
                    -0.5f
                )
            ),
            Size(
                shape.value(118f, 52f, delivery.width, 3f),
                shape.value(56f, 52f, delivery.height, 1f)
            ),
            CornerRadius(shape.value(16f, 18f, 19f)),
            alpha = alpha
        )
        bird(
            Offset(shape.value(-49f, -19f, -delivery.width / 2f + 10f, -1.5f), -19f),
            lerp(palette.primary, palette.white, sourceColor),
            alpha,
            palette
        )
        bars(
            shape.value(1f, 34f, -delivery.width / 2f + 60f, -1.5f),
            shape.value(40f, 1f, delivery.width - 78f, 3f),
            shape.value(17f, 1f, delivery.width - 101f, 3f),
            lerp(palette.primary, palette.white, sourceColor),
            lerp(palette.outline, palette.container, sourceColor),
            alpha * textAlpha
        )
    }
    confirmation(time, delivery, palette)
}

private fun DrawScope.confirmation(time: Float, delivery: PushDelivery, palette: PushPalette) {
    val start = delivery.arrival + 320f
    val alpha = visibility(time, start, start + 230f, 5040f, 5460f)
    val size = .72f + .28f * ShapeEasing.transform(progress(time, start, start + 260f))
    translate(delivery.confirmation.x, delivery.confirmation.y) {
        scale(size, pivot = Offset.Zero) {
            drawCircle(palette.surface, 12f, Offset.Zero, alpha)
            drawCircle(palette.container, 10f, Offset.Zero, alpha)
            drawPath(
                Checkmark,
                palette.primary,
                alpha,
                Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun DrawScope.bird(position: Offset, color: Color, alpha: Float, palette: PushPalette) {
    translate(position.x, position.y) {
        scale(.38f, pivot = Offset.Zero) {
            drawPath(BirdTail, color, alpha)
            drawPath(BirdWingTail, palette.wing, alpha)
            drawPath(BirdBody, color, alpha)
            drawPath(BirdWing, palette.wing, alpha)
            drawPath(BirdBeak, color, alpha)
            drawCircle(palette.surface, 2f, Offset(71f, 35f), alpha)
        }
    }
}

private fun DrawScope.bars(
    left: Float,
    topWidth: Float,
    bottomWidth: Float,
    topColor: Color,
    bottomColor: Color,
    alpha: Float
) {
    drawRoundRect(topColor, Offset(left, -11f), Size(topWidth, 6f), CornerRadius(3f), alpha = alpha)
    drawRoundRect(
        bottomColor,
        Offset(left, 6f),
        Size(bottomWidth, 5f),
        CornerRadius(2.5f),
        alpha = alpha
    )
}

private fun progress(time: Float, start: Float, end: Float): Float =
    ((time - start) / (end - start)).coerceIn(0f, 1f)

private fun visibility(
    time: Float,
    fadeInStart: Float,
    fadeInEnd: Float,
    fadeOutStart: Float = 5040f,
    fadeOutEnd: Float = 5540f
): Float = ArrivalEasing.transform(progress(time, fadeInStart, fadeInEnd)) *
    (1f - ArrivalEasing.transform(progress(time, fadeOutStart, fadeOutEnd)))

private fun path(data: String) = PathParser().parsePathString(data).toPath()
