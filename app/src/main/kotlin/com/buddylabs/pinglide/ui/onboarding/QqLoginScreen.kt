package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun QqLoginScreen(
    state: LoginState,
    onLogin: () -> Unit,
    onCancel: () -> Unit,
    onRetryStorage: () -> Unit,
    onBack: () -> Unit
) {
    when (state) {
        LoginState.Restoring, LoginState.Loading -> LoginBusyScreen(state, onCancel, onBack)

        LoginState.FirstDevice -> SetupStepScreen(
            step = 4,
            icon = R.drawable.ic_devices,
            title = R.string.setup_first_device_title,
            description = R.string.setup_first_device_description,
            onBack = onBack
        ) {}

        else -> LoginActionScreen(state, onLogin, onCancel, onRetryStorage, onBack)
    }
}

@Composable
private fun LoginActionScreen(
    state: LoginState,
    onLogin: () -> Unit,
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
        if (state == LoginState.Error) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.setup_back))
            }
        }
    }
}

@Composable
private fun LoginBusyScreen(state: LoginState, onCancel: () -> Unit, onBack: () -> Unit) {
    val title = stringResource(
        if (state ==
            LoginState.Loading
        ) {
            R.string.setup_qq_loading
        } else {
            R.string.setup_identity_loading
        }
    )
    ScreenLayout {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().weight(2f).verticalScroll(rememberScrollState())
                .semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            if (state == LoginState.Loading) {
                QqLoadingEmblem()
            } else {
                CircularProgressIndicator()
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = if (state == LoginState.Loading) onCancel else onBack,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(
                    if (state ==
                        LoginState.Loading
                    ) {
                        R.string.setup_cancel
                    } else {
                        R.string.setup_back
                    }
                )
            )
        }
    }
}

@Composable
private fun QqEmblem() {
    val shape = RoundedCornerShape(percent = 31)
    Image(
        painterResource(R.drawable.ic_qq),
        contentDescription = null,
        modifier = Modifier.size(92.dp).shadow(10.dp, shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1597DE), Color(0xFF075E9D))),
                shape
            ).padding(16.56.dp)
    )
}

@Composable
private fun QqLoadingEmblem() {
    var motionEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }.collect {
            motionEnabled = it
        }
    }
    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        val progressModifier = Modifier.matchParentSize().semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
        if (motionEnabled) {
            CircularProgressIndicator(progressModifier, strokeWidth = 6.dp)
        } else {
            CircularProgressIndicator(progress = { 0.25f }, progressModifier, strokeWidth = 6.dp)
        }
        QqEmblem()
    }
}
