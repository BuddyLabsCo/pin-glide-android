package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState

@Composable
internal fun HomeHero(state: HomeState, readiness: HomeReadiness) {
    val strings = heroStrings(state, readiness)
    val ready = readiness == HomeReadiness.Ready && !state.failed
    Surface(
        color = if (ready) Color(0xFF006B63) else colorResource(R.color.home_warning_container),
        contentColor = if (ready) Color.White else colorResource(R.color.home_on_warning),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(strings.first),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(strings.second), style = MaterialTheme.typography.bodyMedium)
        }
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
