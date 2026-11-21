package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
        LoginState.Entry -> QqSignInScreen(onLogin)

        LoginState.Loading -> {
            val title = stringResource(R.string.setup_qq_loading)
            LoginStatusLayout(
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
                LoginStatusHeading(
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
            LoginStatusLayout(
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
                LoginStatusHeading(title, MaterialTheme.typography.headlineLarge)
            }
        }

        LoginState.Error -> QqSignInFailureScreen(onLogin, onCancel)

        LoginState.RestorationError, LoginState.StorageError ->
            LoginErrorScreen(state, onLogin, onNewAccount, onRetryStorage, onBack)

        LoginState.FirstDevice, LoginState.Welcome, LoginState.Revoked -> Unit
    }
}

@Composable
private fun QqSignInScreen(onLogin: () -> Unit) {
    CompactSetupStepScreen(
        step = 3,
        title = R.string.setup_qq_title,
        description = R.string.setup_qq_description,
        emblem = { RingedSetupEmblem(CircleShape) { QqEmblem() } }
    ) {
        CompactSetupButton(R.string.setup_qq_sign_in, R.drawable.ic_chevron, onLogin)
    }
}

@Composable
private fun QqSignInFailureScreen(onRetry: () -> Unit, onBack: () -> Unit) {
    val title = stringResource(R.string.setup_qq_error_title)
    LoginStatusLayout(
        title = title,
        spacing = 0.dp,
        contentPadding = PaddingValues(bottom = 34.dp),
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactSetupButton(R.string.setup_qq_retry, R.drawable.ic_refresh, onRetry)
                LoginBusyAction(
                    label = stringResource(R.string.setup_back),
                    onClick = onBack,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.14.sp
                    )
                )
            }
        }
    ) {
        RingedSetupEmblem(RoundedCornerShape(44.dp), error = true) {
            Icon(
                painterResource(R.drawable.ic_error_filled),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = colorResource(R.color.error_emblem)
            )
        }
        Text(
            title,
            modifier = Modifier.padding(top = 7.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-1.17).sp
            ),
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.setup_qq_error_description),
            modifier = Modifier.padding(top = 10.dp).widthIn(max = 286.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = 18.sp,
                letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoginErrorScreen(
    state: LoginState,
    onLogin: () -> Unit,
    onNewAccount: () -> Unit,
    onRetryStorage: () -> Unit,
    onBack: () -> Unit
) {
    val storageError = state == LoginState.StorageError
    SetupStepScreen(
        step = 3,
        icon = R.drawable.ic_info,
        title = if (storageError) {
            R.string.setup_identity_unavailable_title
        } else {
            R.string.setup_qq_error_title
        },
        description = if (storageError) {
            R.string.setup_identity_unavailable_description
        } else {
            R.string.account_restore_error
        },
        onBack = onBack,
        error = true
    ) {
        LoginErrorActions(storageError, onLogin, onNewAccount, onRetryStorage)
    }
}

@Composable
private fun LoginErrorActions(
    storageError: Boolean,
    onLogin: () -> Unit,
    onNewAccount: () -> Unit,
    onRetryStorage: () -> Unit
) {
    Button(
        onClick = if (storageError) onRetryStorage else onLogin,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text(
            stringResource(
                if (storageError) R.string.setup_check_again else R.string.setup_qq_retry
            )
        )
    }
    if (!storageError) {
        TextButton(
            onClick = onNewAccount,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.account_new_login))
        }
    }
}

@Composable
private fun LoginStatusLayout(
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
private fun LoginStatusHeading(title: String, style: TextStyle) {
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
