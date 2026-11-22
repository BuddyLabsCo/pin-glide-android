package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

class HomeSmsRecoveryScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightHomeShowsPermissionRecoveryAndPreservesTargets() {
        verifyRecovery(Configuration.UI_MODE_NIGHT_NO)
    }

    @Test
    fun darkHomeShowsPermissionRecoveryAndPreservesTargets() {
        verifyRecovery(Configuration.UI_MODE_NIGHT_YES)
    }

    private fun verifyRecovery(nightMode: Int) {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val now = Instant.now()
        val state = HomeState(
            targets = listOf(
                TargetDevice("home", "我的电脑", true, true, Presence.Online, now.toString()),
                TargetDevice(
                    "office",
                    "办公室电脑",
                    true,
                    true,
                    Presence.Online,
                    now.minusSeconds(130).toString()
                )
            ),
            listed = true,
            authorization = AuthorizationState.Ready
        )
        val recovery = mutableStateOf(SmsRecovery.Idle)
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
                            smsGranted = false,
                            smsRecovery = recovery.value,
                            home = home,
                            onRestoreSms = { recovery.value = SmsRecovery.Pending },
                            onAdd = {},
                            onSettings = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("权限失效").assertIsDisplayed()
        compose.onNodeWithText("重新开启短信权限后，转发会自动恢复；无需重新登录或重新配对。")
            .assertIsDisplayed()
        state.targets.forEach { target ->
            compose.onNodeWithContentDescription("${target.alias}接收短信")
                .assertIsDisplayed().assertIsEnabled().assertIsOn()
        }
        val restore = compose.onNodeWithText("重新开启短信权限")
        restore.assertIsDisplayed().assertIsEnabled().performClick()
        restore.assertIsNotEnabled()
    }
}
