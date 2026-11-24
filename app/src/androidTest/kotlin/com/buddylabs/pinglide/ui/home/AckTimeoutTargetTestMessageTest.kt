package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.data.service.TestDeliveryService
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.generated.http.model.UploadRequest
import com.buddylabs.pinglide.ui.home.PendingTargetTestMessageTest.TestMessageFeedbackEvents
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AckTimeoutTargetTestMessageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val inSheet = hasAnyAncestor(
        SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, TARGET)
    )
    private val uploads = ConcurrentLinkedQueue<UploadRequest>()

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun acknowledgmentTimeoutOverlaysIntactDetailsAndReportsUnconfirmedDelivery() {
        val now = Instant.now().toString()
        renderReadyHome(
            compose,
            listOf(
                RemotePairing("home", "receiver-home", TARGET, Presence.Online, now),
                RemotePairing("office", "receiver-office", "办公室电脑", Presence.Offline, now)
            ),
            scope,
            accessibilityManager = textTimeoutAccessibility(RECOMMENDED_TIMEOUT),
            testDelivery = TestDeliveryService("https://delivery.test", timeoutUpload())
        ) { error("Unexpected Pairing removal") }

        compose.onNodeWithText(TARGET).performClick()
        val send = sheetText("发送测试消息").assertIsDisplayed()
        val rename = sheetText("重命名设备")
        val sendBounds = send.fetchSemanticsNode().boundsInWindow
        val renameBounds = rename.fetchSemanticsNode().boundsInWindow
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val feedback = TestMessageFeedbackEvents(UNCONFIRMED, unexpectedMessage = SUCCESS)
        val notices = compose.onAllNodes(inSheet and hasText(UNCONFIRMED))
        val success = compose.onAllNodes(hasText(SUCCESS, substring = true), useUnmergedTree = true)
        automation.setOnAccessibilityEventListener(feedback)
        try {
            send.performClick()
            compose.waitUntil(3_000L) {
                feedback.status.isNotEmpty() || notices.fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodes(inSheet and hasText("$TARGET：$UNCONFIRMED"))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            notices.assertCountEquals(1)
            val notice = sheetText(UNCONFIRMED).assertIsDisplayed()
            val bounds = notice.fetchSemanticsNode().boundsInWindow
            assertEquals(sendBounds.center.x, bounds.center.x, 1f)
            val expectedBottom = sendBounds.bottom + with(compose.density) { 29.dp.toPx() }
            assertEquals(expectedBottom, bounds.bottom, 2f)
            assertTrue(bounds.top < renameBounds.top && bounds.bottom > renameBounds.top)
            assertEquals(renameBounds, rename.fetchSemanticsNode().boundsInWindow)
            send.assertIsEnabled()
            success.assertCountEquals(0)
            compose.waitUntil(3_000L) { feedback.status.isNotEmpty() }
            compose.waitUntil(RECOMMENDED_TIMEOUT + 2_000L) {
                notices.fetchSemanticsNodes().isEmpty()
            }
            automation.waitForIdle(100L, 2_000L)
            assertTrue(feedback.status.single().isVisibleToUser)
            assertTrue(feedback.announcementsOrToasts.isEmpty())
            assertTrue(
                "Unexpected success events: ${feedback.unexpectedEvents}",
                feedback.unexpectedEvents.isEmpty()
            )
            success.assertCountEquals(0)
            sheetText(TARGET).assertIsDisplayed()
            sheetText("接收短信").assertIsDisplayed()
            sheetText("移除这台设备的配对").assertIsDisplayed()
            assertEquals("home", uploads.single().items.single().pairingId)
        } finally {
            automation.setOnAccessibilityEventListener(null)
        }
    }

    @Test
    fun overlayExpiresAtTheExactRecommendedTimeout() = assertNoticeExpiresAtRecommendedTimeout(
        compose,
        TestMessageOutcome(TARGET, TestMessageResult.AckTimeout),
        UNCONFIRMED,
        RECOMMENDED_TIMEOUT
    )

    private fun timeoutUpload() = testMessageUploadClient { upload ->
        uploads.add(upload)
        """
            {"requestId":"request","messageId":"${upload.messageId}","results":[
              {"index":0,"receiverId":"receiver-home","status":"ack_timeout"}
            ]}
        """.trimIndent()
    }

    private fun sheetText(text: String) = compose.onNode(inSheet and hasText(text))

    private companion object {
        const val TARGET = "我的电脑"
        const val UNCONFIRMED = "投递未确认：等待设备确认超时"
        const val SUCCESS = "测试消息已发送"
        const val RECOMMENDED_TIMEOUT = 8_000L
    }
}
