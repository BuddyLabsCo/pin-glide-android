package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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

class HomeOfflineScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allOfflineHomeShowsWarningAndKeepsBothTargetsSelected() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val now = Instant.now()
        val state = HomeState(
            targets = listOf(
                TargetDevice("home", "我的电脑", true, true, Presence.Offline, now.toString()),
                TargetDevice(
                    "office",
                    "办公室电脑",
                    true,
                    true,
                    Presence.Offline,
                    now.minusSeconds(130).toString()
                )
            ),
            listed = true,
            authorization = AuthorizationState.Ready
        )
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
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

        compose.onNodeWithText("接收目标均已离线").assertIsDisplayed()
        compose.onNodeWithText("全部离线").assertIsDisplayed()
        compose.onNodeWithText("离线期间的短信不会补发。", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("离线 · 状态变化", substring = true).assertCountEquals(2)
        compose.onNodeWithContentDescription("我的电脑接收短信").assertIsOn()
        compose.onNodeWithContentDescription("办公室电脑接收短信").assertIsOn()
    }
}
