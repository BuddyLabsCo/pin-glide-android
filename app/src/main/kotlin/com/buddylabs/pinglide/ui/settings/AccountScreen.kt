package com.buddylabs.pinglide.ui.settings

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.AccountState
import com.buddylabs.pinglide.ui.components.ConfirmationDialog
import com.buddylabs.pinglide.ui.components.ConfirmationDialogEmblem
import com.buddylabs.pinglide.ui.components.ConfirmationDialogHeading
import kotlinx.coroutines.delay

@Composable
internal fun AccountScreen(account: AccountSession, onBack: () -> Unit, onLogout: () -> Unit) {
    val state by account.state.collectAsState()
    LaunchedEffect(account) { account.open() }
    val noticeGeneration = rememberFailureNotice(state.failureGeneration)
    AccountPage(noticeGeneration, onBack) {
        AccountScreenContent(state, onLogout)
    }
}

@Composable
private fun rememberFailureNotice(failureGeneration: Long?): Long? {
    val accessibility = LocalAccessibilityManager.current
    var noticeGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var deadline by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(failureGeneration) {
        if (failureGeneration != noticeGeneration) {
            noticeGeneration = failureGeneration
            val now = SystemClock.elapsedRealtime()
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                4_000L,
                containsText = true
            ) ?: 4_000L
            deadline = if (failureGeneration != null) {
                now + timeout.coerceAtMost(Long.MAX_VALUE - now)
            } else {
                0L
            }
        }
        if (failureGeneration != null && deadline != 0L) {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            deadline = 0L
        }
    }
    return failureGeneration?.takeIf {
        it == noticeGeneration && deadline > SystemClock.elapsedRealtime()
    }
}

@Composable
private fun AccountScreenContent(state: AccountState, onLogout: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val logoutFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountProfileCard(state)
        AccountLogoutAction(Modifier.focusRequester(logoutFocus)) { confirming = true }
    }
    if (confirming) {
        LogoutConfirmationDialog(
            onCancel = {
                confirming = false
                logoutFocus.requestFocus()
            },
            onLogout = {
                confirming = false
                onLogout()
            }
        )
    }
}

@Composable
private fun LogoutConfirmationDialog(onCancel: () -> Unit, onLogout: () -> Unit) {
    val title = stringResource(R.string.account_logout_title)
    val colors = MaterialTheme.colorScheme
    ConfirmationDialog(
        pane = title,
        description = stringResource(R.string.account_logout_description),
        onDismiss = onCancel,
        emblem = {
            ConfirmationDialogEmblem(
                painterResource(R.drawable.ic_logout),
                colors.primaryContainer,
                colors.primary,
                24.dp
            )
        },
        heading = { ConfirmationDialogHeading(title) }
    ) {
        LogoutConfirmationActions(onCancel, onLogout)
    }
}

@Composable
private fun LogoutConfirmationActions(onCancel: () -> Unit, onLogout: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val padding = PaddingValues(horizontal = 22.dp)
    TextButton(
        onClick = onCancel,
        modifier = Modifier.heightIn(min = 48.dp).focusRequester(cancelFocus),
        contentPadding = padding
    ) {
        Text(stringResource(R.string.setup_cancel), fontWeight = FontWeight.Bold)
    }
    Button(
        onClick = onLogout,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = padding
    ) {
        Text(stringResource(R.string.account_logout), fontWeight = FontWeight.Bold)
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
}

@Composable
private fun AccountProfileCard(state: AccountState) {
    SettingsCard(Modifier.testTag("settings-account-profile")) {
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
                val hasNickname = !state.loading && !state.failed && state.nickname != null
                Text(
                    when {
                        state.loading -> stringResource(R.string.account_loading)
                        state.failed -> stringResource(R.string.settings_account)
                        else -> state.nickname ?: stringResource(R.string.settings_account)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    maxLines = if (hasNickname) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasNickname) {
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
    SettingsCard(
        modifier = Modifier.testTag("settings-account-logout"),
        contentColor = colorResource(R.color.error_emblem)
    ) {
        SettingsListRow(
            title = stringResource(R.string.account_logout),
            modifier = modifier.clickable(role = Role.Button, onClick = onClick),
            leading = { Icon(painterResource(R.drawable.ic_logout), null, Modifier.size(20.dp)) },
            trailing = { Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp)) }
        )
    }
}
