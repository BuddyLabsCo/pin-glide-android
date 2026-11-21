package com.buddylabs.pinglide.ui.home

import android.icu.text.RelativeDateTimeFormatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
    val outline by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "Pairing scan outline"
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer)
            .border(2.dp, outline, shape).padding(horizontal = 12.dp, vertical = 10.dp),
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
            TargetCardDetails(target, name, now, highlighted)
        }
        val switchLabel = stringResource(R.string.home_receive, name)
        Switch(
            checked = target.enabled,
            onCheckedChange = onSelect,
            enabled = enabled && target.keyUsable,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.surfaceContainerHigh,
                uncheckedBorderColor = colors.outline
            ),
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = switchLabel }
        )
    }
}

@Composable
private fun TargetCardDetails(
    target: TargetDevice,
    name: String,
    now: Instant,
    highlighted: Boolean
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
        if (highlighted) {
            Text(
                stringResource(R.string.pairing_already_added),
                color = colors.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
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
