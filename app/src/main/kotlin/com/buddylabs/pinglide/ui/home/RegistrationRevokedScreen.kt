package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.RevokedRegistrationMessage
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun RegistrationRevokedScreen(onNewRegistration: () -> Unit) {
    val title = stringResource(R.string.home_revoked_title)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ScreenLayout {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)
                    .padding(top = 28.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RevokedRegistrationMessage(
                    title,
                    stringResource(R.string.registration_revoked_description),
                    Modifier.focusRequester(focus).focusable().semantics { heading() }
                )
            }
        }
        Button(
            onClick = onNewRegistration,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 48.dp)
        ) {
            HomeActionLabel(R.drawable.ic_account_circle, R.string.registration_start_again)
        }
    }
}
