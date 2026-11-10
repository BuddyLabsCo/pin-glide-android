package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.ui.components.QqEmblem

@Composable
internal fun AccountScreen(account: AccountSession, onLogout: () -> Unit) {
    val state by account.state.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val logoutFocus = remember { FocusRequester() }
    LaunchedEffect(account) { account.open() }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QqEmblem()
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    when {
                        state.loading -> stringResource(R.string.account_loading)
                        state.failed -> stringResource(R.string.account_error)
                        else -> state.nickname ?: stringResource(R.string.settings_account)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.nickname != null) Text(stringResource(R.string.settings_account))
            }
        }
    }
    if (state.failed) {
        TextButton(onClick = account::retry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.setup_check_again))
        }
    }
    TextButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(logoutFocus),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) { Text(stringResource(R.string.account_logout)) }
    if (confirming) {
        val cancelFocus = remember { FocusRequester() }
        val dismiss: () -> Unit = {
            confirming = false
            logoutFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.account_logout_title)) },
            text = {
                Text(
                    stringResource(R.string.account_logout_description),
                    Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirming = false
                    onLogout()
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.account_logout))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = dismiss,
                    modifier = Modifier.heightIn(min = 48.dp).focusRequester(cancelFocus)
                ) {
                    Text(stringResource(R.string.setup_cancel))
                }
                LaunchedEffect(Unit) { cancelFocus.requestFocus() }
            }
        )
    }
}
