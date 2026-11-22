package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeRefreshScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun pullingPastTheThresholdRefreshesOnReleaseAndKeepsDeviceRows() {
        val now = Instant.now()
        val targets = listOf(
            RemotePairing("home", "receiver-home", "我的电脑", Presence.Online, now.toString()),
            RemotePairing(
                "office",
                "receiver-office",
                "办公室电脑",
                Presence.Online,
                now.minusSeconds(150).toString()
            )
        )
        val response = CompletableDeferred<List<RemotePairing>>()
        var requests = 0
        val service = object : PairingService {
            override suspend fun list(managementToken: String) =
                if (++requests == 1) targets else response.await()
        }
        val store = HomePairingStore(targets, now.plusSeconds(3600).toString())
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val pairings = OwnPairings(store, service, renewal, scope)
        runBlocking(Dispatchers.Main) { pairings.refresh() }
        val app = compose.activity.application as PinGlideApplication
        val home = HomeViewModel(pairings, app.senderRestoration()) { null }
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val state = home.state.collectAsState().value
            LocalizedContent(AppLanguage.SimplifiedChinese) {
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

        val list = compose.onNode(hasScrollAction())
        val pulling = compose.onNodeWithText("下拉刷新")
        val release = compose.onNodeWithText("松开刷新")
        val refreshing = compose.onNodeWithText("正在刷新")
        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, 72.dp.toPx()))
        }
        pulling.assertIsDisplayed()
        list.performTouchInput { up() }
        pulling.assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, requests) }

        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, 72.dp.toPx()))
        }
        pulling.assertIsDisplayed()
        list.performTouchInput { moveBy(Offset(0f, 144.dp.toPx())) }
        release.assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests) }

        list.performTouchInput { up() }
        compose.waitUntil { compose.onAllNodes(hasText("正在刷新")).fetchSemanticsNodes().size == 1 }
        refreshing.assertIsDisplayed()
        release.assertDoesNotExist()
        targets.forEach { compose.onNodeWithText(it.alias).assertIsDisplayed() }
        compose.runOnIdle { assertEquals(2, requests) }

        response.complete(targets)
        compose.waitUntil { compose.onAllNodes(hasText("正在刷新")).fetchSemanticsNodes().isEmpty() }
        targets.forEach { compose.onNodeWithText(it.alias).assertIsDisplayed() }
    }
}
