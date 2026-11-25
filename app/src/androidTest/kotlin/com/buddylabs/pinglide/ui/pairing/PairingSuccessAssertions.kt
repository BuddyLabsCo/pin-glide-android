package com.buddylabs.pinglide.ui.pairing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.ui.components.captureUiEvidence
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

internal fun ComposeContentTestRule.sendTestMessageAndAssertPairingSuccess(
    pairing: OwnPairing,
    model: PairingEntryViewModel,
    store: PairingStore,
    testedPairings: ConcurrentLinkedQueue<String>
) {
    val original = pairing.copy(key = checkNotNull(pairing.key).copyOf())
    val heading = onNode(
        hasText("配对完成") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
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
    assertSuccessCheck(title.center.x, title.top)
    assertTrue(testedPairings.isEmpty())
    captureUiEvidence("pairing-success.png")
    send.performClick()
    val feedbackMatcher = hasText("测试消息已发送") and
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    waitUntil(5_000) { onAllNodes(feedbackMatcher).fetchSemanticsNodes().size == 1 }
    assertEquals(listOf(pairing.pairingId), testedPairings.toList())
    val feedback = onNode(feedbackMatcher).assertIsDisplayed()
    heading.assertIsDisplayed()
    assertEquals(sendBounds, send.assertIsEnabled().fetchSemanticsNode().boundsInRoot)
    assertEquals(homeBounds, home.assertIsEnabled().fetchSemanticsNode().boundsInRoot)
    assertTrue(feedback.fetchSemanticsNode().boundsInRoot.bottom < sendBounds.top)
    captureUiEvidence("pairing-success-feedback.png")
    assertPairingRetained(original, model, store)
    waitUntil(10_000) { onAllNodes(feedbackMatcher).fetchSemanticsNodes().isEmpty() }
    heading.assertIsDisplayed()
    assertEquals(sendBounds, send.assertIsEnabled().fetchSemanticsNode().boundsInRoot)
    assertEquals(homeBounds, home.assertIsEnabled().fetchSemanticsNode().boundsInRoot)
    assertPairingRetained(original, model, store)
    assertEquals(listOf(pairing.pairingId), testedPairings.toList())
}

private fun assertPairingRetained(
    original: OwnPairing,
    model: PairingEntryViewModel,
    store: PairingStore
) {
    assertEquals(PairingEntryPage.Success, model.state.value.page)
    assertEquals(original.pairingId, model.state.value.activatedPairingId)
    val snapshot = runBlocking { store.readPairings() }
    assertTrue(snapshot.provisional.isEmpty())
    val retained = snapshot.items.single()
    assertEquals(original.pairingId, retained.pairingId)
    assertEquals(original.receiverId, retained.receiverId)
    assertEquals(original.alias, retained.alias)
    assertEquals(original.enabled, retained.enabled)
    assertTrue(retained.keyUsable)
    assertNull(retained.provisionalSessionId)
    assertArrayEquals(original.key, retained.key)
    assertEquals(original.encryptions + 1, retained.encryptions)
}

private fun ComposeContentTestRule.assertSuccessCheck(titleCenter: Float, titleTop: Float) {
    val check = onNodeWithTag("pairing-success-check").assertIsDisplayed()
    val bounds = check.fetchSemanticsNode().boundsInRoot
    val size = check.getUnclippedBoundsInRoot()
    assertEquals(54.dp, size.right - size.left)
    assertEquals(54.dp, size.bottom - size.top)
    assertEquals(titleCenter, bounds.center.x, 2f)
    assertTrue((titleTop - bounds.bottom) / bounds.width * 54f in 70f..100f)
    val pixels = check.captureToImage().toPixelMap()
    assertTrue(
        pixels[0, 0] == Color(0xFFD0F5E4) || pixels[0, 0] == Color(0xFF0D493C)
    )
    var greenPixels = 0
    for (row in 0 until pixels.height) {
        for (column in 0 until pixels.width) {
            val color = pixels[column, row]
            if (color == Color(0xFF087F5B) || color == Color(0xFF66D7B4)) greenPixels++
        }
    }
    assertTrue(
        "The check icon must be visibly drawn",
        greenPixels > pixels.width * pixels.height / 30
    )
}
