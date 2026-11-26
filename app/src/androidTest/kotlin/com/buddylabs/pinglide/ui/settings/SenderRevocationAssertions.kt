package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal fun AndroidComposeTestRule<*, ComponentActivity>.assertReadableRevocationText(
    text: String
): TextLayoutResult {
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
        assertTrue(
            "$text line $line extends beyond ${layout.size.width}px",
            layout.getLineLeft(line) >= -1f &&
                layout.getLineRight(line) <= layout.size.width + 1f
        )
    }
    return layout
}
