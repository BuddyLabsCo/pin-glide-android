package com.buddylabs.pinglide.ui.components

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt

internal fun runUiTestShell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
).bufferedReader().use { it.readText() }

internal fun AndroidComposeTestRule<*, ComponentActivity>.withCompactViewport(
    widthDp: Int,
    heightDp: Int,
    check: () -> Unit
) {
    val originalSize = Regex("Override size: (\\d+x\\d+)")
        .find(runUiTestShell("wm size"))?.groupValues?.get(1)
    val originalFont = runUiTestShell("settings get system font_scale").trim()
    val originalConfiguration = Configuration(activity.resources.configuration)
    val density = activity.resources.displayMetrics.density
    try {
        runUiTestShell("settings put system font_scale 2.0")
        runUiTestShell(
            "wm size ${(widthDp * density).roundToInt()}x${(heightDp * density).roundToInt()}"
        )
        waitUntil(5_000) {
            runOnUiThread {
                val configuration = activity.resources.configuration
                configuration.screenWidthDp <= widthDp &&
                    configuration.screenHeightDp <= heightDp &&
                    configuration.fontScale == 2f
            }
        }
        check()
    } finally {
        runUiTestShell("wm size ${originalSize ?: "reset"}")
        if (originalFont == "null") {
            runUiTestShell("settings delete system font_scale")
        } else {
            runUiTestShell("settings put system font_scale $originalFont")
        }
        waitUntil(5_000) {
            runOnUiThread {
                val configuration = activity.resources.configuration
                configuration.screenWidthDp == originalConfiguration.screenWidthDp &&
                    configuration.screenHeightDp == originalConfiguration.screenHeightDp &&
                    configuration.fontScale == originalConfiguration.fontScale
            }
        }
    }
}
