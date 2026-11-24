package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.data.service.TestDeliveryService
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.generated.http.model.UploadRequest
import com.buddylabs.pinglide.ui.home.PendingTargetTestMessageTest.TestMessageFeedbackEvents
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UncertainTargetTestMessageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val inSheet = hasAnyAncestor(
        SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, TARGET)
    )
    private val sheetContent = compose.onNode(inSheet and hasScrollAction())
    private val accessibility = object : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean
        ): Long = if (containsText) NOTICE_TIMEOUT else originalTimeoutMillis
    }

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsShowsUnconfirmedDeliveryOverTheSendDivider() =
        assertUnconfirmedDelivery(Configuration.UI_MODE_NIGHT_NO)

    @Test
    fun darkDetailsShowsUnconfirmedDeliveryOverTheSendDivider() =
        assertUnconfirmedDelivery(Configuration.UI_MODE_NIGHT_YES)

    @Test
    fun wrappedNoticeKeepsTheSheetBottomAnchorWhenTheDividerScrollsOutOfView() =
        withShortHomeViewport(compose) {
            val delivery = CompletableDeferred<TestMessageOutcome>()
            renderReadyHome(
                compose,
                targets(),
                scope,
                sendTest = { delivery.await() },
                accessibilityManager = accessibility
            ) { error("Unexpected Pairing removal") }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(TARGET))
            compose.onNodeWithText(TARGET).performClick()
            val send = sheetText("发送测试消息").performScrollTo().assertIsDisplayed()
            val rename = sheetText("重命名设备")
            send.performClick()
            sheetText("正在发送…").assertIsNotEnabled()
            sheetText("关闭").performScrollTo().assertIsDisplayed()
            assertTrue(
                rename.getUnclippedBoundsInRoot().top < sheetContent.getUnclippedBoundsInRoot().top
            )

            compose.runOnIdle {
                delivery.complete(TestMessageOutcome(TARGET, TestMessageResult.Uncertain))
            }
            awaitNotice(NOTICE)
            val notice = sheetText(NOTICE)
            val layouts = mutableListOf<TextLayoutResult>()
            notice.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().lineCount > 1)
            val bottom = assertSheetAnchor(NOTICE)
            sheetText("关闭").assertIsDisplayed()

            send.performScrollTo().assertIsDisplayed()
            rename.performScrollTo().assertIsDisplayed()
            assertTrue(
                rename.getUnclippedBoundsInRoot().top > sheetContent.getUnclippedBoundsInRoot().top
            )
            assertEquals(bottom, assertSheetAnchor(NOTICE), 1f)
        }

    @Test
    fun mismatchedDeliveryReceiptShowsTheEnglishUnconfirmedNotice() =
        assertEnglishReceiptFeedback(matchesTarget = false, ENGLISH_NOTICE, ENGLISH_SUCCESS)

    @Test
    fun matchingDeliveryReceiptShowsTheEnglishSuccessNotice() =
        assertEnglishReceiptFeedback(matchesTarget = true, ENGLISH_SUCCESS, ENGLISH_NOTICE)

    private fun assertEnglishReceiptFeedback(
        matchesTarget: Boolean,
        expectedNotice: String,
        unexpectedNotice: String
    ) {
        val uploads = ConcurrentLinkedQueue<UploadRequest>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.method == "POST" && request.url.encodedPath == "/v1/messages")
            val json = Buffer().also { checkNotNull(request.body).writeTo(it) }.readUtf8()
            val upload = Json.decodeFromString<UploadRequest>(json)
            uploads.add(upload)
            val receiverId = if (matchesTarget) {
                upload.items.single().receiverId
            } else {
                "receiver-office"
            }
            val response = """
                {"requestId":"request","messageId":"${upload.messageId}","results":[
                  {"index":0,"receiverId":"$receiverId","status":"received"}
                ]}
            """.trimIndent()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(207)
                .message("Multi-Status")
                .body(response.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        renderReadyHome(
            compose,
            targets(),
            scope,
            language = AppLanguage.English,
            accessibilityManager = accessibility,
            testDelivery = TestDeliveryService("https://delivery.test", client)
        ) { error("Unexpected Pairing removal") }
        compose.onNodeWithText(TARGET).performClick()
        assertFeedback(expectedNotice, unexpectedNotice) {
            sheetText("Send test message").performClick()
            awaitNotice(expectedNotice)
            if (!matchesTarget) assertSheetAnchor(expectedNotice)
            sheetText(TARGET).assertIsDisplayed()
        }
        val item = uploads.single().items.single()
        assertEquals("home", item.pairingId)
        assertEquals("receiver-home", item.receiverId)
    }

    private fun assertUnconfirmedDelivery(nightMode: Int) {
        val delivery = CompletableDeferred<TestMessageOutcome>()
        val sentTo = mutableListOf<String>()
        renderReadyHome(
            compose,
            targets(),
            scope,
            nightMode = nightMode,
            accessibilityManager = accessibility,
            sendTest = {
                sentTo += it
                delivery.await()
            }
        ) { error("Unexpected Pairing removal") }
        compose.onNodeWithText(TARGET).performClick()
        val send = sheetText("发送测试消息").assertIsDisplayed()
        val rename = sheetText("重命名设备").assertIsDisplayed()
        val sendBounds = send.fetchSemanticsNode().boundsInWindow
        val renameBounds = rename.fetchSemanticsNode().boundsInWindow
        assertFeedback(NOTICE, "测试消息已发送") {
            send.performClick()
            sheetText("正在发送…").assertIsNotEnabled()
            compose.runOnIdle {
                delivery.complete(TestMessageOutcome(TARGET, TestMessageResult.Uncertain))
            }
            awaitNotice(NOTICE)
            val notice = sheetText(NOTICE).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertEquals(sendBounds.center.x, notice.center.x, 1f)
            assertTrue(notice.top < sendBounds.bottom && notice.bottom > renameBounds.top)
            assertEquals(renameBounds, rename.fetchSemanticsNode().boundsInWindow)
            assertSheetAnchor(NOTICE)
            sheetText(TARGET).assertIsDisplayed()
            send.assertIsEnabled()
            compose.runOnIdle { assertEquals(listOf("home"), sentTo) }
        }
    }

    private fun assertFeedback(
        message: String,
        unexpectedMessage: String,
        sendAndCheck: () -> Unit
    ) {
        val feedback = TestMessageFeedbackEvents(message)
        val unexpectedFeedback = TestMessageFeedbackEvents(unexpectedMessage)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setOnAccessibilityEventListener {
            feedback.onAccessibilityEvent(it)
            unexpectedFeedback.onAccessibilityEvent(it)
        }
        try {
            sendAndCheck()
            sheetText(unexpectedMessage).assertDoesNotExist()
            compose.waitUntil(3_000L) { feedback.status.isNotEmpty() }
            compose.waitUntil(NOTICE_TIMEOUT + 2_000L) {
                compose.onAllNodes(inSheet and hasText(message)).fetchSemanticsNodes().isEmpty()
            }
            automation.waitForIdle(200, 3_000)
            val update = feedback.status.single()
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, update.liveRegion)
            assertTrue(update.isVisibleToUser)
            assertTrue(feedback.announcementsOrToasts.isEmpty())
            assertTrue(unexpectedFeedback.status.isEmpty())
            assertTrue(unexpectedFeedback.announcementsOrToasts.isEmpty())
        } finally {
            automation.setOnAccessibilityEventListener(null)
        }
    }

    private fun awaitNotice(message: String) {
        compose.waitUntil(3_000L) {
            compose.onAllNodes(inSheet and hasText(message)).fetchSemanticsNodes().isNotEmpty()
        }
        sheetText(message).assertIsDisplayed()
    }

    private fun assertSheetAnchor(message: String): Float {
        val notice = sheetText(message).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = sheetContent.getUnclippedBoundsInRoot()
        assertTrue(notice.left >= viewport.left && notice.right <= viewport.right)
        assertTrue(notice.top >= viewport.top && notice.bottom <= viewport.bottom)
        val bottomOffset = (viewport.bottom - notice.bottom).value
        val availableOffset = (viewport.bottom - viewport.top) - (notice.bottom - notice.top)
        assertEquals(minOf(164f, availableOffset.value), bottomOffset, 1f)
        return bottomOffset
    }

    private fun targets(): List<RemotePairing> {
        val now = Instant.now().toString()
        return listOf(
            RemotePairing("home", "receiver-home", TARGET, Presence.Online, now),
            RemotePairing("office", "receiver-office", "办公室电脑", Presence.Offline, now)
        )
    }

    private fun sheetText(text: String) = compose.onNode(inSheet and hasText(text))

    private companion object {
        const val TARGET = "我的电脑"
        const val NOTICE = "投递未确认：无法获取投递结果"
        const val ENGLISH_NOTICE = "Delivery unconfirmed: unable to retrieve the delivery result"
        const val ENGLISH_SUCCESS = "Test message sent"
        const val NOTICE_TIMEOUT = 8_000L
    }
}
