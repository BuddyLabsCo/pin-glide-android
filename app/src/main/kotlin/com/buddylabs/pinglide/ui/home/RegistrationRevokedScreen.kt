package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun RegistrationRevokedScreen(onNewRegistration: () -> Unit) {
    val title = stringResource(R.string.home_revoked_title)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ScreenLayout {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = 32.dp)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colorResource(R.color.registration_error_container)
            ) {
                Icon(
                    painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = colorResource(R.color.registration_error),
                    modifier = Modifier.padding(21.dp).size(30.dp)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.focusRequester(focus).focusable().semantics { heading() }
            )
            Text(
                stringResource(R.string.registration_revoked_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onNewRegistration,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 56.dp)
        ) {
            Icon(painterResource(R.drawable.ic_account), contentDescription = null)
            Text(
                stringResource(R.string.registration_start_again),
                modifier = Modifier.padding(start = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
