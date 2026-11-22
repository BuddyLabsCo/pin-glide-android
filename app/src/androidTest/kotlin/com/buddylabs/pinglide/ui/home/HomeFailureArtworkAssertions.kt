package com.buddylabs.pinglide.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import kotlin.math.abs
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

internal fun Density.assertCachedFailureArtwork(
    screenshot: Bitmap,
    context: Context,
    warningTextLeft: Dp,
    warningTextTop: Dp
) {
    val heroTop = 14.dp.roundToPx()
    val heroColors = (24.dp.roundToPx() until screenshot.width - 24.dp.roundToPx())
        .step(1.dp.roundToPx()).map { column -> screenshot.getPixel(column, heroTop) }
    assertTrue("The warning hero has a gradient", heroColors.first() != heroColors[96])
    val illustrated = heroColors.zipWithNext().any { (first, second) ->
        abs(Color.red(first) - Color.red(second)) >= 5 ||
            abs(Color.green(first) - Color.green(second)) >= 5 ||
            abs(Color.blue(first) - Color.blue(second)) >= 5
    }
    assertTrue("The decorative ring is visible on the warning hero", illustrated)

    val iconLeft = (warningTextLeft - 28.dp).roundToPx()
    val iconTop = warningTextTop.roundToPx()
    val iconSize = 18.dp.roundToPx()
    val iconColor = context.getColor(R.color.setup_on_warning)
    val cardColor = context.getColor(R.color.setup_warning_container)
    assertTrue("The warning has a tinted card", iconLeft >= 0)
    assertEquals(cardColor, screenshot.getPixel(iconLeft, iconTop))
    val iconVisible = (iconLeft until iconLeft + iconSize).any { column ->
        (iconTop until iconTop + iconSize).any { row ->
            screenshot.getPixel(column, row) == iconColor
        }
    }
    assertTrue("The information icon is visible beside the warning", iconVisible)
}
