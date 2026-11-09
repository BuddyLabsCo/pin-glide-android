package com.buddylabs.pinglide.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.ui.components.QqEmblem
import com.buddylabs.pinglide.ui.components.QqLoadingEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun HomeLoginScreen(
    state: RestorationState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    val strings = when (state) {
        RestorationState.Loading -> R.string.home_login_loading to R.string.home_login_loading_copy

        RestorationState.Cancelled ->
            R.string.home_login_cancelled to R.string.home_login_cancelled_copy

        RestorationState.AccountMismatch ->
            R.string.home_login_mismatch to R.string.home_login_mismatch_copy

        else -> R.string.home_login_error to R.string.home_login_error_copy
    }
    val title = stringResource(strings.first)
    val titleFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(state) { titleFocus.requestFocus() }
    ScreenLayout {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.setup_back))
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp).semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state == RestorationState.Loading) QqLoadingEmblem() else QqEmblem()
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.focusRequester(titleFocus).focusable()
                    .semantics { heading() }
            )
            Text(
                stringResource(strings.second),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
        Column(Modifier.fillMaxWidth().imePadding().padding(vertical = 16.dp)) {
            if (state == RestorationState.Loading) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.setup_cancel))
                }
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.home_login_action))
                }
            }
        }
    }
}

@Composable
internal fun LoginRestorationNotice(
    state: RestorationState,
    refreshing: Boolean,
    onDismiss: () -> Unit
) {
    val message = stringResource(R.string.home_login_restored)
    val context = LocalContext.current
    LaunchedEffect(state, refreshing) {
        if (state == RestorationState.Restored && !refreshing) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }
}
