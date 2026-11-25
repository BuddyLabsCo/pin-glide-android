package com.buddylabs.pinglide.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun uiEvidenceEnabled(): Boolean {
    val arguments = InstrumentationRegistry.getArguments()
    return arguments.getString("uiEvidence") == "true" ||
        arguments.getString("consentEvidence") == "true"
}

internal fun captureUiEvidence(filename: String) {
    if (!uiEvidenceEnabled()) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    File(directory, filename).outputStream().use {
        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}

internal fun captureSettledUiEvidence(filename: String, root: SemanticsNodeInteraction) {
    if (!uiEvidenceEnabled()) return
    root.captureToImage()
    InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
    captureUiEvidence(filename)
}
