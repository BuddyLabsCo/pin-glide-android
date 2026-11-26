package com.buddylabs.pinglide.ui.settings

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence

internal fun ComposeTestRule.assertPendingAccountProfile() {
    val loading = hasText("正在获取账号资料…")
    onNodeWithText("正在获取账号资料…").assertIsDisplayed().assertHasNoClickAction()
    onNode(
        loading and hasAnyAncestor(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        ),
        useUnmergedTree = true
    ).assertIsDisplayed()
    onNodeWithText("QQ 账号").assertDoesNotExist()
    captureSettledUiEvidence(
        "settings-account-loading.png",
        onNode(isRoot() and hasAnyDescendant(loading))
    )
    onNodeWithText("退出登录").assertIsDisplayed().assertIsEnabled().performClick()
    onNodeWithText("退出登录？").assertIsDisplayed()
    onNodeWithText("取消").performClick()
    onNodeWithText("正在获取账号资料…").assertIsDisplayed()
}
