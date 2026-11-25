package com.buddylabs.pinglide.ui.pairing

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.TestMessageAction
import com.buddylabs.pinglide.ui.home.textTimeoutAccessibility
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PairingSuccessScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var sends = 0
    private var leaves = 0
    private val response = CompletableDeferred<TestMessageOutcome>()
    private var bottom = 0.dp

    @Test
    fun compactEnglishWindowWithLargeFontsCanReachBothActions() {
        render(120.dp)
        heading().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send test message").performScrollTo().assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, sends) }
        compose.onNodeWithText(
            "Sending…"
        ).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Go to Home").performScrollTo().assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, leaves) }
        heading().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fittingLargeFontWindowKeepsActionsAtBottomDuringTransientFailureFeedback() {
        render(650.dp)
        heading().assertIsDisplayed()
        val send = compose.onNodeWithText("Send test message").assertIsDisplayed()
        val home = compose.onNodeWithText("Go to Home").assertIsDisplayed()
        val sendBounds = send.getUnclippedBoundsInRoot()
        val homeBounds = home.getUnclippedBoundsInRoot()
        assertTrue("Actions must reach the bottom", homeBounds.bottom >= bottom - 12.dp)
        send.performClick()
        compose.onNodeWithText("Sending…").assertIsNotEnabled()
        response.complete(TestMessageOutcome("My device", TestMessageResult.Rejected))
        val message = "My device: Test message was not sent: delivery was rejected"
        val notice = compose.onNode(
            hasText(message) and
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        ).assertIsDisplayed()
        assertTrue(notice.getUnclippedBoundsInRoot().bottom < sendBounds.top)
        assertEquals(sendBounds, send.assertIsEnabled().getUnclippedBoundsInRoot())
        assertEquals(homeBounds, home.getUnclippedBoundsInRoot())
        compose.mainClock.advanceTimeBy(4_100)
        notice.assertDoesNotExist()
        assertEquals(sendBounds, send.getUnclippedBoundsInRoot())
        assertEquals(homeBounds, home.getUnclippedBoundsInRoot())
    }

    private fun heading() = compose.onNode(
        hasText(
            "Pairing complete"
        ) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    )

    private fun render(usableHeight: Dp) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalAccessibilityManager provides textTimeoutAccessibility(4_000)
            ) {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                bottom = usableHeight + insets.calculateTopPadding()
                val scope = rememberCoroutineScope()
                val action = remember {
                    TestMessageAction(scope) {
                        sends++
                        response.await()
                    }.apply { enter("fixture") }
                }
                Box(Modifier.height(bottom + insets.calculateBottomPadding())) {
                    LocalizedContent(AppLanguage.English) {
                        PinGlideTheme {
                            PairingSuccessScreen(action) { leaves++ }
                        }
                    }
                }
            }
        }
    }
}
