package com.buddylabs.pinglide.ui.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PendingTargetTestMessageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val targetPane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "我的电脑")
    private val inSheet = hasAnyAncestor(targetPane)
    private val sheetContent = compose.onNode(inSheet and hasScrollAction())
    private val accessibility = textTimeoutAccessibility(RECOMMENDED_TIMEOUT)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsDimsThePendingTestRowAndIgnoresDuplicateTaps() {
        assertPendingTestRow(Configuration.UI_MODE_NIGHT_NO, prototypeStrokeContrast = 40)
    }

    @Test
    fun darkDetailsDimsThePendingTestRowAndIgnoresDuplicateTaps() {
        assertPendingTestRow(Configuration.UI_MODE_NIGHT_YES, prototypeStrokeContrast = 39)
    }

    @Test
    fun androidAccessibilityReportsOneSuccessWithoutAToast() =
        assertAccessibleFeedback(TestMessageResult.Received, SUCCESS_MESSAGE)

    @Test
    fun androidAccessibilityReportsOfflineOnceAndUsesTheRecommendedTimeout() =
        assertAccessibleFeedback(TestMessageResult.Offline, OFFLINE_MESSAGE)

    private fun assertAccessibleFeedback(result: TestMessageResult, message: String) {
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val verifyTalkBackSpeech = InstrumentationRegistry.getArguments()
            .getString("verifyTalkBackSpeech") == "true"
        val delivery = CompletableDeferred<TestMessageOutcome>()
        renderReadyHome(
            compose,
            targets(),
            scope,
            accessibilityManager = accessibility,
            sendTest = { delivery.await() }
        ) { error("Unexpected Pairing removal") }
        compose.onNodeWithText("我的电脑").performClick()
        sheetText("发送测试消息").assertIsDisplayed()
        val originalFlags = automation.serviceInfo.flags
        val feedback = TestMessageFeedbackEvents(message)
        val notice = compose.onAllNodes(hasText(message))
        try {
            val send = if (verifyTalkBackSpeech) focusSendForTalkBack(automation) else null
            automation.setOnAccessibilityEventListener(feedback)
            sheetText("发送测试消息").performClick()
            compose.waitUntil(5_000L) {
                compose.onAllNodes(inSheet and hasText("正在发送…")).fetchSemanticsNodes().isNotEmpty()
            }
            if (verifyTalkBackSpeech) {
                compose.waitUntil(12_000L) { feedback.talkBackSpeech.any { STARTED_SENDING in it } }
            }
            val deliveredAt = compose.runOnIdle {
                SystemClock.elapsedRealtime().also {
                    delivery.complete(TestMessageOutcome("我的电脑", result))
                }
            }
            sheetText(message).assertIsDisplayed()
            compose.waitUntil(3_000L) { feedback.status.isNotEmpty() }
            assertTrue(feedback.status.first().isVisibleToUser)
            if (verifyTalkBackSpeech) {
                assertEquals(send, automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            }
            var dismissedAt: Long? = null
            compose.waitUntil(if (verifyTalkBackSpeech) 12_000L else RECOMMENDED_TIMEOUT + 1_500) {
                if (dismissedAt == null && notice.fetchSemanticsNodes().isEmpty()) {
                    dismissedAt = SystemClock.elapsedRealtime()
                }
                dismissedAt != null &&
                    (!verifyTalkBackSpeech || feedback.talkBackSpeech.any { message in it })
            }
            val duration = checkNotNull(dismissedAt) - deliveredAt
            assertTrue(
                "Notice dismissed after ${duration}ms",
                duration in (RECOMMENDED_TIMEOUT - 500)..(RECOMMENDED_TIMEOUT + 1_500)
            )
            assertTrue(
                "Unexpected explicit announcement or Toast: ${feedback.announcementsOrToasts}",
                feedback.announcementsOrToasts.isEmpty()
            )
            assertEquals("One native live-region update", 1, feedback.status.size)
            if (verifyTalkBackSpeech) {
                assertEquals(send, automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
                assertEquals(
                    "TalkBack speech: ${feedback.talkBackSpeech}",
                    1,
                    feedback.spokenNotices()
                )
            }
        } finally {
            automation.setOnAccessibilityEventListener(null)
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Suppress("DEPRECATION")
    internal class TestMessageFeedbackEvents(
        private val message: String,
        private val duplicatePhrase: String = message,
        private val unexpectedMessage: String? = null
    ) : UiAutomation.OnAccessibilityEventListener {
        val status = ConcurrentLinkedQueue<AccessibilityNodeInfo>()
        val talkBackSpeech = ConcurrentLinkedQueue<String>()
        val announcementsOrToasts = ConcurrentLinkedQueue<Int>()
        val unexpectedEvents = ConcurrentLinkedQueue<Int>()

        override fun onAccessibilityEvent(event: AccessibilityEvent) {
            val unexpected = unexpectedMessage?.let { phrase ->
                event.text.any { phrase in it } ||
                    event.contentDescription?.contains(phrase) == true
            } == true
            if (unexpected) unexpectedEvents.add(event.eventType)
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> recordTextChange(event, unexpected)

                AccessibilityEvent.TYPE_ANNOUNCEMENT,
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    if (event.text.any { duplicatePhrase in it.toString() }) {
                        announcementsOrToasts.add(event.eventType)
                    }
                }
            }
        }

        private fun recordTextChange(event: AccessibilityEvent, unexpectedInEvent: Boolean) {
            val source = event.source ?: return
            val text = source.text?.toString()
            if (!unexpectedInEvent && unexpectedMessage != null &&
                (
                    text?.contains(unexpectedMessage) == true ||
                        source.contentDescription?.contains(unexpectedMessage) == true
                    )
            ) {
                unexpectedEvents.add(event.eventType)
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                source.liveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE &&
                text == message
            ) {
                status.add(source)
            }
            if (text != null && event.packageName == TALKBACK_PACKAGE &&
                event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
            ) {
                talkBackSpeech.add(text)
            }
        }

        fun spokenNotices(): Int =
            talkBackSpeech.sumOf { Regex.fromLiteral(message).findAll(it).count() }
    }

    private fun focusSendForTalkBack(automation: UiAutomation): AccessibilityNodeInfo {
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        automation.serviceInfo = info
        fun sendNode() = automation.windows.firstNotNullOfOrNull { it.root?.findSendAction() }
        compose.waitUntil(5_000L) { sendNode() != null }
        val send = checkNotNull(sendNode())
        if (!send.isAccessibilityFocused) {
            send.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
        compose.waitUntil(3_000L) {
            send == automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        }
        return send
    }

    private fun AccessibilityNodeInfo.findSendAction(): AccessibilityNodeInfo? {
        if (text?.toString()?.contains("发送测试消息") == true ||
            contentDescription?.toString()?.contains("发送测试消息") == true
        ) {
            return if (isClickable) this else parent
        }
        return (0 until childCount).firstNotNullOfOrNull { getChild(it)?.findSendAction() }
    }

    @Test
    fun delayedSuccessStaysWithTheDividerInAShortSheetAtDoubleFontScale() =
        assertDelayedFeedbackInShortSheet(TestMessageResult.Received, SUCCESS_MESSAGE)

    @Test
    fun delayedOfflineFeedbackStaysVisibleAndWrapsAboveThePrototypeLowerEdge() =
        assertDelayedFeedbackInShortSheet(TestMessageResult.Offline, OFFLINE_MESSAGE)

    private fun assertDelayedFeedbackInShortSheet(result: TestMessageResult, message: String) {
        val originalSize = runHomeTestShell("wm size")
        val overrideSize = Regex(
            "Override size: (\\d+x\\d+)"
        ).find(originalSize)?.groupValues?.get(1)
        val originalFont = runHomeTestShell("settings get system font_scale").trim()
        val density = compose.activity.resources.displayMetrics.density
        val delivery = CompletableDeferred<TestMessageOutcome>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val feedback = TestMessageFeedbackEvents(message)
        try {
            runHomeTestShell("settings put system font_scale 2.0")
            runHomeTestShell(
                "wm size ${(366 * density).roundToInt()}x${(260 * density).roundToInt()}"
            )
            compose.waitUntil(5_000) {
                compose.runOnUiThread {
                    val configuration = compose.activity.resources.configuration
                    configuration.screenHeightDp <= 260 && configuration.fontScale == 2f
                }
            }
            renderReadyHome(
                compose,
                targets(),
                scope,
                accessibilityManager = accessibility,
                sendTest = { delivery.await() }
            ) { error("Unexpected Pairing removal") }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("我的电脑"))
            compose.onNodeWithText("我的电脑").performClick()
            val send = sheetText("发送测试消息").performScrollTo()
            val rename = sheetText("重命名设备")
            rename.performScrollTo()
            val textLayouts = mutableListOf<TextLayoutResult>()
            compose.onNode(inSheet and hasText("发送测试消息"), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            assertEquals(2f, textLayouts.single().layoutInput.density.fontScale)
            send.assertIsDisplayed().performClick()
            sheetText("正在发送…").assertIsNotEnabled()
            sheetText("关闭").performScrollTo()
            assertTrue(
                "Send/Rename divider is above the viewport",
                rename.getUnclippedBoundsInRoot().top < sheetContent.getUnclippedBoundsInRoot().top
            )
            automation.setOnAccessibilityEventListener(feedback)
            compose.runOnIdle {
                delivery.complete(TestMessageOutcome("我的电脑", result))
            }
            send.assertIsEnabled()
            if (result == TestMessageResult.Offline) {
                val noticeBounds = sheetText(message).assertIsDisplayed().getUnclippedBoundsInRoot()
                val viewport = sheetContent.getUnclippedBoundsInRoot()
                assertTrue(
                    "Unclipped notice $noticeBounds fits inside $viewport",
                    noticeBounds.left >= viewport.left && noticeBounds.right <= viewport.right &&
                        noticeBounds.top >= viewport.top && noticeBounds.bottom <= viewport.bottom
                )
                sheetText("关闭").assertIsDisplayed()
            } else {
                sheetText(message).assertDoesNotExist()
            }
            compose.waitUntil(3_000L) { feedback.status.isNotEmpty() }
            assertTrue(feedback.status.first().isVisibleToUser)

            send.performScrollTo()
            rename.performScrollTo()
            if (result == TestMessageResult.Offline) {
                val scrollRoom = with(compose.density) { 20.dp.toPx() }
                sheetContent.performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, -scrollRoom)
                }
                val notice = sheetText(message).assertIsDisplayed()
                val textLayouts = mutableListOf<TextLayoutResult>()
                notice.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(textLayouts)
                }
                assertTrue(textLayouts.single().lineCount > 1)
                val expectedBottom =
                    sheetBounds(send).bottom + with(compose.density) { 29.dp.toPx() }
                val noticeBounds = sheetBounds(notice)
                assertTrue("Wrapped notice has room above its anchor", noticeBounds.top > 0f)
                assertEquals(expectedBottom, noticeBounds.bottom, 1f)
            } else {
                assertSuccessFeedback(sheetBounds(send), sheetBounds(rename), rename)
            }
        } finally {
            automation.setOnAccessibilityEventListener(null)
            runHomeTestShell("wm size ${overrideSize ?: "reset"}")
            if (originalFont == "null") {
                runHomeTestShell("settings delete system font_scale")
            } else {
                runHomeTestShell("settings put system font_scale $originalFont")
            }
            compose.waitUntil(5_000) {
                compose.runOnUiThread {
                    compose.activity.resources.configuration.fontScale ==
                        (originalFont.toFloatOrNull() ?: 1f)
                }
            }
        }
    }

    private fun targets(): List<RemotePairing> {
        val now = Instant.now().toString()
        return listOf(
            RemotePairing("home", "receiver-home", "我的电脑", Presence.Online, now),
            RemotePairing("office", "receiver-office", "办公室电脑", Presence.Offline, now)
        )
    }

    private fun assertPendingTestRow(nightMode: Int, prototypeStrokeContrast: Int) {
        val sentTo = mutableListOf<String>()
        val delivery = CompletableDeferred<TestMessageOutcome>()
        renderReadyHome(
            compose,
            targets(),
            scope,
            nightMode = nightMode,
            accessibilityManager = accessibility,
            sendTest = { pairingId ->
                sentTo += pairingId
                delivery.await()
            }
        ) { error("Unexpected Pairing removal") }

        compose.onNodeWithText("我的电脑").performClick()
        val receiveSwitch = compose.onNode(inSheet and hasContentDescription("我的电脑接收短信"))
        val rename = sheetText("重命名设备")
        val send = sheetText("发送测试消息").assertIsDisplayed().assertIsEnabled()
        val idleBounds = sheetBounds(send)
        val idleRenameBounds = sheetBounds(rename)
        val idleDivider = dividerStroke(idleBounds, idleRenameBounds)
        send.performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click()
        }

        val sending = sheetText("正在发送…").assertIsDisplayed().assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        sending.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("home"), sentTo)
        }
        val sendingBounds = sheetBounds(sending)
        val renameBounds = sheetBounds(rename)
        assertEquals("The pending row keeps its bounds within the sheet", idleBounds, sendingBounds)
        assertTrue(
            "The pending row stays between Receive SMS and Rename",
            sheetBounds(receiveSwitch).bottom <= sendingBounds.top &&
                sendingBounds.bottom <= renameBounds.top
        )
        val pendingDivider = dividerStroke(sendingBounds, renameBounds)
        val pendingThickness = with(compose.density) { 1.dp.toPx() }
        assertEquals(
            "The pending divider is 1 dp thick",
            pendingThickness.roundToInt(),
            pendingDivider.size
        )
        val expectedContrast = prototypeStrokeContrast * pendingThickness.coerceAtMost(1f)
        assertEquals(
            "The pending divider matches the prototype stroke contrast",
            expectedContrast,
            pendingDivider.max().toFloat(),
            4f
        )
        compose.runOnIdle {
            delivery.complete(TestMessageOutcome("我的电脑", TestMessageResult.Received))
        }
        send.assertIsEnabled()
        assertEquals(
            "The same divider recovers its initial thickness and contrast",
            idleDivider,
            dividerStroke(sheetBounds(send), sheetBounds(rename))
        )
        assertSuccessFeedback(idleBounds, idleRenameBounds, rename)
    }

    private fun assertSuccessFeedback(
        sendBounds: Rect,
        renameBounds: Rect,
        rename: SemanticsNodeInteraction
    ) {
        compose.onAllNodes(successLabel).assertCountEquals(1)
        val notice = compose.onNode(inSheet and successLabel).assertIsDisplayed()
        val noticeBounds = sheetBounds(notice)
        assertEquals(
            "Feedback is centered over the target actions",
            sendBounds.center.x,
            noticeBounds.center.x,
            1f
        )
        assertTrue(
            "Feedback $noticeBounds overlays the boundary between Send and Rename $renameBounds",
            noticeBounds.top < renameBounds.top && noticeBounds.bottom > renameBounds.top
        )
        assertEquals("Feedback leaves Rename in place", renameBounds, sheetBounds(rename))
    }

    private fun sheetText(label: String) = compose.onNode(inSheet and hasText(label))

    private val successLabel = hasText("测试消息已发送")

    private fun sheetBounds(row: SemanticsNodeInteraction): Rect =
        row.fetchSemanticsNode().boundsInWindow.translate(
            -sheetContent.fetchSemanticsNode().boundsInWindow.topLeft
        )

    private fun dividerStroke(upper: Rect, lower: Rect): List<Int> {
        val image = sheetContent.captureToImage().asAndroidBitmap()
        val columns = with(compose.density) {
            val inset = 6.dp.roundToPx()
            listOf(upper.left.roundToInt() + inset, upper.right.roundToInt() - inset)
        }
        val firstLine = upper.bottom.roundToInt()
        val lastLine = lower.top.roundToInt() - 1
        val background = image.getPixel(columns.first(), lastLine + 1)
        val strokes = (firstLine..lastLine).map { line ->
            val pixel = image.getPixel(columns.first(), line)
            assertEquals(
                "The separator agrees across the row at y=$line",
                pixel,
                image.getPixel(columns.last(), line)
            )
            abs(Color.red(pixel) - Color.red(background)) +
                abs(Color.green(pixel) - Color.green(background)) +
                abs(Color.blue(pixel) - Color.blue(background))
        }
        val stroke = strokes.takeLastWhile { it > 0 }
        assertTrue(
            "A single separator ends the gap between rows",
            stroke.isNotEmpty() && strokes.count { it > 0 } == stroke.size
        )
        return stroke
    }

    private companion object {
        const val STARTED_SENDING = "正在发送"
        const val SUCCESS_MESSAGE = "测试消息已发送"
        const val OFFLINE_MESSAGE = "测试消息未发送：目标设备离线"
        const val RECOMMENDED_TIMEOUT = 8_000L
        const val TALKBACK_PACKAGE = "com.google.android.marvin.talkback"
    }
}
