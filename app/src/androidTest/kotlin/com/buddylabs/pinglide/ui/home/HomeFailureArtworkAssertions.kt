package com.buddylabs.pinglide.ui.home

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal fun Density.assertInitialFailureArtwork(screenshot: Bitmap, context: Context) {
    val heroTop = 14.dp.roundToPx()
    val heroLeft = screenshot.getPixel(20.dp.roundToPx(), heroTop)
    val heroMiddle = screenshot.getPixel(120.dp.roundToPx(), heroTop)
    assertTrue("The warning hero has a gradient", heroLeft != heroMiddle)
    val emblemColor = context.getColor(R.color.primary_container)
    val centerX = screenshot.width / 2
    val emblemRows = (0 until screenshot.height)
        .filter { row -> screenshot.getPixel(centerX, row) == emblemColor }
    assertTrue("The cloud-off emblem is visible", emblemRows.isNotEmpty())
    val slashOffset = 11.dp.roundToPx()
    val slashY = (emblemRows.first() + emblemRows.last()) / 2 - slashOffset
    assertEquals(
        "The cloud-off slash extends above and left of the cloud",
        context.getColor(R.color.primary),
        screenshot.getPixel(centerX - slashOffset, slashY)
    )
}
