package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.identity.AccountState
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profileShowsServiceNicknameAndKeepsLogoutConfirmation() {
        var state by mutableStateOf(AccountState())
        var logoutCount = 0
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                PinGlideTheme {
                    SettingsPage(
                        stringResource(R.string.settings_title),
                        onBack = {},
                        rootLayout = true,
                        contentTopPadding = 10.dp
                    ) {
                        AccountScreenContent(state) { logoutCount++ }
                    }
                }
            }
        }
        compose.onNodeWithText("正在获取账号资料…").assertIsDisplayed()
        compose.runOnIdle { state = AccountState(loading = false, failed = true) }
        compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        compose.runOnIdle { state = AccountState(loading = false, nickname = "示例昵称") }
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("示例昵称").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        captureSettledUiEvidence("settings-account.png", compose.onRoot())
        compose.onNodeWithText("退出登录").assertHasClickAction().performClick()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, logoutCount) }
        compose.onNodeWithText("退出登录").assertIsDisplayed().performClick()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNode(hasText("退出登录") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1, logoutCount) }
    }
}
