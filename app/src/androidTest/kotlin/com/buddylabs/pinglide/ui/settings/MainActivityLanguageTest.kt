package com.buddylabs.pinglide.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.MainActivity
import com.buddylabs.pinglide.data.storage.LanguagePreferences
import com.buddylabs.pinglide.domain.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MainActivityLanguageTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun changingLanguageUpdatesHomeWithoutRestartingTheActivity() {
        assumeTrue(
            "Requires a dedicated test device with completed setup and a signed-in test account",
            InstrumentationRegistry.getArguments().getString("productionLanguageEvidence") == "true"
        )
        val preferences =
            LanguagePreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        val originalLanguage = preferences.language
        preferences.language = AppLanguage.SimplifiedChinese
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithContentDescription(
                        "设置"
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription("设置").assertIsDisplayed().performClick()
                compose.onNodeWithText("语言").performClick()
                compose.onNodeWithText("简体中文").assertIsSelected()
                compose.onNodeWithText("English").performClick()
                compose.onNodeWithText("Language").assertIsDisplayed()
                compose.onNodeWithText("English").assertIsDisplayed()
                compose.onNodeWithText("App language").assertDoesNotExist()
                assertEquals(AppLanguage.English, preferences.language)
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
                compose.onNodeWithText("My devices").assertIsDisplayed()
                compose.onNodeWithContentDescription("Settings").performClick()
                compose.onNodeWithText("Language").performClick()
                compose.onNodeWithText("English").assertIsSelected()
            }
        } finally {
            preferences.language = originalLanguage
        }
    }
}
