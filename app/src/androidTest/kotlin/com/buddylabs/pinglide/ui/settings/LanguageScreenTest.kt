package com.buddylabs.pinglide.ui.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LanguageScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun languagePickerMatchesApprovedGroupAndImmediatelyAppliesSelection() {
        val activity = compose.activity
        val app = activity.application as PinGlideApplication
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            var language by remember { mutableStateOf(AppLanguage.System) }
            val systemConfiguration = LocalConfiguration.current
            val referenceConfiguration = remember(systemConfiguration) {
                Configuration(systemConfiguration).apply {
                    fontScale = 1f
                    setLocales(LocaleList.forLanguageTags("zh-Hans"))
                }
            }
            CompositionLocalProvider(
                LocalConfiguration provides referenceConfiguration,
                LocalDensity provides Density(LocalDensity.current.density, 1f)
            ) {
                LocalizedContent(language) {
                    PinGlideTheme {
                        SettingsNavigation(
                            language = language,
                            onLanguage = { language = it },
                            onBatterySettings = { false },
                            installedVersion = "0.1.0",
                            account = app.accountSession,
                            senders = app.accountSenders,
                            revocation = app.senderRevocation,
                            onLogout = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("语言").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        val group = compose.onNodeWithTag("settings-group:应用语言").assertIsDisplayed()
        val bounds = group.getUnclippedBoundsInRoot()
        with(compose.density) {
            assertTrue(
                "Language group height",
                abs((bounds.bottom - bounds.top).toPx() - 180.dp.toPx()) <= 3f
            )
        }
        compose.onNodeWithText("跟随系统").assertIsSelected().assertHasClickAction()
        compose.onNodeWithText("简体中文").assertIsNotSelected().assertHasClickAction()
        compose.onNodeWithText("English").assertIsNotSelected().assertHasClickAction()
        assertLanguageArtwork(group.captureToImage().asAndroidBitmap())
        captureSettledUiEvidence(
            "settings-language.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("应用语言")))
        )
        compose.onNodeWithText("简体中文").performClick()
        compose.onNodeWithText("应用语言").assertDoesNotExist()
        compose.onNodeWithText("简体中文").assertIsDisplayed()
        compose.onNodeWithText("语言").performClick()
        compose.onNodeWithText("简体中文").assertIsSelected()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithText("Follow system").performClick()
        compose.onNodeWithText("语言").assertIsDisplayed()
        compose.onNodeWithText("语言").performClick()
        compose.onNodeWithText("跟随系统").assertIsSelected()
    }

    private fun assertLanguageArtwork(image: Bitmap) {
        val context = compose.activity
        val container = ContextCompat.getColor(context, R.color.surface_container)
        val primary = ContextCompat.getColor(context, R.color.primary)
        val onSurface = ContextCompat.getColor(context, R.color.on_surface)
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val outline = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(onSurface, if (dark) 82 else 87),
            container
        )
        val divider = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(onSurface, if (dark) 41 else 43),
            container
        )
        with(compose.density) {
            val centers = listOf(29.5f, 89.5f, 150f)
            centers.forEachIndexed { index, center ->
                val selected = index == 0
                val stroke = if (selected) primary else outline
                val thicknessSample = if (selected) primary else container
                val centerY = center.dp.roundToPx()
                assertPixelNear(
                    "radio center",
                    container,
                    image.getPixel(24.dp.roundToPx(), centerY)
                )
                assertPixelNear(
                    "radio corner",
                    container,
                    image.getPixel(15.dp.roundToPx(), (center - 9).dp.roundToPx())
                )
                assertPixelNear("radio stroke", stroke, image.getPixel(33.dp.roundToPx(), centerY))
                assertPixelNear(
                    "radio thickness",
                    thicknessSample,
                    image.getPixel(30.dp.roundToPx(), centerY)
                )
                assertPixelNear(
                    "radio top stroke",
                    stroke,
                    image.getPixel(24.dp.roundToPx(), (center - 9).dp.roundToPx())
                )
            }
            listOf(59, 119).forEach { separator ->
                val row = separator.dp.roundToPx()
                assertTrue(
                    "visible separator at $separator dp",
                    (-2..2).any { offset ->
                        pixelDifference(divider, image.getPixel(4.dp.roundToPx(), row + offset)) <=
                            4
                    }
                )
            }
        }
    }

    private fun assertPixelNear(label: String, expected: Int, actual: Int) {
        assertTrue(
            "$label expected ${Integer.toHexString(expected)}, got ${Integer.toHexString(actual)}",
            pixelDifference(expected, actual) <= 4
        )
    }

    private fun pixelDifference(expected: Int, actual: Int): Int = maxOf(
        abs(Color.red(expected) - Color.red(actual)),
        abs(Color.green(expected) - Color.green(actual)),
        abs(Color.blue(expected) - Color.blue(actual))
    )

    @Test
    fun compactLanguagePickerAtDoubleFontScaleKeepsAllRadioOptionsReachable() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            var selected by remember { mutableStateOf(AppLanguage.System) }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                Box(Modifier.height(240.dp)) {
                    LocalizedContent(AppLanguage.English) {
                        PinGlideTheme {
                            SettingsPage("Settings", onBack = {}, rootLayout = true) {
                                LanguageScreen(selected) { selected = it }
                            }
                        }
                    }
                }
            }
        }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNodeWithText("Follow system").assert(radio).assertIsSelected()
        listOf("简体中文", "English", "Follow system").forEach { label ->
            val option = compose.onNodeWithText(label).assert(radio).assertHasClickAction()
            option.performScrollTo().assertIsDisplayed().performTouchInput { click() }
            option.assertIsSelected()
        }
    }
}
