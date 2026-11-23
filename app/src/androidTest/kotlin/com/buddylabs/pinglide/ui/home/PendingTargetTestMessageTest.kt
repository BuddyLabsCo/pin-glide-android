package com.buddylabs.pinglide.ui.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.res.Configuration
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.AccessibilityManager
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
    private val accessibility = object : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean
        ): Long = if (containsText) SUCCESS_TIMEOUT else originalTimeoutMillis
    }

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
    @Suppress("DEPRECATION")
    fun androidAccessibilityReportsOneSuccessWithoutAToast() {
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
        val initialInfo = automation.serviceInfo
        val send = if (verifyTalkBackSpeech) focusSendForTalkBack(automation) else null
        val successEvents = ConcurrentLinkedQueue<Int>()
        automation.setOnAccessibilityEventListener { event ->
            if ((
                    event.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT ||
                        event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                    ) &&
                event.text.any { it.toString() == "测试消息已发送" }
            ) {
                successEvents.add(event.eventType)
            }
        }
        try {
            sheetText("发送测试消息").performClick()
            compose.waitUntil(5_000L) {
                compose.onAllNodes(inSheet and hasText("正在发送…")).fetchSemanticsNodes().isNotEmpty()
            }
            if (verifyTalkBackSpeech) waitForTalkBackSpeech(STARTED_SENDING)
            compose.runOnIdle {
                delivery.complete(TestMessageOutcome("我的电脑", TestMessageResult.Received))
            }
            compose.onNode(inSheet and successLabel).assertIsDisplayed()
            compose.waitUntil(3_000L) { successEvents.isNotEmpty() }
            if (verifyTalkBackSpeech) {
                assertEquals(send, automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
                waitForTalkBackSpeech(SUCCESS_MESSAGE)
                assertEquals(send, automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            }
            compose.waitUntil(7_000L) {
                compose.onAllNodes(successLabel).fetchSemanticsNodes().isEmpty()
            }
            assertEquals(
                listOf(AccessibilityEvent.TYPE_ANNOUNCEMENT),
                successEvents.toList()
            )
        } finally {
            automation.setOnAccessibilityEventListener(null)
            automation.serviceInfo = initialInfo
        }
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

    private fun waitForTalkBackSpeech(phrase: String) {
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        compose.waitUntil(12_000L) {
            automation.windows.any { window ->
                window.root?.let { root ->
                    root.packageName == "com.google.android.marvin.talkback" &&
                        root.findAccessibilityNodeInfosByText(phrase).isNotEmpty()
                } == true
            }
        }
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
    fun successFollowsTheDividerInAShortSheetWithLargeText() {
        val originalSize = shell("wm size")
        val overrideSize = Regex(
            "Override size: (\\d+x\\d+)"
        ).find(originalSize)?.groupValues?.get(1)
        val density = compose.activity.resources.displayMetrics.density
        try {
            shell("wm size ${(366 * density).roundToInt()}x${(320 * density).roundToInt()}")
            compose.waitUntil(5_000) {
                compose.activity.resources.configuration.screenHeightDp <=
                    320
            }
            renderReadyHome(
                compose,
                targets(),
                scope,
                fontScale = 2f,
                accessibilityManager = accessibility,
                sendTest = { TestMessageOutcome("我的电脑", TestMessageResult.Received) }
            ) { error("Unexpected Pairing removal") }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("我的电脑"))
            compose.onNodeWithText("我的电脑").performClick()
            val send = sheetText("发送测试消息").performScrollTo()
            val rename = sheetText("重命名设备")
            rename.performScrollTo()
            send.assertIsDisplayed().performClick()

            val success = compose.onNode(inSheet and successLabel).assertIsDisplayed()
            assertSuccessFeedback(sheetBounds(send), sheetBounds(rename), rename)
            val before = success.getUnclippedBoundsInRoot().top
            sheetText("关闭").performScrollTo()
            val after = success.assertIsDisplayed().getUnclippedBoundsInRoot()
            val viewport = sheetContent.getUnclippedBoundsInRoot()
            assertTrue("Success follows the divider upward", after.top < before)
            assertTrue(
                "Success stays in the viewport",
                after.top >= viewport.top && after.bottom <= viewport.bottom
            )
        } finally {
            shell("wm size ${overrideSize ?: "reset"}")
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

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
        val completedAt = compose.runOnIdle {
            delivery.complete(TestMessageOutcome("我的电脑", TestMessageResult.Received))
            SystemClock.uptimeMillis()
        }
        send.assertIsEnabled()
        assertEquals(
            "The same divider recovers its initial thickness and contrast",
            idleDivider,
            dividerStroke(sheetBounds(send), sheetBounds(rename))
        )
        assertSuccessFeedback(idleBounds, idleRenameBounds, rename)
        assertSuccessTimeout(completedAt)
    }

    private fun assertSuccessFeedback(
        sendBounds: Rect,
        renameBounds: Rect,
        rename: SemanticsNodeInteraction
    ) {
        compose.onAllNodes(successLabel).assertCountEquals(1)
        val success = compose.onNode(inSheet and successLabel).assertIsDisplayed()
        val successBounds = sheetBounds(success)
        assertEquals(
            "Success is centered over the target actions",
            sendBounds.center.x,
            successBounds.center.x,
            1f
        )
        assertTrue(
            "Success $successBounds overlays the boundary between Send and Rename $renameBounds",
            successBounds.top < renameBounds.top && successBounds.bottom > renameBounds.top
        )
        assertEquals("Success leaves Rename in place", renameBounds, sheetBounds(rename))
    }

    private fun assertSuccessTimeout(completedAt: Long) {
        val success = compose.onNode(inSheet and successLabel)
        compose.waitUntil(timeoutMillis = SUCCESS_TIMEOUT) {
            SystemClock.uptimeMillis() - completedAt >= SUCCESS_TIMEOUT - 1_000L
        }
        success.assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(successLabel).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(SystemClock.uptimeMillis() - completedAt >= SUCCESS_TIMEOUT)
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
        const val SUCCESS_TIMEOUT = 5_000L
    }
}
