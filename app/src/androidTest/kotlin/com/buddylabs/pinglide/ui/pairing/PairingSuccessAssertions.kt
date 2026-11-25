package com.buddylabs.pinglide.ui.pairing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal fun ComposeContentTestRule.assertPairingSuccess(
    pairingId: String,
    testedPairings: ConcurrentLinkedQueue<String>
) {
    val heading = onNode(
        hasText("配对完成") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    ).assertIsDisplayed()
    val send = onNodeWithText("发送测试消息").assertIsDisplayed()
    val home = onNodeWithText("进入首页").assertIsDisplayed()
    val viewport = onRoot().fetchSemanticsNode().boundsInRoot
    val title = heading.fetchSemanticsNode().boundsInRoot
    val sendBounds = send.fetchSemanticsNode().boundsInRoot
    val homeBounds = home.fetchSemanticsNode().boundsInRoot
    assertTrue(title.center.y > viewport.top + viewport.height * 0.35f)
    assertTrue(title.center.y < viewport.top + viewport.height * 0.65f)
    assertEquals(viewport.center.x, title.center.x, 2f)
    assertTrue(sendBounds.top > viewport.top + viewport.height * 0.75f)
    assertTrue(homeBounds.top >= sendBounds.bottom)
    assertTrue(testedPairings.isEmpty())
    captureConsentEvidence("pairing-success.png")
    send.performClick()
    waitUntil(5_000) { testedPairings.isNotEmpty() }
    assertEquals(listOf(pairingId), testedPairings.toList())
    onNodeWithText("测试消息已发送").assertIsDisplayed()
    home.performClick()
    heading.assertDoesNotExist()
}
