package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

class HomeReadyScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readyHomeUsesEnglishWhenSelected() {
        verifyHome(AppLanguage.English, "en", "Online · State changed: 2 minutes ago")
    }

    @Test
    fun readyHomeUsesSimplifiedChineseWhenSelected() {
        verifyHome(AppLanguage.SimplifiedChinese, "zh-Hans", "在线 · 状态变化：2分钟前")
    }

    @Test
    fun readyHomeFallsBackToSimplifiedChineseForFrenchSystemLocale() {
        verifyHome(AppLanguage.System, "zh-Hans", "在线 · 状态变化：2分钟前")
    }

    private fun verifyHome(language: AppLanguage, expectedLocale: String, officePresence: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PinGlideApplication
        val home = HomeViewModel(app.ownPairings, app.senderRestoration()) { null }
        val frenchSystem = Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("fr-FR"))
        }
        val expected = app.createConfigurationContext(
            Configuration(frenchSystem).apply {
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
            CompositionLocalProvider(LocalConfiguration provides frenchSystem) {
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
