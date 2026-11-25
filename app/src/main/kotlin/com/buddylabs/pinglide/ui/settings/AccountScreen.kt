package com.buddylabs.pinglide.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.AccountState

@Composable
internal fun AccountScreen(account: AccountSession, onLogout: () -> Unit) {
    val state by account.state.collectAsState()
    LaunchedEffect(account) { account.open() }
    val context = LocalContext.current
    DisposableEffect(state.failed, context) {
        val toast = if (state.failed) {
            Toast.makeText(context, R.string.account_error, Toast.LENGTH_LONG).also { it.show() }
        } else {
            null
        }
        onDispose { toast?.cancel() }
    }
    AccountScreenContent(state, onLogout)
}

@Composable
internal fun AccountScreenContent(state: AccountState, onLogout: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val logoutFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountProfileCard(state)
        AccountLogoutAction(Modifier.focusRequester(logoutFocus)) { confirming = true }
    }
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

@Composable
private fun AccountProfileCard(state: AccountState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsQqBadge()
            Column(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    when {
                        state.loading -> stringResource(R.string.account_loading)
                        state.failed -> stringResource(R.string.settings_account)
                        else -> state.nickname ?: stringResource(R.string.settings_account)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp
                )
                if (!state.loading && !state.failed && state.nickname != null) {
                    Text(
                        stringResource(R.string.settings_account),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountLogoutAction(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.error
    ) {
        SettingsListRow(
            title = stringResource(R.string.account_logout),
            modifier = modifier.clickable(role = Role.Button, onClick = onClick),
            leading = { Icon(painterResource(R.drawable.ic_logout), null, Modifier.size(20.dp)) },
            trailing = { Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp)) }
        )
    }
}
