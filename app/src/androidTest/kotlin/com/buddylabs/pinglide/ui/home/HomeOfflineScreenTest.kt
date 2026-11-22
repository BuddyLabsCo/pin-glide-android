package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeOfflineScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightHomeShowsOfflineWarningAndKeepsBothTargetsSelected() {
        verifyOfflineHome(Configuration.UI_MODE_NIGHT_NO, Color(0xFF003832).copy(alpha = 0.78f))
    }

    @Test
    fun darkHomeShowsOfflineWarningAndKeepsBothTargetsSelected() {
        verifyOfflineHome(Configuration.UI_MODE_NIGHT_YES, Color.White)
    }

    private fun verifyOfflineHome(nightMode: Int, expectedCopyColor: Color) {
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

        val title = "接收目标均已离线"
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("全部离线").assertIsDisplayed()
        val copy = compose.onNodeWithText("离线期间的短信不会补发。", substring = true)
            .assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        copy.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expectedCopyColor, layouts.single().layoutInput.style.color)

        val hero = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
                hasAnyDescendant(hasText(title)),
            useUnmergedTree = true
        )
        compose.density.assertWarningHeroArtwork(hero.captureToImage().asAndroidBitmap())

        val list = compose.onNode(hasScrollAction())
        val offlineStatus = hasText("离线 · 状态变化", substring = true)
        state.targets.forEach { target ->
            val alias = hasText(target.alias)
            list.performScrollToNode(alias)
            compose.onNode(alias and offlineStatus).assertIsDisplayed()
            compose.onNodeWithContentDescription("${target.alias}接收短信")
                .assertIsDisplayed().assertIsEnabled().assertIsOn()
        }
    }
}
