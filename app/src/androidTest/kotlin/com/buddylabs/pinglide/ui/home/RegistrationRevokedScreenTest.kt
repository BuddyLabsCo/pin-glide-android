package com.buddylabs.pinglide.ui.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegistrationRevokedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun revokedSenderCanStartFreshRegistrationFromTheBottomAction() {
        var requestedRegistration = false
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                PinGlideTheme {
                    RegistrationRevokedScreen { requestedRegistration = true }
                }
            }
        }

        compose.onNodeWithText("设备注册已撤销")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("退出并重新登录")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(48.dp)
            .performClick()
        compose.runOnIdle { assertTrue(requestedRegistration) }
    }
}
