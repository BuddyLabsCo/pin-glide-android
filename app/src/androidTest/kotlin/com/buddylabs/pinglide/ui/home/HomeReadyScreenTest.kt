package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeReadyScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyHomeUsesEnglishWhenSelected() {
        verifyHome(AppLanguage.English, "fr-FR", "en", "Online · State changed: 2 minutes ago")
    }

    @Test
    fun readyHomeUsesSimplifiedChineseWhenSelected() {
        verifyHome(AppLanguage.SimplifiedChinese, "en-US", "zh-Hans", "在线 · 状态变化：2分钟前")
    }

    @Test
    fun readyHomeFallsBackToSimplifiedChineseForFrenchSystemLocale() {
        verifyHome(AppLanguage.System, "fr-FR", "zh-Hans", "在线 · 状态变化：2分钟前")
    }

    @Test
    fun duplicateScanShowsASeparateNoticeAndCuesOnlyTheScannedTarget() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val now = Instant.now().toString()
        val state = HomeState(
            targets = listOf(
                TargetDevice("home", "My computer", true, true, Presence.Online, now),
                TargetDevice("office", "Office computer", false, true, Presence.Online, now)
            ),
            listed = true,
            authorization = AuthorizationState.Ready
        )
        val scannedPairingId = mutableStateOf<String?>(null)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            Box(Modifier.height(520.dp)) {
                LocalizedContent(AppLanguage.English) {
                    PinGlideTheme {
                        HomeScreen(
                            state,
                            smsGranted = true,
                            smsRecovery = SmsRecovery.Idle,
                            home = home,
                            onRestoreSms = {},
                            onAdd = {},
                            onSettings = {},
                            highlightedPairingId = scannedPairingId.value
                        )
                    }
                }
            }
        }
        val cue = "Just scanned device"
        val notice = compose.onNodeWithText("This target is already added")
        val enabled = compose.onNodeWithContentDescription("Receive SMS on My computer")
        val disabled = compose.onNodeWithContentDescription("Receive SMS on Office computer")
        val list = compose.onNode(hasScrollAction())
        val before = disabled.getUnclippedBoundsInRoot()
        val beforeBottom = list.getUnclippedBoundsInRoot().bottom
        assertTrue(before.top < beforeBottom && before.bottom > beforeBottom)
        compose.runOnIdle { scannedPairingId.value = "office" }
        compose.onNode(hasText("Office computer") and hasText(cue)).assertIsDisplayed()
        compose.onAllNodes(hasText(cue)).assertCountEquals(1)
        notice.assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        assertTrue(
            compose.onNodeWithText(cue, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom +
                15.dp <= list.getUnclippedBoundsInRoot().bottom
        )
        disabled.assertIsOff()

        compose.runOnIdle { scannedPairingId.value = null }
        compose.onNodeWithText(cue).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3_000)
        notice.assertDoesNotExist()
        list.performScrollToNode(hasText("My computer"))
        enabled.assertIsOn()
        disabled.assertIsOff()
    }

    @Test
    fun emptyHomeShowsWaitingStatusAndOnlyTheFirstDeviceAction() {
        verifyEmptyHome(AppLanguage.SimplifiedChinese, "zh-Hans", "等待设备")
    }

    @Test
    fun emptyHomeUsesEnglishWhenSelected() {
        verifyEmptyHome(AppLanguage.English, "en", "Waiting for devices")
    }

    @Test
    fun homeWaitsForFirstDeviceListBeforeOfferingPairing() {
        val state = mutableStateOf(
            HomeState(refreshing = true, authorization = AuthorizationState.Ready)
        )
        renderHome(AppLanguage.English, homeState = { state.value })
        compose.onNodeWithText("Loading device list").assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
        compose.onNodeWithText("Refreshing").assertDoesNotExist()
        compose.onNodeWithText("Add device").assertDoesNotExist()
        compose.onNodeWithText("Add first device").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(listed = true, refreshing = false) }
        compose.onNodeWithText("Loading device list").assertDoesNotExist()
        compose.onNode(hasText("Add first device") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun homeAnnouncesListRefreshWhileSmsPermissionIsOff() {
        renderHome(AppLanguage.English, smsGranted = false, homeState = {
            HomeState(refreshing = true, authorization = AuthorizationState.Ready)
        })

        assertRefreshing()
        compose.onNode(hasText("Allow SMS permission again") and hasClickAction())
            .assertIsDisplayed()
        compose.onNodeWithText("Loading device list").assertDoesNotExist()
    }

    @Test
    fun initialListFailureExplainsRecoveryAndOffersTwoWaysToRefresh() {
        val scope = CoroutineScope(Dispatchers.Main)
        try {
            val store = HomePairingStore(
                emptyList(),
                Instant.now().plusSeconds(3600).toString(),
                listed = false
            )
            val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
            var requests = 0
            val service = object : PairingService {
                override suspend fun list(managementToken: String): List<RemotePairing> {
                    if (++requests < 3) throw PairingServiceException(PairingFailure.Unavailable)
                    return emptyList()
                }

                override suspend fun remove(managementToken: String, pairingId: String) =
                    error("Unexpected removal")
            }
            val pairings = OwnPairings(store, service, renewal, scope)
            runBlocking(Dispatchers.Main) { pairings.refresh() }
            renderHome(AppLanguage.SimplifiedChinese, pairings = pairings)

            compose.onNodeWithText("暂时无法获取设备").assertIsDisplayed()
            compose.onNodeWithText("设备列表暂不可用").assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val recoveryCopy = "下拉刷新设备列表。这不代表配对已被删除，也不代表登录失效。"
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(recoveryCopy).assertIsDisplayed().performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) { it(layouts) }
            val layout = layouts.single()
            if (layout.size.width >= with(compose.density) { 240.dp.roundToPx() }) {
                assertEquals(
                    "下拉刷新设备列表。这不代表配对已被删除，",
                    recoveryCopy.take(layout.getLineEnd(0))
                )
            }
            compose.onNodeWithText("添加第一台设备").assertDoesNotExist()

            val list = compose.onNodeWithContentDescription("设备列表，可下拉刷新或按 Enter 刷新")
            compose.density.assertInitialFailureArtwork(
                list.captureToImage().asAndroidBitmap(),
                compose.activity
            )
            val refresh = list.fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .single { it.label == "刷新设备列表" }
            compose.runOnIdle { assertTrue(refresh.action()) }
            compose.runOnIdle { assertEquals(2, requests) }
            compose.onNodeWithText("设备列表暂不可用").assertIsDisplayed()

            list.performTouchInput { swipeDown() }
            compose.runOnIdle { assertEquals(3, requests) }
            compose.onNodeWithText("设备列表暂不可用").assertDoesNotExist()
            compose.onNode(hasText("添加第一台设备") and hasClickAction()).assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedFirstListKeepsLoginAndSmsRecoverySeparateFromTheListFailure() {
        val state = mutableStateOf(
            HomeState(failed = true, authorization = AuthorizationState.LoginRequired)
        )
        renderHome(AppLanguage.English, smsGranted = false, homeState = { state.value })

        compose.onNode(hasText("Sign in again with QQ") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Device list temporarily unavailable").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(authorization = AuthorizationState.Ready)
        }
        compose.onNode(hasText("Allow SMS permission again") and hasClickAction())
            .assertIsDisplayed()
        compose.onNodeWithText("Device list temporarily unavailable").assertDoesNotExist()
    }

    @Test
    fun homeKeepsCachedDevicesWhenFirstListRefreshFails() {
        val target = TargetDevice("cached", "Cached device", enabled = true, keyUsable = true)
        val state = mutableStateOf(
            HomeState(
                targets = listOf(target),
                refreshing = true,
                authorization = AuthorizationState.Ready
            )
        )
        renderHome(AppLanguage.English, homeState = { state.value })

        assertRefreshing()
        val refreshing = compose.onNodeWithText("Refreshing").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        )
        val hero = compose.onNodeWithText("Ready to forward").assertIsDisplayed()
        val devices = compose.onNodeWithText("My devices").assertIsDisplayed()
        assertTrue(
            hero.getUnclippedBoundsInRoot().bottom <= refreshing.getUnclippedBoundsInRoot().top
        )
        assertTrue(
            refreshing.getUnclippedBoundsInRoot().bottom <= devices.getUnclippedBoundsInRoot().top
        )
        compose.onNodeWithText("Loading device list").assertDoesNotExist()
        compose.onNodeWithText("Cached device").assertIsDisplayed()
        compose.onNodeWithText("Add device").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(targets = listOf(target.copy(enabled = false)))
        }
        compose.onNodeWithText("No receiving devices selected").assertIsDisplayed()
        compose.runOnIdle {
            state.value =
                state.value.copy(targets = listOf(target.copy(presence = Presence.Offline)))
        }
        compose.onNodeWithText("All selected devices are offline").assertIsDisplayed()

        compose.runOnIdle {
            state.value =
                state.value.copy(targets = listOf(target), refreshing = false, failed = true)
        }
        compose.onNodeWithText("Refreshing").assertDoesNotExist()
        compose.onNodeWithText("Device status not updated").assertIsDisplayed()
        compose.onNodeWithText("Existing Pairings are retained.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Cached device").assertIsDisplayed()
        compose.onNodeWithText("Device list temporarily unavailable").assertDoesNotExist()
        compose.onNodeWithText("Add device").assertDoesNotExist()
    }

    private fun assertRefreshing() {
        compose.onNodeWithText("Refreshing").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
    }

    @Test
    fun emptyHomeStacksTheHeroAndKeepsTheActionReachableAtLargeFontScale() {
        var addRequests = 0
        renderHome(AppLanguage.English, fontScale = 2f, onAdd = { addRequests++ })
        val heading = compose.onNodeWithText("Account connected").assertIsDisplayed()
        val badge = compose.onNodeWithText("Waiting for devices").assertIsDisplayed()
        assertTrue(
            badge.getUnclippedBoundsInRoot().top >= heading.getUnclippedBoundsInRoot().bottom
        )
        compose.onNode(hasText("Add first device") and hasClickAction())
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, addRequests) }
    }

    private fun renderHome(
        language: AppLanguage,
        fontScale: Float = 1f,
        onAdd: () -> Unit = {},
        smsGranted: Boolean = true,
        pairings: OwnPairings? = null,
        homeState: () -> HomeState = {
            HomeState(listed = true, authorization = AuthorizationState.Ready)
        }
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(pairings ?: app.ownPairings, app.senderRestoration()) { null }
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = Density(LocalDensity.current.density, fontScale)
            CompositionLocalProvider(LocalDensity provides density) {
                Box(Modifier.widthIn(max = 366.dp)) {
                    LocalizedContent(language) {
                        PinGlideTheme {
                            HomeScreen(
                                pairings?.state?.collectAsState()?.value ?: homeState(),
                                smsGranted = smsGranted,
                                smsRecovery = SmsRecovery.Idle,
                                home = home,
                                onRestoreSms = {},
                                onAdd = onAdd,
                                onSettings = {}
                            )
                        }
                    }
                }
            }
        }
    }

    private fun verifyEmptyHome(language: AppLanguage, locale: String, waiting: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(locale))
            }
        )
        var addRequests = 0
        renderHome(language, onAdd = { addRequests++ })
        compose.onNodeWithText(waiting).assertIsDisplayed()
        compose.onNodeWithText(expected.getString(R.string.home_no_devices))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(expected.getString(R.string.home_add_instructions))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(expected.getString(R.string.home_add)).assertDoesNotExist()
        compose.onNodeWithText(expected.getString(R.string.home_count, 0)).assertDoesNotExist()
        compose.onNode(hasText(expected.getString(R.string.home_add_first)) and hasClickAction())
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, addRequests) }
    }

    private fun verifyHome(
        language: AppLanguage,
        systemLocale: String,
        expectedLocale: String,
        officePresence: String
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val systemConfiguration = Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(systemLocale))
        }
        val expected = app.createConfigurationContext(
            Configuration(systemConfiguration).apply {
                setLocales(LocaleList.forLanguageTags(expectedLocale))
            }
        )
        val first = "My computer"
        val second = "Office computer"
        val now = Instant.now()
        val state = HomeState(
            targets = listOf(
                TargetDevice("home", first, true, true, Presence.Online, now.toString()),
                TargetDevice(
                    "office",
                    second,
                    true,
                    true,
                    Presence.Online,
                    now.minusSeconds(150).toString()
                )
            ),
            listed = true,
            authorization = AuthorizationState.Ready
        )
        var addRequests = 0
        var settingsRequests = 0
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides systemConfiguration) {
                LocalizedContent(language) {
                    PinGlideTheme {
                        HomeScreen(
                            state,
                            smsGranted = true,
                            smsRecovery = SmsRecovery.Idle,
                            home = home,
                            onRestoreSms = {},
                            onAdd = { addRequests++ },
                            onSettings = { settingsRequests++ }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(expected.getString(R.string.brand_wordmark)).assertIsDisplayed()
        compose.onNodeWithText(expected.getString(R.string.home_ready_title)).assertIsDisplayed()
        compose.onNodeWithText(expected.getString(R.string.home_count, 2)).assertIsDisplayed()
        compose.onNodeWithText(first).assertIsDisplayed()
        val office = compose.onNodeWithText(second).assertIsDisplayed()
        compose.onNodeWithText(
            expected.getString(
                R.string.home_presence,
                expected.getString(R.string.home_online),
                expected.getString(R.string.home_presence_just_now)
            )
        ).assertIsDisplayed()
        compose.onNodeWithText(officePresence).assertIsDisplayed()
        compose.onNodeWithContentDescription(expected.getString(R.string.home_receive, first))
            .assertIsDisplayed().assertIsOn()
        compose.onNodeWithContentDescription(expected.getString(R.string.home_receive, second))
            .assertIsDisplayed().assertIsOn()
        compose.onNodeWithText(expected.getString(R.string.home_add)).performClick()
        compose.onNodeWithContentDescription(expected.getString(R.string.settings_title))
            .performClick()
        compose.runOnIdle {
            assertEquals(1, addRequests)
            assertEquals(1, settingsRequests)
        }
        office.performClick()
        val inSheet = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        compose.onNode(hasText(officePresence) and inSheet).assertIsDisplayed()
    }
}
