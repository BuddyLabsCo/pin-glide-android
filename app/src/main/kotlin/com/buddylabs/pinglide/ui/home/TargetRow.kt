package com.buddylabs.pinglide.ui.home

import android.icu.text.RelativeDateTimeFormatter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TargetOutlineOutset = 4.dp
private val TargetOutlineWidth = 2.dp
internal val TargetOutlineClearance = TargetOutlineOutset + TargetOutlineWidth / 2

@Composable
internal fun TargetRow(
    target: TargetDevice,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSelect: (Boolean) -> Unit,
    now: Instant,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val name = target.alias.ifBlank { stringResource(R.string.home_target_device) }
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = if (highlighted) snap() else tween(240),
        label = "Pairing scan outline"
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().scanHalo(shape, colors.primary, highlightAlpha)
            .clip(shape).background(colors.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier.weight(1f).heightIn(min = 54.dp)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_details),
                    onClick = onOpen
                ),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TargetCardDetails(target, name, now, highlightAlpha)
        }
        TargetReceiveSwitch(
            checked = target.enabled,
            enabled = enabled && target.keyUsable,
            label = stringResource(R.string.home_receive, name),
            onSelect = onSelect
        )
    }
}

@Composable
private fun TargetReceiveSwitch(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onSelect: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onSelect,
        enabled = enabled,
        thumbContent = if (!checked && enabled) {
            { Box(Modifier.size(22.dp).background(colors.onSurfaceVariant, CircleShape)) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            uncheckedThumbColor = Color.Transparent,
            uncheckedTrackColor = colors.surfaceContainerHigh,
            uncheckedBorderColor = colors.onSurface.copy(
                alpha = if (isSystemInDarkTheme()) 0.32f else 0.34f
            )
        ),
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }
    )
}

@Composable
private fun Modifier.scanHalo(shape: RoundedCornerShape, color: Color, opacity: Float): Modifier {
    if (opacity == 0f) return this
    val pulse by rememberInfiniteTransition(label = "Pairing scan halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "Halo pulse"
    )
    return dropShadow(shape) {
        this.color = color
        radius = (12 + 4 * pulse).dp.toPx()
        alpha = opacity * (0.25f + 0.15f * pulse)
    }.dropShadow(shape) {
        this.color = color
        spread = (6 + 2 * pulse).dp.toPx()
        alpha = opacity * (0.12f + 0.08f * pulse)
    }.drawBehind {
        val outset = TargetOutlineOutset.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + 2 * outset, size.height + 2 * outset),
            cornerRadius = CornerRadius(20.dp.toPx()),
            alpha = opacity,
            style = Stroke(TargetOutlineWidth.toPx())
        )
    }
}

@Composable
private fun TargetCardDetails(
    target: TargetDevice,
    name: String,
    now: Instant,
    highlightAlpha: Float
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(42.dp)
            .background(colors.surfaceContainerHigh, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_computer),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = colors.primary
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(name, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight(720))
        TargetRowPresence(target, now)
        if (highlightAlpha > 0f) {
            Text(
                stringResource(R.string.home_just_scanned),
                color = colors.primary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.alpha(highlightAlpha)
            )
        }
        if (!target.keyUsable) {
            Text(
                stringResource(R.string.home_key_unavailable),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TargetRowPresence(target: TargetDevice, now: Instant) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (target.presence != Presence.Unknown && target.stateChangedAt != null) {
            val dot = if (target.presence == Presence.Online) {
                colorResource(R.color.home_online)
            } else {
                muted
            }
            Box(Modifier.size(6.dp).background(dot, CircleShape))
        }
        Text(targetPresence(target, now), fontSize = 11.sp, lineHeight = 16.sp, color = muted)
    }
}

@Composable
internal fun targetPresence(target: TargetDevice, now: Instant): String {
    if (target.presence == Presence.Unknown || target.stateChangedAt == null) {
        return stringResource(R.string.home_awaiting_update)
    }
    val status = stringResource(
        if (target.presence == Presence.Online) R.string.home_online else R.string.home_offline
    )
    val timestamp = Instant.parse(target.stateChangedAt)
    val changed = recentPresence(timestamp, now)
    return stringResource(R.string.home_presence, status, changed)
}

@Composable
private fun recentPresence(timestamp: Instant, now: Instant): String {
    val age = Duration.between(timestamp, now)
    val minutes = age.toMinutes()
    return when {
        age.isNegative || minutes >= 60 -> presenceDate(timestamp)

        minutes == 0L -> stringResource(R.string.home_presence_just_now)

        else -> RelativeDateTimeFormatter.getInstance(LocalConfiguration.current.locales[0])
            .format(
                minutes.toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.MINUTES
            )
    }
}

@Composable
private fun presenceDate(timestamp: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(LocalConfiguration.current.locales[0])
        .withZone(ZoneId.systemDefault()).format(timestamp)
