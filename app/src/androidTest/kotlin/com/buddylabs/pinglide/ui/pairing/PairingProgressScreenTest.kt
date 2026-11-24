package com.buddylabs.pinglide.ui.pairing

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.pairing.PairingStage
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
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
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        ).assertIsDisplayed()
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        ).assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
