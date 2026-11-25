package com.buddylabs.pinglide.ui.settings

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.ui.components.QqEmblem

@Composable
internal fun AccountScreen(account: AccountSession, onLogout: () -> Unit) {
    val state by account.state.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val logoutFocus = remember { FocusRequester() }
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
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QqEmblem(size = 46.dp)
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    when {
                        state.loading -> stringResource(R.string.account_loading)
                        state.failed -> stringResource(R.string.settings_account)
                        else -> state.nickname ?: stringResource(R.string.settings_account)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.nickname != null) Text(stringResource(R.string.settings_account))
            }
        }
    }
    AccountLogoutAction(Modifier.focusRequester(logoutFocus)) { confirming = true }
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val width = (configuration.screenWidthDp.dp - 48.dp).coerceIn(0.dp, 320.dp)
    val height = with(density) {
        (configuration.screenHeightDp.dp - statusBarHeight.toDp() - 48.dp).coerceAtLeast(0.dp)
    }
    val dimAmount = if (isSystemInDarkTheme()) 0.64f else 0.45f
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            window.setDimAmount(dimAmount)
            val attributes = window.attributes
            if (attributes.y != statusBarHeight / 2) {
                attributes.y = statusBarHeight / 2
                window.attributes = attributes
            }
        }
        Surface(
            modifier = Modifier.width(width).heightIn(max = height).semantics {
                paneTitle = title
                isTraversalGroup = true
            },
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_logout),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = colors.primary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        letterSpacing = (-0.77).sp
                    ),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    stringResource(R.string.account_logout_description),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 0.sp,
                        lineHeight = 19.sp
                    )
                )
                Spacer(Modifier.height(22.dp))
                LogoutConfirmationActions(onCancel, onLogout)
            }
        }
    }
}

@Composable
private fun LogoutConfirmationActions(onCancel: () -> Unit, onLogout: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val padding = PaddingValues(horizontal = 22.dp)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
}

@Composable
private fun AccountLogoutAction(modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = MaterialTheme.colorScheme.error
            Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = color)
            Text(
                stringResource(R.string.account_logout),
                Modifier.weight(1f),
                color = color,
                style = MaterialTheme.typography.bodyLarge
            )
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = color)
        }
    }
}
