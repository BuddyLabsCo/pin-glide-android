package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal fun AndroidComposeTestRule<*, ComponentActivity>.assertReadableRevocationText(
    text: String
) {
    val node = onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    val bounds = node.getUnclippedBoundsInRoot()
    val clipped = node.fetchSemanticsNode().boundsInRoot
    with(density) {
        assertDp("Unclipped $text width", clipped.width.toDp(), bounds.right - bounds.left)
        assertDp("Unclipped $text height", clipped.height.toDp(), bounds.bottom - bounds.top)
    }
    val layout = textLayout(text)
    assertEquals(2f, layout.layoutInput.density.fontScale)
    assertFalse("$text overflows vertically: ${layout.size}", layout.didOverflowHeight)
    for (line in 0 until layout.lineCount) {
        assertFalse("$text line $line is ellipsized", layout.isLineEllipsized(line))
        assertTrue(
            "$text line $line extends outside ${layout.size}",
            layout.getLineLeft(line) >= -1f &&
                layout.getLineRight(line) <= layout.size.width + 1f &&
                layout.getLineTop(line) >= -1f &&
                layout.getLineBottom(line) <= layout.size.height + 1f
        )
    }
}
