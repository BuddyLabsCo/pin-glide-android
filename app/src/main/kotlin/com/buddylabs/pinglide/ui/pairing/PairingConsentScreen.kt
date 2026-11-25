package com.buddylabs.pinglide.ui.pairing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.ui.components.PairingAliasField
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun PairingConsentScreen(
    state: PairingEntryState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onAliasChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRescan: () -> Unit
) {
    val title = stringResource(R.string.pairing_consent_title)
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < 360.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val scroll = Modifier.verticalScroll(rememberScrollState())
            Column(
                Modifier.fillMaxSize().then(if (compact) scroll else Modifier)
                    .semantics { paneTitle = title }
            ) {
                ConsentHeader(title, onBack)
                ConsentBody(
                    state,
                    onAliasChange,
                    onRescan,
                    if (compact) Modifier else Modifier.weight(1f).then(scroll)
                )
                ConsentActions(state, onConfirm, onCancel)
            }
        }
    }
}

@Composable
private fun ConsentHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.padding(top = 16.dp, bottom = 8.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_back_chevron),
                contentDescription = stringResource(R.string.setup_back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontSize = 20.sp,
            letterSpacing = (-0.7).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun ConsentBody(
    state: PairingEntryState,
    onAliasChange: (String) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.padding(top = 10.dp, bottom = 14.dp)) {
        ConsentIllustration()
        Spacer(Modifier.height(16.dp))
        ConsentDescription(state.host)
        Spacer(Modifier.height(18.dp))
        ConsentAliasField(state, onAliasChange)
        Spacer(Modifier.height(12.dp))
        ConsentCountdown(state.remainingSeconds)
        if (state.confirmed) {
            Text(
                stringResource(R.string.pairing_confirmed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        state.problem?.let { PairingProblem(it, onRescan) }
    }
}

@Composable
private fun ConsentDescription(host: PairingHost) {
    val title = if (host == PairingHost.Onboarding) {
        R.string.pairing_connect_title
    } else {
        R.string.pairing_add_title
    }
    Text(
        stringResource(title),
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-1.17).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.pairing_consent_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
}

@Composable
private fun ConsentIllustration() {
    val colors = MaterialTheme.colorScheme
    val tileShape = RoundedCornerShape(22.dp)
    val shadow = Shadow(
        radius = 24.dp,
        offset = DpOffset(0.dp, 9.dp),
        color = Color.Black,
        alpha = if (isSystemInDarkTheme()) 0.38f else 0.15f
    )
    Box(
        Modifier.fillMaxWidth().height(214.dp).background(
            Brush.linearGradient(
                listOf(
                    colors.surfaceContainer,
                    lerp(colors.surfaceContainer, colors.primaryContainer, .54f)
                )
            ),
            RoundedCornerShape(28.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.widthIn(max = 244.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(R.mipmap.ic_launcher),
                null,
                Modifier.size(70.dp).dropShadow(tileShape, shadow).dropShadow(
                    tileShape,
                    Shadow(
                        radius = 12.dp,
                        offset = DpOffset(0.dp, 5.dp),
                        color = Color(0xFF0A1210),
                        alpha = 0.12f
                    )
                ).clip(tileShape)
            )
            ConsentLink(Modifier.weight(1f).height(18.dp))
            Box(
                Modifier.size(70.dp).dropShadow(tileShape, shadow)
                    .background(colors.surface, tileShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_computer),
                    null,
                    Modifier.size(30.dp),
                    colors.primary
                )
            }
        }
    }
}

@Composable
private fun ConsentLink(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = .62f)
    var motionEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val motion = coroutineContext[MotionDurationScale]
        snapshotFlow { (motion?.scaleFactor ?: 1f) > 0f }.collect { motionEnabled = it }
    }
    val phase = if (motionEnabled) {
        rememberInfiniteTransition(label = "Pairing link").animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "Pairing packets"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Canvas(modifier) {
        val lineY = 1.dp.toPx()
        drawLine(
            color,
            Offset(0f, lineY),
            Offset(size.width, lineY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
        if (motionEnabled) {
            repeat(2) { index ->
                val progress = (phase.value + index * .5f) % 1f
                val fade = minOf(progress / .15f, (1f - progress) / .15f, 1f)
                val alpha = FastOutSlowInEasing.transform(fade)
                val position = when {
                    progress < .15f -> FastOutSlowInEasing.transform(progress / .15f) / 14f

                    progress < .85f -> (
                        1f +
                            12f * FastOutSlowInEasing.transform((progress - .15f) / .7f)
                        ) /
                        14f

                    else -> (13f + FastOutSlowInEasing.transform((progress - .85f) / .15f)) / 14f
                }
                val radius = 4.dp.toPx()
                val travel = radius + position * (size.width - 2 * radius)
                val center = Offset(if (index == 0) travel else size.width - travel, lineY)
                val scale = .7f + .3f * alpha
                drawCircle(
                    color.copy(alpha = .14f * color.alpha * alpha),
                    radius * 2 * scale,
                    center
                )
                drawCircle(color.copy(alpha = color.alpha * alpha), radius * scale, center)
            }
        }
    }
}

@Composable
private fun ConsentAliasField(state: PairingEntryState, onAliasChange: (String) -> Unit) {
    val guidance = stringResource(
        when (state.aliasError) {
            PairingAliasError.Blank -> R.string.home_alias_blank
            PairingAliasError.TooLong -> R.string.home_alias_too_long
            PairingAliasError.InvalidCharacters -> R.string.home_alias_invalid_characters
            PairingAliasError.Duplicate -> R.string.home_alias_duplicate
            null -> R.string.pairing_alias_help
        }
    )
    PairingAliasField(
        alias = state.alias,
        guidance = guidance,
        invalid = state.aliasError != null,
        enabled = !state.pending && !state.confirmed && state.problem == null,
        onAliasChange = onAliasChange
    )
}

@Composable
private fun ConsentCountdown(seconds: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_key),
            null,
            Modifier.size(20.dp),
            tint = colors.onSurfaceVariant
        )
        Spacer(Modifier.width(24.dp))
        Text(
            stringResource(R.string.pairing_remaining_label),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.pairing_remaining_time, seconds / 60, seconds % 60),
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
private fun ConsentActions(state: PairingEntryState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val navigationInset =
        with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Column(
        Modifier.fillMaxWidth().padding(bottom = (22.dp - navigationInset).coerceAtLeast(0.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onConfirm,
            enabled =
                !state.pending && !state.confirmed && state.aliasError == null &&
                    state.problem == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Icon(painterResource(R.drawable.ic_key), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            val label = when {
                state.pending -> R.string.pairing_validating
                state.host == PairingHost.Onboarding -> R.string.pairing_confirm_first
                else -> R.string.pairing_confirm
            }
            Text(stringResource(label), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.pairing_cancel), fontWeight = FontWeight.Bold)
        }
    }
}
