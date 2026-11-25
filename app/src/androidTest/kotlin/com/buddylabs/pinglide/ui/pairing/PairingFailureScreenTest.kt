package com.buddylabs.pinglide.ui.pairing

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Rule
import org.junit.Test

class PairingFailureScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactWindowWithLargeFontsCanScrollToAssuranceAndBothActions() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                val height = 120.dp + insets.calculateTopPadding() +
                    insets.calculateBottomPadding()
                Box(Modifier.height(height)) {
                    LocalizedContent(AppLanguage.SimplifiedChinese) {
                        PinGlideTheme {
                            PairingFailureScreen(
                                PairingEntryState(
                                    page = PairingEntryPage.Progress,
                                    problem = PairingEntryProblem.Unavailable
                                ),
                                onRetry = {},
                                onLeave = {}
                            )
                        }
                    }
                }
            }
        }
        val labels = listOf(
            "配对未完成",
            "连接中断或确认超时，未能添加这台设备。已有设备的配对不受影响，请重新扫描后再试。",
            "重新扫描",
            "返回首页"
        )
        labels.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText(labels.first()).performScrollTo().assertIsDisplayed()
    }
}
