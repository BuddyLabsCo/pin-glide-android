package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.platform.DeviceInfo
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.pairing.captureConsentEvidence
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun approvedSettingsRowsDisplayInstalledVersionAndKeepTheirActions() {
        val context = compose.activity
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!
        val destinations = mutableListOf<SettingsDestination>()
        var batteryOpens = 0
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                PinGlideTheme {
                    SettingsScreen(
                        language = AppLanguage.System,
                        installedVersion = DeviceInfo(context).installedVersion(),
                        onBatterySettings = {
                            batteryOpens++
                            false
                        },
                        onNavigate = { destinations.add(it) },
                        onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithText("隐私与数据").assertHasClickAction()
        compose.onNodeWithText(version).assertHasNoClickAction()
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("外观").assertHasNoClickAction()
        compose.onNodeWithText("QQ 账号").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("consentEvidence") == "true") {
            compose.onRoot().captureToImage()
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            captureConsentEvidence("settings.png")
        }
        listOf("QQ 账号", "语言", "设备访问管理", "隐私与数据").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        compose.onNodeWithText("电池优化").performScrollTo().performClick()
        compose.onNodeWithText("无法打开系统设置。请从设备设置中找到鹊信后重试。")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    SettingsDestination.Account,
                    SettingsDestination.Language,
                    SettingsDestination.DeviceAccess,
                    SettingsDestination.Privacy
                ),
                destinations
            )
            assertEquals(1, batteryOpens)
        }
    }

    @Test
    fun compactEnglishWindowWithLargeFontsKeepsPrivacyAndBackReachable() {
        var destination: SettingsDestination? = null
        var back = false
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                Box(Modifier.height(300.dp)) {
                    LocalizedContent(AppLanguage.English) {
                        PinGlideTheme {
                            SettingsScreen(
                                language = AppLanguage.English,
                                installedVersion = "0.1.0",
                                onBatterySettings = { true },
                                onNavigate = { destination = it },
                                onBack = { back = true }
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Privacy and data").performScrollTo().assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(SettingsDestination.Privacy, destination) }
        compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(true, back) }
    }
}
