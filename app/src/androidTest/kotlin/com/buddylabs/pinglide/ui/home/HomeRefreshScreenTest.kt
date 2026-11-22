package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
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
    private var selectionAttempts = 0

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun missingPairingKeyDisablesOnlyTheAffectedReceiveSwitchInLightTheme() {
        verifyMissingPairingKey(
            Configuration.UI_MODE_NIGHT_NO,
            rowColor = Color(0xFFEEF6F2),
            thumbColor = Color(0xFF52635E),
            trackColor = Color(0xFFE3EFEA)
        )
    }

    @Test
    fun missingPairingKeyDisablesOnlyTheAffectedReceiveSwitchInDarkTheme() {
        verifyMissingPairingKey(
            Configuration.UI_MODE_NIGHT_YES,
            rowColor = Color(0xFF11221F),
            thumbColor = Color(0xFFA7BBB5),
            trackColor = Color(0xFF172B27)
        )
    }

    private fun verifyMissingPairingKey(
        nightMode: Int,
        rowColor: Color,
        thumbColor: Color,
        trackColor: Color
    ) {
        openHome(listOf("我的电脑", "办公室电脑"), missingKeyIndex = 0, nightMode = nightMode)
        val keyError = hasText("密钥丢失或损坏")
        compose.onNode(hasText("我的电脑") and keyError).assertIsDisplayed().assertIsEnabled()
        compose.onAllNodes(keyError).assertCountEquals(1)
        compose.onNodeWithText("转发已就绪").assertIsDisplayed()
        compose.onNodeWithText("添加设备").assertIsDisplayed()
        val affected = compose.onNodeWithContentDescription("我的电脑接收短信")
        val other = compose.onNodeWithContentDescription("办公室电脑接收短信")
        affected.assertIsNotEnabled().assertIsOff()
        other.assertIsEnabled().assertIsOn()
        assertUncheckedSwitchArtwork(
            affected,
            thumbColor.copy(alpha = 0.38f).compositeOver(rowColor),
            trackColor.copy(alpha = 0.38f).compositeOver(rowColor)
        )
        affected.performTouchInput { click() }.assertIsOff()
        compose.runOnIdle { assertEquals(0, selectionAttempts) }
        other.performTouchInput { click() }.assertIsOff()
        other.performTouchInput { click() }.assertIsOn()
        affected.assertIsNotEnabled().assertIsOff()
        compose.runOnIdle { assertEquals(2, selectionAttempts) }

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(keyError, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(11.sp, layouts.single().layoutInput.style.fontSize)
    }

    @Test
    fun allReceiveSwitchesOffShowsWarningUntilAnOnlineTargetIsEnabled() {
        val aliases = listOf("我的电脑", "办公室电脑")
        openHome(aliases, initiallyDisabled = listOf(0, 1))
        val warningTitle = hasText("尚未选择接收设备")
        compose.onNode(warningTitle).assertIsDisplayed()
        val warningBody = compose.onNodeWithText("开启至少一台设备的接收开关后，鹊信才会转发新短信。")
        warningBody.assertIsDisplayed()
        assertWarningBodyColor(warningBody)
        compose.assertWarningHeroArtwork(warningTitle)
        compose.onNodeWithText("添加设备").assertIsDisplayed()

        val first = compose.onNodeWithContentDescription("我的电脑接收短信")
        val second = compose.onNodeWithContentDescription("办公室电脑接收短信")
        first.assertIsEnabled().assertIsOff()
        second.assertIsEnabled().assertIsOff()
        assertUncheckedSwitchArtwork(first, Color(0xFF52635E), Color(0xFFE3EFEA))
        compose.onNode(hasText(aliases.first()) and hasText("在线 · 状态变化：2020/1/15 17:00"))
            .assertIsDisplayed()
        compose.onNode(hasText(aliases.last()) and hasText("在线 · 状态变化：2021/1/15 17:00"))
            .assertIsDisplayed()

        first.performTouchInput { click() }.assertIsOn()
        second.assertIsOff()
        compose.onNode(warningTitle).assertDoesNotExist()
        compose.onNodeWithText("转发已就绪").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, selectionAttempts) }
    }

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
    fun failedRefreshRetainsPairingsAndPullingAgainRestoresPresence() {
        val aliases = listOf("我的电脑", "办公室电脑")
        openHome(aliases, initiallyDisabled = listOf(1))
        val list = compose.onNode(hasScrollAction())
        val warning = compose.onNodeWithText("已有配对仍保留。当前无法确认设备是否在线。")
        val heading = compose.onNodeWithText("我的设备")
        val stalePresence = hasText("状态待更新")

        list.pullToRefresh()
        compose.waitUntil { compose.onAllNodes(hasText("正在刷新")).fetchSemanticsNodes().size == 1 }
        response.completeExceptionally(PairingServiceException(PairingFailure.Unavailable))
        val heroTitle = hasText("设备状态未更新")
        compose.waitUntil { compose.onAllNodes(heroTitle).fetchSemanticsNodes().size == 1 }

        warning.assertIsDisplayed()
        compose.assertWarningHeroArtwork(heroTitle)
        val bounds = list.getUnclippedBoundsInRoot()
        val warningBounds = warning.getUnclippedBoundsInRoot()
        compose.density.assertCachedFailureNoticeArtwork(
            list.captureToImage().asAndroidBitmap(),
            compose.activity,
            warningBounds.left - bounds.left,
            warningBounds.top - bounds.top
        )
        assertTrue(
            warning.getUnclippedBoundsInRoot().bottom < heading.getUnclippedBoundsInRoot().top
        )
        compose.onNodeWithText("添加设备").assertDoesNotExist()
        aliases.forEach { alias ->
            compose.onNodeWithText(alias).assertIsDisplayed().assert(stalePresence)
        }
        val first = compose.onNodeWithContentDescription("${aliases.first()}接收短信")
        val second = compose.onNodeWithContentDescription("${aliases.last()}接收短信")
        first.assertIsEnabled().assertIsOn()
        second.assertIsEnabled().assertIsOff().performClick().assertIsOn()
        warning.assertIsDisplayed()
        compose.onAllNodes(stalePresence).assertCountEquals(2)

        list.pullToRefresh()
        compose.waitUntil { compose.onAllNodes(stalePresence).fetchSemanticsNodes().isEmpty() }
        warning.assertDoesNotExist()
        compose.onNodeWithText("添加设备").assertIsDisplayed()
        aliases.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.runOnIdle { assertEquals(3, requests) }
    }

    @Test
    fun rejectedReceiveSwitchWriteKeepsTheCurrentDeviceList() {
        val alias = "我的电脑"
        openHome(listOf(alias), rejectSelection = true)

        compose.onNodeWithContentDescription("${alias}接收短信")
            .assertIsOn().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, selectionAttempts)
            assertEquals(1, requests)
        }
        compose.onNodeWithText(alias).assert(hasText("在线", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("转发已就绪").assertIsDisplayed()
        compose.onNodeWithText("已有配对仍保留。当前无法确认设备是否在线。").assertDoesNotExist()
        compose.onNodeWithText("添加设备").assertIsDisplayed()
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

    private fun assertUncheckedSwitchArtwork(
        switch: SemanticsNodeInteraction,
        thumbColor: Color,
        trackColor: Color
    ) {
        val image = switch.captureToImage().asAndroidBitmap()
        val center = image.height / 2
        val thumbWidth = (0 until image.width).count { column ->
            Color(image.getPixel(column, center)).matchesRgb(thumbColor)
        }
        assertEquals("Thumb width in dp", 22f, thumbWidth / compose.density.density, 1f)
        val actualTrackColor = Color(image.getPixel(image.width * 3 / 4, center))
        assertTrue(
            "Track color expected $trackColor, got $actualTrackColor",
            actualTrackColor.matchesRgb(trackColor)
        )
    }

    private fun Color.matchesRgb(expected: Color): Boolean =
        abs(red - expected.red) <= 1.5f / 255f &&
            abs(green - expected.green) <= 1.5f / 255f &&
            abs(blue - expected.blue) <= 1.5f / 255f

    private fun assertWarningBodyColor(body: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val expected = Color(0xFF003832).copy(alpha = 0.78f)
        assertEquals(expected, layouts.single().layoutInput.style.color)
    }

    private fun SemanticsNodeInteraction.pullToRefresh() = performTouchInput {
        down(center)
        moveBy(Offset(0f, 216.dp.toPx()))
        up()
    }

    private fun openHome(
        aliases: List<String>,
        modifier: Modifier = Modifier,
        initiallyDisabled: List<Int> = emptyList(),
        rejectSelection: Boolean = false,
        missingKeyIndex: Int? = null,
        nightMode: Int = Configuration.UI_MODE_NIGHT_NO
    ): List<RemotePairing> {
        val now = Instant.now()
        val targets = aliases.mapIndexed { index, alias ->
            val changedAt = LocalDateTime.of(2020 + index, 1, 15, 17, 0)
                .atZone(ZoneId.systemDefault()).toInstant()
            RemotePairing(
                "pairing-$index",
                "receiver-$index",
                alias,
                Presence.Online,
                changedAt.toString()
            )
        }
        val service = object : PairingService {
            override suspend fun list(managementToken: String) =
                if (++requests == 2) response.await() else targets
        }
        val store = HomePairingStore(
            targets,
            now.plusSeconds(3600).toString(),
            missingKeyId = missingKeyIndex?.let { targets[it].pairingId }
        )
        val pairingStore = object : PairingStore by store {
            override suspend fun select(
                expected: PairingSnapshot,
                pairingId: String,
                enabled: Boolean
            ): Boolean {
                selectionAttempts++
                return !rejectSelection && store.select(expected, pairingId, enabled)
            }
        }
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val pairings = OwnPairings(pairingStore, service, renewal, scope)
        runBlocking(Dispatchers.Main) {
            initiallyDisabled.forEach { index ->
                store.select(store.readPairings(), targets[index].pairingId, false)
            }
            pairings.refresh()
        }
        val app = compose.activity.application as PinGlideApplication
        val home = HomeViewModel(pairings, app.senderRestoration()) { null }
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val state = home.state.collectAsState().value
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
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
        }
        return targets
    }
}
