package com.buddylabs.pinglide.ui.settings

import android.view.KeyEvent
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
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withSystemFontScale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsPrivacyScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun privacyFromSettingsUsesApprovedHeaderAndSharedFactsWithoutAcknowledgment() {
        renderSettings(AppLanguage.SimplifiedChinese)
        val privacy = compose.onNodeWithText("隐私与数据")
        privacy.performScrollTo().performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        val back = compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        val backBounds = back.getUnclippedBoundsInRoot()
        assertTrue(backBounds.bottom - backBounds.top >= 48.dp)
        compose.onNodeWithText("知道了").assertDoesNotExist()
        val paragraphs = listOf(
            "零数据保留",
            "鹊信不会创建短信或 OTP 的转发历史，也不会把明文上传到鹊信服务。",
            "短信正文",
            "只在当前处理过程中存在于内存，完成或失败后释放。",
            "配对密钥与模型凭据",
            "由 Android Keystore 保护，不会写入日志、URL 或服务端请求。",
            "服务端只路由密文",
            "服务不会读取短信正文、OTP 或客户端加密密钥。",
            "本地诊断",
            "只保留时间、结果类别、状态码、请求 ID 和目标数量等非内容元数据。",
            "卸载应用或清除应用数据会丢失本机身份与配对密钥，需要重新进行设备配对。"
        )
        val bounds = paragraphs.map {
            compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        assertTrue(bounds.zipWithNext().all { (previous, next) -> previous.bottom <= next.top })
        captureSettledUiEvidence(
            "settings-privacy.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("零数据保留")))
        )
        back.performClick()
        privacy.assertIsDisplayed()
        privacy.performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        privacy.assertIsDisplayed()
    }

    @Test
    fun englishPrivacyFactsAndBackRemainReachableAtDoubleFontScale() {
        compose.withSystemFontScale(2f) {
            renderSettings(AppLanguage.English)
            compose.onNodeWithText("Privacy and data").performScrollTo().performClick()
            compose.onNodeWithText("Zero data retention").assertIsDisplayed()
            compose.onNodeWithText("Got it").assertDoesNotExist()
            compose.onNodeWithText(
                "Uninstalling the app or clearing its data removes this device’s identity and " +
                    "Pairing keys. Pair your target devices again."
            ).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
            compose.onNodeWithText("Privacy and data").assertIsDisplayed()
        }
    }

    private fun renderSettings(language: AppLanguage) {
        val app = compose.activity.application as PinGlideApplication
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(language) {
                PinGlideTheme {
                    SettingsNavigation(
                        language = language,
                        onLanguage = {},
                        onBatterySettings = { false },
                        installedVersion = "1.0",
                        account = app.accountSession,
                        senders = app.accountSenders,
                        revocation = app.senderRevocation,
                        onLogout = {},
                        onDismiss = { error("Privacy back must return to Settings") }
                    )
                }
            }
        }
    }
}
