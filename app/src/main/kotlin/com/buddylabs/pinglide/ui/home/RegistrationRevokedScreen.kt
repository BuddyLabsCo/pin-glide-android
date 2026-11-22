package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
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
                Surface(
                    modifier = Modifier.padding(bottom = 18.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = colorResource(R.color.error_emblem_container)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_registration_revoked_lock),
                        contentDescription = null,
                        tint = colorResource(R.color.error_emblem),
                        modifier = Modifier.padding(21.dp).size(30.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.04).sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.focusRequester(focus).focusable().semantics { heading() }
                )
                Text(
                    stringResource(R.string.registration_revoked_description),
                    modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
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
