package com.buddylabs.pinglide.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.ui.components.QqEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout
import com.buddylabs.pinglide.ui.home.HomeLoginScreen
import com.buddylabs.pinglide.ui.home.HomeViewModel

@Composable
internal fun PairingLoginRecovery(home: HomeViewModel, onBackground: () -> Unit) {
    val restoration by home.loginRestoration.collectAsState()
    if (restoration != RestorationState.Idle && restoration != RestorationState.Restored) {
        HomeLoginScreen(restoration, home::restoreLogin, home::cancelLogin, home::leaveLogin)
        return
    }
    BackHandler(onBack = onBackground)
    ScreenLayout {
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            QqEmblem()
            Text(
                stringResource(R.string.pairing_login_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            Text(
                stringResource(R.string.pairing_login_copy),
                modifier = Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                }
            )
            Button(
                onClick = home::restoreLogin,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.pairing_login_action))
            }
        }
    }
}
