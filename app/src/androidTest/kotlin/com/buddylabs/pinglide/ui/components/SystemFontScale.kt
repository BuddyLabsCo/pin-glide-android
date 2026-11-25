package com.buddylabs.pinglide.ui.components

import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

internal fun AndroidComposeTestRule<*, ComponentActivity>.withSystemFontScale(
    scale: Float,
    block: () -> Unit
) {
    val original = Settings.System.getString(activity.contentResolver, Settings.System.FONT_SCALE)
    val originalScale = runOnUiThread { activity.resources.configuration.fontScale }
    try {
        setSystemFontScale(scale.toString(), scale)
        block()
    } finally {
        if (original == null) {
            setSystemFontScale(originalScale.toString(), originalScale)
        }
        setSystemFontScale(original, originalScale)
    }
}

private fun AndroidComposeTestRule<*, ComponentActivity>.setSystemFontScale(
    value: String?,
    expectedScale: Float
) {
    val command = if (value == null) {
        "settings delete system font_scale"
    } else {
        require(value.toFloatOrNull()?.isFinite() == true && value.none(Char::isWhitespace))
        "settings put system font_scale $value"
    }
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
        command
    )
    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    waitUntil(5_000) {
        runOnUiThread {
            Settings.System.getString(activity.contentResolver, Settings.System.FONT_SCALE) ==
                value && activity.resources.configuration.fontScale == expectedScale
        }
    }
}
