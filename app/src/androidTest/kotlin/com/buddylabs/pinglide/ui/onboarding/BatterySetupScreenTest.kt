package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BatterySetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun failedSettingsLaunchAnnouncesErrorAndAllowsNextOrRetry() {
        var settingsAttempts = 0
        var nextRequests = 0
        compose.setContent {
            PinGlideTheme {
                BatterySetupScreen(
                    onSettings = {
                        settingsAttempts++
                        settingsAttempts == 2
                    },
                    onNext = { nextRequests++ }
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = compose.onNodeWithText(
            context.getString(R.string.setup_open_battery_settings)
        )
        val feedback = compose.onNodeWithText(
            context.getString(R.string.setup_settings_unavailable)
        )
        feedback.assertDoesNotExist()
        settings.performScrollTo().performTouchInput { click() }
        feedback.performScrollTo().assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )

        settings.performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, settingsAttempts) }
        feedback.assertDoesNotExist()

        settings.performScrollTo().performTouchInput { click() }
        feedback.performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_next))
            .performScrollTo()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, nextRequests) }
    }
}
