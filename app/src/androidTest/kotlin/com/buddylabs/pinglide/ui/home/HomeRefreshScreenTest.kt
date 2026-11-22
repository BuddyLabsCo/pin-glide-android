package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRefreshScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val response = CompletableDeferred<List<RemotePairing>>()
    private val refreshInProgress =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在刷新")
    private val indeterminateProgress = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )
    private var requests = 0

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun pullingPastTheThresholdRefreshesOnReleaseAndKeepsDeviceRows() {
        val targets = openHome(listOf("我的电脑", "办公室电脑"))
        val list = compose.onNode(hasScrollAction())
        val heading = compose.onNodeWithText("我的设备")
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
        val releaseHeadingTop = heading.getUnclippedBoundsInRoot().top
        compose.runOnIdle { assertEquals(1, requests) }

        list.performTouchInput { up() }
        compose.waitUntil { compose.onAllNodes(hasText("正在刷新")).fetchSemanticsNodes().size == 1 }
        refreshing.assertIsDisplayed()
        release.assertDoesNotExist()
        compose.onNodeWithText("我的电脑").assertIsDisplayed()
        compose.onNodeWithText("办公室电脑").assertIsDisplayed()
        assertEquals(16f, (releaseHeadingTop - heading.getUnclippedBoundsInRoot().top).value, 1f)
        compose.runOnIdle { assertEquals(2, requests) }

        compose.onNodeWithContentDescription("设备列表，可下拉刷新或按 Enter 刷新")
            .assert(!refreshInProgress)

        response.complete(targets)
        compose.waitUntil { compose.onAllNodes(hasText("正在刷新")).fetchSemanticsNodes().isEmpty() }
        targets.forEach { compose.onNodeWithText(it.alias).assertIsDisplayed() }
    }

    @Test
    fun refreshingThroughAccessibilityKeepsProgressAvailableAfterScrolling() {
        val aliases = (1..5).map { "设备 $it" }
        val targets = openHome(aliases, Modifier.height(300.dp))
        val scroll = compose.onNode(hasScrollAction())
        val list = refreshList()
        val heading = compose.onNodeWithText("我的设备")
        val lastDevice = compose.onNodeWithText(aliases.last())
        scroll.performScrollToNode(hasText(aliases.last()))
        heading.assertIsNotDisplayed()
        lastDevice.assertIsDisplayed()
        list.assert(!refreshInProgress)

        val refresh = list.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "刷新设备列表" }
        compose.runOnIdle { refresh.action() }
        compose.waitUntil { compose.onAllNodes(refreshInProgress).fetchSemanticsNodes().size == 1 }
        list.assertIsDisplayed()
            .assert(refreshInProgress)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assert(indeterminateProgress)
        heading.assertIsNotDisplayed()
        lastDevice.assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests) }

        scroll.performScrollToNode(hasText("正在刷新"))
        val hint = compose.onNodeWithText("正在刷新")
        val viewportTop = scroll.getUnclippedBoundsInRoot().top
        val scrollDistance = hint.getUnclippedBoundsInRoot().top - viewportTop + 8.dp
        scroll.performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, with(compose.density) { scrollDistance.toPx() })
        }
        hint.assertIsDisplayed().assert(indeterminateProgress)
        val clippedHint = hint.getUnclippedBoundsInRoot()
        assertTrue(clippedHint.top < viewportTop && clippedHint.bottom > viewportTop)
        list.assert(!refreshInProgress)
        compose.onAllNodes(indeterminateProgress).assertCountEquals(1)

        scroll.performScrollToNode(hasText(aliases.last()))
        list.assert(refreshInProgress).assert(indeterminateProgress)
        response.complete(targets)
        compose.waitUntil { compose.onAllNodes(refreshInProgress).fetchSemanticsNodes().isEmpty() }
        lastDevice.assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun enterBringsInlineRefreshIntoViewFromAScrolledPosition() {
        val aliases = (1..5).map { "设备 $it" }
        val targets = openHome(aliases, Modifier.height(300.dp))
        val scroll = compose.onNode(hasScrollAction())
        val list = refreshList()
        val hint = compose.onNodeWithText("正在刷新")
        list.performSemanticsAction(SemanticsActions.RequestFocus)
        scroll.performScrollToNode(hasText(aliases.last()))
        compose.onNodeWithText("我的设备").assertIsNotDisplayed()

        list.performKeyInput { pressKey(Key.Enter) }
        hint.assertIsDisplayed().assert(indeterminateProgress)
        list.assert(!refreshInProgress)
        compose.runOnIdle { assertEquals(2, requests) }

        response.complete(targets)
        compose.waitUntil { compose.onAllNodes(hasText("正在刷新")).fetchSemanticsNodes().isEmpty() }
    }

    private fun refreshList() = compose.onNodeWithContentDescription("设备列表，可下拉刷新或按 Enter 刷新")

    private fun openHome(
        aliases: List<String>,
        modifier: Modifier = Modifier
    ): List<RemotePairing> {
        val now = Instant.now()
        val targets = aliases.mapIndexed { index, alias ->
            RemotePairing(
                "pairing-$index",
                "receiver-$index",
                alias,
                Presence.Online,
                now.toString()
            )
        }
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
                    Box(modifier) {
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
        return targets
    }
}
