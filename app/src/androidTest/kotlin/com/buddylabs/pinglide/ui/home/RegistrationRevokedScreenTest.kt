package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegistrationRevokedScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun revokedSenderCanStartFreshRegistrationFromTheBottomAction() {
        var requestedRegistration = false
        var bottomInset = 0.dp
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val safeBottomInset = WindowInsets.safeDrawing.asPaddingValues()
                .calculateBottomPadding()
            SideEffect { bottomInset = safeBottomInset }
            Box(Modifier.height(520.dp).testTag("revokedViewport")) {
                LocalizedContent(AppLanguage.SimplifiedChinese) {
                    PinGlideTheme {
                        RegistrationRevokedScreen { requestedRegistration = true }
                    }
                }
            }
        }

        compose.onNodeWithText("设备注册已撤销")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(
            "当前设备的旧注册已被安全撤销，不能恢复。" +
                "重新登录会创建新的设备注册，之后需要重新配对设备。"
        ).assertIsDisplayed()
        val action = compose.onNodeWithText("退出并重新登录")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(48.dp)
        val viewport = compose.onNodeWithTag("revokedViewport").getUnclippedBoundsInRoot()
        val safeBottom = viewport.bottom - compose.runOnIdle { bottomInset }
        assertEquals(safeBottom.value, action.getUnclippedBoundsInRoot().bottom.value, 0.5f)
        action.performClick()
        compose.runOnIdle { assertTrue(requestedRegistration) }
    }
}
