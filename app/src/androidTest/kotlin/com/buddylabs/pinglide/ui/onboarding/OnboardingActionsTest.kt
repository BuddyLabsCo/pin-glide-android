package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tappingQqSignInRequestsLogin() {
        var loginRequests = 0
        compose.setContent {
            PinGlideTheme {
                QqLoginScreen(
                    state = LoginState.Entry,
                    onLogin = { loginRequests++ },
                    onNewAccount = {},
                    onCancel = {},
                    onRetryStorage = {},
                    onBack = {}
                )
            }
        }

        compose.runOnIdle { assertEquals(0, loginRequests) }
        compose.onNodeWithText(text(R.string.setup_qq_sign_in))
            .performScrollTo()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, loginRequests) }
    }

    @Test
    fun smsPermissionCannotBeRequestedAgainWhilePending() {
        val pending = mutableStateOf(false)
        var permissionRequests = 0
        compose.setContent {
            PinGlideTheme {
                SmsPermissionScreen(
                    denied = false,
                    requestPending = pending.value,
                    onRequest = {
                        permissionRequests++
                        pending.value = true
                    },
                    onSettings = { false },
                    onCheckAgain = { false }
                )
            }
        }

        val action = compose.onNodeWithText(text(R.string.setup_allow_sms)).performScrollTo()
        action.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, permissionRequests) }
        action.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, permissionRequests) }
    }

    @Test
    fun deniedSmsPermissionShowsRecoveryFeedback() {
        compose.setContent {
            PinGlideTheme {
                SmsPermissionScreen(
                    denied = true,
                    requestPending = false,
                    onRequest = { error("Denied SMS permission must use recovery actions") },
                    onSettings = { false },
                    onCheckAgain = { false }
                )
            }
        }

        compose.onNodeWithText(text(R.string.setup_sms_denied_hint)).assertExists()
        compose.onNodeWithText(text(R.string.setup_open_application_settings))
            .performScrollTo()
            .performTouchInput { click() }
        compose.onNodeWithText(text(R.string.setup_settings_unavailable)).assertExists()
        compose.onNodeWithText(text(R.string.setup_check_again))
            .performScrollTo()
            .performTouchInput { click() }
        compose.onNodeWithText(text(R.string.setup_settings_unavailable)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.setup_sms_still_denied)).assertExists()
    }

    private fun text(resource: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
