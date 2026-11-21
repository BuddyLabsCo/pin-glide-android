package com.buddylabs.pinglide.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class QqLoadingEmblemTest {
    private val animatorScale = mutableFloatStateOf(1f)
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = animatorScale.floatValue
    }

    @get:Rule
    val compose = createComposeRule(effectContext = durationScale)

    @Test
    fun disablingMotionHoldsVisibleArcAndKeepsIndeterminateProgress() {
        var arcColor = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PinGlideTheme {
                arcColor = MaterialTheme.colorScheme.primary.toArgb()
                QqLoadingEmblem()
            }
        }
        val progress = compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        )
        compose.mainClock.advanceTimeBy(200)
        val active = progress.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(250)
        assertFalse(active.sameAs(progress.captureToImage().asAndroidBitmap()))

        compose.runOnIdle { animatorScale.floatValue = 0f }
        compose.mainClock.advanceTimeBy(200)
        val still = progress.captureToImage().asAndroidBitmap()
        assertEquals(arcColor, still.getPixel(still.width / 2, still.width / 56))
        compose.mainClock.advanceTimeBy(250)
        assertTrue(still.sameAs(progress.captureToImage().asAndroidBitmap()))
    }
}
