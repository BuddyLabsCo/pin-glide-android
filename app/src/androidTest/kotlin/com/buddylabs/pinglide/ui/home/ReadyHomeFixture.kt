package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal fun renderReadyHome(
    compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    targets: List<RemotePairing>,
    scope: CoroutineScope,
    language: AppLanguage = AppLanguage.SimplifiedChinese,
    nightMode: Int? = null,
    sendTest: suspend (String) -> TestMessageOutcome? = { null },
    missingKeyId: String? = null,
    onRemove: suspend (String) -> Unit
) {
    val store = HomePairingStore(
        targets,
        Instant.now().plusSeconds(3600).toString(),
        missingKeyId = missingKeyId
    )
    val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
    val service = object : PairingService {
        private val remote = targets.associateBy { it.pairingId }.toMutableMap()

        override suspend fun list(managementToken: String) = remote.values.toList()

        override suspend fun rename(
            managementToken: String,
            pairingId: String,
            alias: String
        ): RemotePairing {
            val target = remote.getValue(pairingId)
            val updated = RemotePairing(
                pairingId,
                target.receiverId,
                alias,
                target.presence,
                target.stateChangedAt
            )
            remote[pairingId] = updated
            return updated
        }

        override suspend fun remove(managementToken: String, pairingId: String) {
            onRemove(pairingId)
            checkNotNull(remote.remove(pairingId))
        }
    }
    val pairings = OwnPairings(store, service, renewal, scope)
    runBlocking(Dispatchers.Main) { pairings.refresh() }
    compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
    val app = compose.activity.application as PinGlideApplication
    val home = HomeViewModel(pairings, app.senderRestoration(), sendTest)
    compose.setContent {
        val state by home.state.collectAsState()
        val systemConfiguration = LocalConfiguration.current
        val configuration = Configuration(systemConfiguration).apply {
            if (nightMode != null) {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
        }
        CompositionLocalProvider(LocalConfiguration provides configuration) {
            LocalizedContent(language) {
                PinGlideTheme {
                    HomeScreen(
                        state,
                        smsGranted = true,
                        smsRecovery = SmsRecovery.Idle,
                        home = home,
                        onRestoreSms = {},
                        onAdd = {},
                        onSettings = {}
                    )
                }
            }
        }
    }
}
