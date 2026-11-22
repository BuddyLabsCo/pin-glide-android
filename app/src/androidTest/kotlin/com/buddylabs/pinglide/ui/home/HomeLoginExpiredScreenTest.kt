package com.buddylabs.pinglide.ui.home

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeLoginExpiredScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightHomeShowsRecoveryAndLocksCachedTargets() {
        val state = expiredState().copy(listRefreshFailed = true)
        verifyRecovery(Configuration.UI_MODE_NIGHT_NO, state)
    }

    @Test
    fun darkHomeShowsRecoveryAndKeepsAnOffSwitchOff() {
        verifyRecovery(Configuration.UI_MODE_NIGHT_YES, expiredState(secondTargetEnabled = false))
    }

    @Test
    fun expirationDismissesOpenDetailsAndRestoringAuthorizationDoesNotReopenThem() {
        val ready = expiredState().copy(authorization = AuthorizationState.Ready)
        val state = mutableStateOf(ready)
        renderHome { state.value }
        val details = compose.onNodeWithText("重命名设备")
        compose.onNodeWithText("我的电脑").performClick()
        details.assertIsDisplayed()

        compose.runOnIdle {
            state.value = ready.copy(authorization = AuthorizationState.LoginRequired)
        }
        details.assertDoesNotExist()
        compose.onNodeWithText("使用 QQ 重新登录").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("我的电脑接收短信").assertIsNotEnabled().assertIsOn()

        compose.runOnIdle { state.value = ready }
        details.assertDoesNotExist()
        compose.onNodeWithText("我的电脑").assertIsEnabled().performClick()
        details.assertIsDisplayed()
    }

    @Test
    fun expiredListStillScrollsButHidesRefreshControlsAndFeedback() {
        val expired = expiredState()
        val targets = (1..5).map {
            expired.targets.first().copy(pairingId = "target-$it", alias = "设备 $it")
        }
        val state = mutableStateOf(expired.copy(targets = targets, refreshing = true))
        renderHome(modifier = Modifier.height(400.dp)) { state.value }

        compose.onNodeWithContentDescription("设备列表，可下拉刷新或按 Enter 刷新").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .assertDoesNotExist()
        compose.onNodeWithText("正在刷新").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(refreshing = false) }
        val list = compose.onNode(hasScrollAction())
        val heading = compose.onNodeWithText("我的设备")
        val headingTop = heading.getUnclippedBoundsInRoot().top
        list.performTouchInput {
            down(center.copy(y = 48.dp.toPx()))
            moveBy(Offset(0f, 72.dp.toPx()))
        }
        compose.onNodeWithText("下拉刷新").assertDoesNotExist()
        list.performTouchInput { moveBy(Offset(0f, 144.dp.toPx())) }
        compose.onNodeWithText("松开刷新").assertDoesNotExist()
        assertEquals(headingTop, heading.getUnclippedBoundsInRoot().top)
        list.performTouchInput { up() }
        list.performScrollToNode(hasText("设备 5"))
        compose.onNodeWithText("设备 5").assertIsDisplayed()
    }

    private fun expiredState(secondTargetEnabled: Boolean = true): HomeState {
        val now = Instant.now().toString()
        return HomeState(
            targets = listOf(
                TargetDevice("home", "我的电脑", true, true, Presence.Online, now),
                TargetDevice("office", "办公室电脑", secondTargetEnabled, true, Presence.Offline, now)
            ),
            listed = true,
            authorization = AuthorizationState.LoginRequired
        )
    }

    private fun renderHome(
        nightMode: Int = Configuration.UI_MODE_NIGHT_NO,
        modifier: Modifier = Modifier,
        state: () -> HomeState
    ): Context {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val dark = nightMode == Configuration.UI_MODE_NIGHT_YES
        val systemBars = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge(systemBars, systemBars) }
        val configuration = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                LocalizedContent(AppLanguage.SimplifiedChinese) {
                    PinGlideTheme {
                        Box(modifier) {
                            HomeScreen(
                                state(),
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
        return app.createConfigurationContext(configuration)
    }

    private fun verifyRecovery(nightMode: Int, state: HomeState) {
        val colors = renderHome(nightMode) { state }
        compose.onNodeWithText("需要重新登录").assertIsDisplayed()
        compose.onNodeWithText("登录失效").assertIsDisplayed()
        compose.onNodeWithText(
            "登录凭据已失效，无法自动续期。使用原 QQ 账号重新登录，可保留当前设备已有的配对。"
        ).assertIsDisplayed()
        val retryLabel = "使用 QQ 重新登录"
        val action = compose.onNodeWithText(retryLabel).assertIsDisplayed().assertIsEnabled()
        compose.assertWarningHeroArtwork(hasText("需要重新登录"))
        compose.assertAccountActionIcon(action, retryLabel, colors.getColor(R.color.on_primary))
        compose.onNodeWithText("已有配对仍保留。当前无法确认设备是否在线。").assertDoesNotExist()
        compose.onNodeWithText("添加设备").assertDoesNotExist()
        compose.onAllNodesWithText("状态待更新").assertCountEquals(2)
        state.targets.forEach { target ->
            compose.onNodeWithText(target.alias).assertIsDisplayed().assertIsNotEnabled()
            val receive = compose.onNodeWithContentDescription("${target.alias}接收短信")
                .assertIsDisplayed().assertIsNotEnabled()
            if (target.enabled) receive.assertIsOn() else receive.assertIsOff()
        }
        assertDisabledArtwork(colors)
    }

    private fun assertDisabledArtwork(colors: Context) = with(compose.density) {
        val mutedPrimary = Color(colors.getColor(R.color.primary)).copy(alpha = 0.38f)
            .compositeOver(Color(colors.getColor(R.color.surface_container)))
        val row = compose.onNodeWithText("我的电脑").captureToImage().asAndroidBitmap()
        val receive = compose.onNodeWithContentDescription("我的电脑接收短信")
            .captureToImage().asAndroidBitmap()
        assertMutedColor(
            "Device icon",
            mutedPrimary,
            row.getPixel(21.dp.roundToPx(), row.height / 2 - 6.dp.roundToPx())
        )
        assertMutedColor(
            "Checked receive switch",
            mutedPrimary,
            receive.getPixel(receive.width / 4, receive.height / 2)
        )
    }

    private fun assertMutedColor(label: String, expected: Color, actual: Int) {
        val rendered = Color(actual)
        val tolerance = 2f / 255f
        assertEquals("$label red", expected.red, rendered.red, tolerance)
        assertEquals("$label green", expected.green, rendered.green, tolerance)
        assertEquals("$label blue", expected.blue, rendered.blue, tolerance)
    }
}
