package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val ready = readiness == HomeReadiness.Ready && !state.failed
    Surface(
        color = if (ready) Color.Transparent else colorResource(R.color.home_warning_container),
        contentColor = if (ready) Color.White else colorResource(R.color.home_on_warning),
        shape = RoundedCornerShape(if (ready) 28.dp else 24.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        val style = if (ready) readyHeroStyle() else Modifier
        Column(
            style.padding(if (ready) 18.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (ready) 8.dp else 12.dp)
        ) {
            Text(
                stringResource(strings.first),
                style = if (ready) {
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
                style = if (ready) {
                    MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = if (ready) Modifier.widthIn(max = 248.dp) else Modifier
            )
        }
    }
}

@Composable
private fun readyHeroStyle(): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val gradient = if (dark) {
        Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer))
    } else {
        Brush.linearGradient(listOf(colors.primary, Color(0xFF155B78)))
    }
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
