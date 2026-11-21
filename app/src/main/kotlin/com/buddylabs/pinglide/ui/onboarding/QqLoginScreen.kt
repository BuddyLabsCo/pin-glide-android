package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.ui.components.QqEmblem
import com.buddylabs.pinglide.ui.components.QqLoadingEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun QqLoginScreen(
    state: LoginState,
    onLogin: () -> Unit,
    onNewAccount: () -> Unit,
    onCancel: () -> Unit,
    onRetryStorage: () -> Unit,
    onBack: () -> Unit
) {
    when (state) {
        LoginState.Restoring, LoginState.Loading -> LoginBusyScreen(state, onCancel, onBack)
        LoginState.FirstDevice -> Unit
        else -> LoginActionScreen(state, onLogin, onNewAccount, onCancel, onRetryStorage, onBack)
    }
}

@Composable
private fun LoginActionScreen(
    state: LoginState,
    onLogin: () -> Unit,
    onNewAccount: () -> Unit,
    onCancel: () -> Unit,
    onRetryStorage: () -> Unit,
    onBack: () -> Unit
) {
    val storageError = state == LoginState.StorageError
    val error = state != LoginState.Entry
    SetupStepScreen(
        step = 3,
        icon = R.drawable.ic_info,
        title = when {
            storageError -> R.string.setup_identity_unavailable_title
            error -> R.string.setup_qq_error_title
            else -> R.string.setup_qq_title
        },
        description = when {
            storageError -> R.string.setup_identity_unavailable_description
            state == LoginState.RestorationError -> R.string.account_restore_error
            error -> R.string.setup_qq_error_description
            else -> R.string.setup_qq_description
        },
        onBack = onBack,
        error = error,
        emblem = if (error) {
            null
        } else {
            { QqEmblem() }
        },
        notice = {
            if (!error) {
                Text(
                    stringResource(R.string.setup_qq_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    ) {
        LoginActions(state, onLogin, onNewAccount, onCancel, onRetryStorage)
    }
}

@Composable
private fun LoginActions(
    state: LoginState,
    onLogin: () -> Unit,
    onNewAccount: () -> Unit,
    onCancel: () -> Unit,
    onRetryStorage: () -> Unit
) {
    val storageError = state == LoginState.StorageError
    val error = state != LoginState.Entry
    Button(
        onClick = if (storageError) onRetryStorage else onLogin,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text(
            stringResource(
                when {
                    storageError -> R.string.setup_check_again
                    error -> R.string.setup_qq_retry
                    else -> R.string.setup_qq_sign_in
                }
            )
        )
    }
    if (state == LoginState.RestorationError) {
        TextButton(
            onClick = onNewAccount,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.account_new_login))
        }
    }
    if (state == LoginState.Error) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.setup_back))
        }
    }
}

@Composable
private fun LoginBusyScreen(state: LoginState, onCancel: () -> Unit, onBack: () -> Unit) {
    val signingIn = state == LoginState.Loading
    val title = stringResource(
        if (signingIn) R.string.setup_qq_loading else R.string.setup_identity_loading
    )
    ScreenLayout {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .then(if (signingIn) Modifier.padding(top = 20.dp, bottom = 26.dp) else Modifier)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (signingIn) 44.dp else 24.dp,
                Alignment.CenterVertically
            )
        ) {
            if (signingIn) {
                QqLoadingEmblem()
            } else {
                CircularProgressIndicator()
            }
            Text(
                title,
                style = if (signingIn) {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight(750),
                        letterSpacing = (-1.17).sp
                    )
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
        }
        TextButton(
            onClick = if (signingIn) onCancel else onBack,
            modifier = Modifier.fillMaxWidth().padding(vertical = if (signingIn) 0.dp else 16.dp)
                .heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(if (signingIn) R.string.setup_cancel else R.string.setup_back),
                fontWeight = if (signingIn) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = if (signingIn) 0.14.sp else 0.1.sp
            )
        }
    }
}
