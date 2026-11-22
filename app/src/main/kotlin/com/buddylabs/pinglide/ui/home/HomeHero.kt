package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState

@Composable
internal fun HomeHero(state: HomeState, readiness: HomeReadiness) {
    val strings = heroStrings(state, readiness)
    if (readiness == HomeReadiness.Empty) {
        EmptyHero(strings)
        return
    }
    val ready = readiness == HomeReadiness.Ready && !state.failed
    Surface(
        color = if (ready) Color.Transparent else colorResource(R.color.home_warning_container),
        contentColor = if (ready) Color.White else colorResource(R.color.home_on_warning),
        shape = RoundedCornerShape(if (ready) 28.dp else 24.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        val style = if (ready) readyHeroStyle() else Modifier
        HeroCopy(strings, ready, style.padding(if (ready) 18.dp else 24.dp))
    }
}

@Composable
private fun HeroCopy(strings: Pair<Int, Int>, prominent: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (prominent) 8.dp else 12.dp)) {
        Text(
            stringResource(strings.first),
            style = if (prominent) {
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight(770),
                    letterSpacing = (-1.4).sp
                )
            } else {
                MaterialTheme.typography.headlineSmall
            },
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(strings.second),
            style = if (prominent) {
                MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = if (prominent) Modifier.widthIn(max = 248.dp) else Modifier
        )
    }
}

@Composable
private fun EmptyHero(strings: Pair<Int, Int>) {
    val colors = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(
        listOf(colorResource(R.color.setup_warning_container), colors.surfaceContainerHigh)
    )
    Surface(
        color = Color.Transparent,
        contentColor = if (isSystemInDarkTheme()) Color.White else colors.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        BoxWithConstraints(heroBackdrop(gradient).padding(18.dp)) {
            if (maxWidth < 264.dp * LocalDensity.current.fontScale) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroCopy(strings, prominent = true)
                    WaitingBadge()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroCopy(strings, prominent = true, modifier = Modifier.weight(1f))
                    WaitingBadge()
                }
            }
        }
    }
}

@Composable
private fun WaitingBadge() {
    val foreground = colorResource(R.color.setup_on_warning)
    Surface(
        color = colorResource(R.color.setup_warning_container),
        contentColor = foreground,
        shape = CircleShape
    ) {
        Box(
            Modifier.heightIn(min = 28.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.size(6.dp).background(foreground, CircleShape))
            Text(
                stringResource(R.string.home_waiting_for_devices),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight(750)
            )
        }
    }
}

@Composable
private fun readyHeroStyle(): Modifier {
    val colors = MaterialTheme.colorScheme
    val gradient = if (isSystemInDarkTheme()) {
        Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer))
    } else {
        Brush.linearGradient(listOf(colors.primary, Color(0xFF155B78)))
    }
    return heroBackdrop(gradient)
}

@Composable
private fun heroBackdrop(gradient: Brush): Modifier {
    val colors = MaterialTheme.colorScheme
    return Modifier.fillMaxWidth().heightIn(min = 128.dp).background(gradient).drawBehind {
        val center = Offset(size.width - 42.dp.toPx(), size.height - 10.dp.toPx())
        val radius = 118.dp.toPx()
        drawCircle(colors.primary.copy(alpha = 0.05f), radius + 24.dp.toPx(), center)
        drawCircle(colors.primary.copy(alpha = 0.20f), radius, center, style = Stroke(1.dp.toPx()))
    }
}

private fun heroStrings(state: HomeState, readiness: HomeReadiness): Pair<Int, Int> =
    when (readiness) {
        HomeReadiness.Revoked -> R.string.home_revoked_title to R.string.home_revoked_copy

        HomeReadiness.LoginRequired -> R.string.home_login_title to R.string.home_login_copy

        HomeReadiness.SmsRequired -> R.string.home_sms_title to R.string.home_sms_copy

        HomeReadiness.Empty -> R.string.home_empty_title to R.string.home_empty_copy

        HomeReadiness.Disabled -> R.string.home_disabled_title to R.string.home_disabled_copy

        HomeReadiness.Offline -> R.string.home_offline_title to R.string.home_offline_copy

        HomeReadiness.Unknown -> if (state.failed) {
            R.string.home_load_error_title to R.string.home_load_error_copy
        } else {
            R.string.home_loading_title to R.string.home_loading_copy
        }

        HomeReadiness.Ready -> if (state.failed) {
            R.string.home_refresh_error_title to R.string.home_refresh_error_copy
        } else {
            R.string.home_ready_title to R.string.home_ready_copy
        }
    }
