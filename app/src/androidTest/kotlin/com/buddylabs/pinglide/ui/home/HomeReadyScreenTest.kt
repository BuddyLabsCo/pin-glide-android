package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeReadyScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyHomeShowsBrandAndKeepsTwoTargetsAndActionsAccessible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val chinese = app.resources.configuration.locales[0].language == "zh"
        val first = if (chinese) "我的电脑" else "My computer"
        val second = if (chinese) "办公室电脑" else "Office computer"
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
                    now.minusSeconds(120).toString()
                )
            ),
            listed = true,
            authorization = AuthorizationState.Ready
        )
        var addRequests = 0
        var settingsRequests = 0
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
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
        compose.onNodeWithText(app.getString(R.string.brand_wordmark)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.home_ready_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.home_count, 2)).assertIsDisplayed()
        compose.onNodeWithText(first).assertIsDisplayed()
        compose.onNodeWithText(second).assertIsDisplayed()
        val online = app.getString(R.string.home_online)
        compose.onNodeWithText(
            app.getString(
                R.string.home_presence,
                online,
                app.getString(R.string.home_presence_just_now)
            )
        ).assertIsDisplayed()
        compose.onNodeWithText(
            app.getString(R.string.home_presence, online, if (chinese) "2分钟前" else "2 minutes ago")
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.home_receive, first))
            .assertIsDisplayed().assertIsOn()
        compose.onNodeWithContentDescription(app.getString(R.string.home_receive, second))
            .assertIsDisplayed().assertIsOn()
        compose.onNodeWithText(app.getString(R.string.home_add)).performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.settings_title)).performClick()
        compose.runOnIdle {
            assertEquals(1, addRequests)
            assertEquals(1, settingsRequests)
        }
    }
}
