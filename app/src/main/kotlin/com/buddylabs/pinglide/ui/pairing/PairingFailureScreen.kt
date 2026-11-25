package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.ui.components.RingedEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun PairingFailureScreen(
    state: PairingEntryState,
    onRetry: () -> Unit,
    onLeave: () -> Unit
) {
    val onboarding = state.host == PairingHost.Onboarding
    val title = stringResource(R.string.pairing_failed_title)
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).padding(top = 12.dp, bottom = 44.dp)
                    .semantics { paneTitle = title },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PairingFailureContent(title, onboarding)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRetry,
                enabled = !state.pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.pairing_failure_retry), fontWeight = FontWeight.Bold)
            }
            TextButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(
                    stringResource(
                        if (onboarding) R.string.pairing_later else R.string.pairing_home_return
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PairingFailureContent(title: String, onboarding: Boolean) {
    val warning = colorResource(R.color.setup_on_warning)
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(title) { titleFocus.requestFocus() }
    RingedEmblem(
        RoundedCornerShape(44.dp),
        containerColor = colorResource(R.color.setup_warning_container)
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), null, Modifier.size(54.dp), tint = warning)
    }
    Row(
        modifier = Modifier.padding(top = 22.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(18.dp, 2.dp).background(warning, RoundedCornerShape(1.dp)))
        Text(
            stringResource(
                if (onboarding) {
                    R.string.pairing_failure_uncommitted
                } else {
                    R.string.pairing_failure_inactive
                }
            ),
            color = warning,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.21.sp
            )
        )
    }
    Text(
        title,
        modifier = Modifier.focusRequester(titleFocus).focusable().semantics { heading() },
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-1.17).sp,
            textAlign = TextAlign.Center
        )
    )
    Text(
        stringResource(
            if (onboarding) {
                R.string.pairing_failure_onboarding_copy
            } else {
                R.string.pairing_failure_home_copy
            }
        ),
        modifier = Modifier.padding(top = 4.dp).widthIn(max = 306.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center
        )
    )
}
