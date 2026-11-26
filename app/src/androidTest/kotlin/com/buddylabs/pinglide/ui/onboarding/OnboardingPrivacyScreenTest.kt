package com.buddylabs.pinglide.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.identity.LogoutState
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withSystemFontScale
import com.buddylabs.pinglide.ui.navigation.SetupNavigation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingPrivacyScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun privacyFromWelcomeKeepsCloseAndAcknowledgmentReturningToWelcome() =
        compose.withSystemFontScale(1f) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
            compose.setContent {
                LocalizedContent(AppLanguage.SimplifiedChinese) {
                    PinGlideTheme {
                        SetupNavigation(
                            progress = UnstartedSetup,
                            smsGranted = false,
                            smsRequestPending = false,
                            onRequestSms = { error("Unexpected SMS request") },
                            onCheckSms = { error("Unexpected SMS check") },
                            onApplicationSettings = { error("Unexpected application settings") },
                            onBatterySettings = { error("Unexpected battery settings") },
                            loginState = LoginState.Entry,
                            onLogin = { error("Unexpected login") },
                            onNewAccount = { error("Unexpected new Account") },
                            logoutState = LogoutState.Ready,
                            onRetryLogout = { error("Unexpected logout") },
                            onCancelLogin = { error("Unexpected login cancellation") },
                            onRetryIdentity = { error("Unexpected identity retry") },
                            onNewRegistration = { error("Unexpected registration") },
                            onBackground = { error("Privacy dismissal must return to Welcome") }
                        )
                    }
                }
            }
            val privacy = compose.onNodeWithText("隐私与数据处理")
            val welcome = compose.onNodeWithText("开始设置")
            val heading = compose.onNodeWithText("零数据保留")
            val close = compose.onNodeWithContentDescription("关闭")
            val acknowledgment = compose.onNodeWithText("知道了")
            privacy.performScrollTo().performClick()
            heading.assertIsDisplayed()
            val closeBounds = close.assertIsDisplayed().getUnclippedBoundsInRoot()
            val acknowledgmentBounds = acknowledgment.assertIsDisplayed().getUnclippedBoundsInRoot()
            val notice = compose.onNodeWithText(
                "卸载应用或清除应用数据会丢失本机身份与配对密钥，需要重新进行设备配对。"
            ).assertIsDisplayed().getUnclippedBoundsInRoot()
            captureSettledUiEvidence(
                "onboarding-privacy.png",
                compose.onNode(isRoot() and hasAnyDescendant(hasText("零数据保留")))
            )
            assertTrue("Close bounds: $closeBounds", closeBounds.bottom - closeBounds.top >= 48.dp)
            assertTrue(acknowledgmentBounds.bottom - acknowledgmentBounds.top >= 48.dp)
            assertTrue(acknowledgmentBounds.top > notice.bottom)
            close.performClick()
            heading.assertDoesNotExist()
            welcome.assertIsDisplayed()
            privacy.performScrollTo().performClick()
            heading.assertIsDisplayed()
            acknowledgment.performClick()
            heading.assertDoesNotExist()
            welcome.assertIsDisplayed()
        }
}

private object UnstartedSetup : SetupProgress {
    override val state = SetupState()
    override fun start() = error("Privacy must not start setup")
    override fun recordSmsRequest() = error("Unexpected SMS request")
    override fun completeSmsPrerequisite() = error("Unexpected SMS completion")
    override fun acknowledgeBattery() = error("Unexpected battery acknowledgment")
    override fun complete() = error("Privacy must not complete setup")
}
