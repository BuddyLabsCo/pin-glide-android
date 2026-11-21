package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
        LoginState.Loading -> {
            val title = stringResource(R.string.setup_qq_loading)
            LoginProgressLayout(
                title = title,
                spacing = 44.dp,
                contentPadding = PaddingValues(top = 20.dp, bottom = 8.dp),
                action = {
                    LoginBusyAction(
                        label = stringResource(R.string.setup_cancel),
                        onClick = onCancel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.14.sp
                        )
                    )
                }
            ) {
                QqLoadingEmblem()
                LoginBusyHeading(
                    title,
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight(750),
                        letterSpacing = (-1.17).sp
                    )
                )
            }
        }

        LoginState.Restoring -> {
            val title = stringResource(R.string.setup_identity_loading)
            LoginProgressLayout(
                title = title,
                spacing = 24.dp,
                action = {
                    LoginBusyAction(
                        label = stringResource(R.string.setup_back),
                        onClick = onBack,
                        verticalPadding = 16.dp
                    )
                }
            ) {
                CircularProgressIndicator()
                LoginBusyHeading(title, MaterialTheme.typography.headlineLarge)
            }
        }

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
private fun LoginProgressLayout(
    title: String,
    spacing: Dp,
    contentPadding: PaddingValues = PaddingValues(),
    action: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    ScreenLayout {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
            content = { content() }
        )
        action()
    }
}

@Composable
private fun LoginBusyHeading(title: String, style: TextStyle) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = style.copy(textAlign = TextAlign.Center)
    )
}

@Composable
private fun LoginBusyAction(
    label: String,
    onClick: () -> Unit,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    verticalPadding: Dp = 0.dp
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding)
            .heightIn(min = 48.dp)
    ) {
        Text(label, style = style)
    }
}
