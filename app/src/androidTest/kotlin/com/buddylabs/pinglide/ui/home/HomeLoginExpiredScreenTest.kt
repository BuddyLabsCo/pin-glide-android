package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class HomeLoginExpiredScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightHomeShowsRecoveryAndLocksCachedTargets() {
        verifyRecovery(Configuration.UI_MODE_NIGHT_NO, secondTargetEnabled = true)
    }

    @Test
    fun darkHomeShowsRecoveryAndKeepsAnOffSwitchOff() {
        verifyRecovery(Configuration.UI_MODE_NIGHT_YES, secondTargetEnabled = false)
    }

    private fun verifyRecovery(nightMode: Int, secondTargetEnabled: Boolean) {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val now = Instant.now().toString()
        val state = HomeState(
            targets = listOf(
                TargetDevice("home", "我的电脑", true, true, Presence.Online, now),
                TargetDevice("office", "办公室电脑", secondTargetEnabled, true, Presence.Offline, now)
            ),
            listed = true,
            listRefreshFailed = true,
            authorization = AuthorizationState.LoginRequired
        )
        val dark = nightMode == Configuration.UI_MODE_NIGHT_YES
        val systemBars = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge(systemBars, systemBars) }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
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
        }

        compose.onNodeWithText("需要重新登录").assertIsDisplayed()
        compose.onNodeWithText("登录失效").assertIsDisplayed()
        compose.onNodeWithText(
            "登录凭据已失效，无法自动续期。使用原 QQ 账号重新登录，可保留当前设备已有的配对。"
        ).assertIsDisplayed()
        compose.onNodeWithText("使用 QQ 重新登录").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("已有配对仍保留。当前无法确认设备是否在线。").assertDoesNotExist()
        compose.onNodeWithText("添加设备").assertDoesNotExist()
        compose.onAllNodesWithText("状态待更新").assertCountEquals(2)
        state.targets.forEach { target ->
            compose.onNodeWithText(target.alias).assertIsDisplayed().assertIsNotEnabled()
            val receive = compose.onNodeWithContentDescription("${target.alias}接收短信")
                .assertIsDisplayed().assertIsNotEnabled()
            if (target.enabled) receive.assertIsOn() else receive.assertIsOff()
        }
    }
}
