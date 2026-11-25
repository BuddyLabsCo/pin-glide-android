package com.buddylabs.pinglide.ui.pairing

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.pairing.PairingStage
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PairingProgressScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun displaysOnlyTheCurrentStageWithAccessibleProgress() {
        val stage = mutableStateOf(PairingStage.Opening)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(AppLanguage.English) {
                PinGlideTheme { PairingProgressScreen(stage.value) }
            }
        }
        val labels = listOf(
            "Opening pairing channel",
            "Generating device identity",
            "Exchanging encryption keys",
            "Confirming pairing information"
        )
        PairingStage.entries.forEachIndexed { index, current ->
            compose.runOnIdle { stage.value = current }
            labels.forEachIndexed { labelIndex, label ->
                if (labelIndex == index) {
                    compose.onNodeWithText(label).assertIsDisplayed()
                } else {
                    compose.onNodeWithText(label).assertDoesNotExist()
                }
            }
        }
        compose.assertAccessiblePairingProgress()
    }

    @Test
    fun keyExchangeDisplaysTheApprovedChineseHeadingAndInstruction() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                PinGlideTheme { PairingProgressScreen(PairingStage.Key) }
            }
        }
        compose.onNode(
            hasText("正在交换加密密钥") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).assertIsDisplayed()
        compose.onNodeWithText(INSTRUCTION).assertIsDisplayed()
        compose.onNodeWithText(OPENING_HEADING).assertDoesNotExist()
        compose.onNodeWithText("正在生成设备身份").assertDoesNotExist()
        compose.onNodeWithText("正在确认配对信息").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun compactViewportDisplaysHeadingAndInstructionWithoutScrolling() {
        renderCompact(400.dp, 1f)
        assertHeadingAndInstructionFit()
    }

    @Test
    fun enlargedFontDisplaysHeadingAndInstructionWhenTheyFit() {
        renderCompact(560.dp, 2f)
        assertHeadingAndInstructionFit()
    }

    @Test
    fun overflowingContentCanScrollToTheInstruction() {
        renderCompact(240.dp, 2f)
        assertTrue(scrollRange().maxValue() > 0f)
        compose.onNodeWithText(OPENING_HEADING).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(INSTRUCTION).performScrollTo().assertIsDisplayed()
    }

    private fun renderCompact(usableHeight: Dp, fontScale: Float) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                val windowHeight =
                    usableHeight + insets.calculateTopPadding() + insets.calculateBottomPadding()
                Box(Modifier.height(windowHeight)) {
                    LocalizedContent(AppLanguage.SimplifiedChinese) {
                        PinGlideTheme { PairingProgressScreen(PairingStage.Opening) }
                    }
                }
            }
        }
    }

    private fun assertHeadingAndInstructionFit() {
        compose.onNodeWithText(OPENING_HEADING).assertIsDisplayed()
        compose.onNodeWithText(INSTRUCTION).assertIsDisplayed()
        assertEquals(0f, scrollRange().maxValue())
    }

    private fun scrollRange() = compose.onNode(hasScrollAction()).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]

    private companion object {
        const val OPENING_HEADING = "正在打开配对通道"
        const val INSTRUCTION = "保持接收端上的鹊信窗口打开。完成后会自动进入配对完成页面。"
    }
}

internal fun ComposeTestRule.assertAccessiblePairingProgress() {
    onNode(
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    ).assertIsDisplayed()
    onNode(
        SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate
        )
    ).assertIsDisplayed()
    onAllNodes(hasClickAction()).assertCountEquals(0)
}
