package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import java.time.Instant
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

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsDimsThePendingTestRowAndIgnoresDuplicateTaps() {
        assertPendingTestRow(Configuration.UI_MODE_NIGHT_NO)
    }

    @Test
    fun darkDetailsDimsThePendingTestRowAndIgnoresDuplicateTaps() {
        assertPendingTestRow(Configuration.UI_MODE_NIGHT_YES)
    }

    private fun assertPendingTestRow(nightMode: Int) {
        val sentTo = mutableListOf<String>()
        val delivery = CompletableDeferred<TestMessageOutcome>()
        val now = Instant.now().toString()
        val targets = listOf(
            RemotePairing("home", "receiver-home", "我的电脑", Presence.Online, now),
            RemotePairing("office", "receiver-office", "办公室电脑", Presence.Offline, now)
        )
        renderReadyHome(
            compose,
            targets,
            scope,
            nightMode = nightMode,
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
        val idleDivider = dividerStroke(idleBounds, sheetBounds(rename))
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
        assertEquals(
            "The pending divider is 1 dp thick",
            with(compose.density) { 1.dp.roundToPx() },
            pendingDivider.size
        )
        val idleInk = idleDivider.max()
        val pendingInk = pendingDivider.max()
        assertTrue(
            "The pending divider has about 38% of its idle contrast ($pendingInk / $idleInk)",
            pendingInk.toDouble() in idleInk * 0.30..idleInk * 0.46
        )
        compose.runOnIdle {
            delivery.complete(TestMessageOutcome("我的电脑", TestMessageResult.Received))
        }
        send.assertIsEnabled()
        sheetText("测试消息已发送").assertIsDisplayed()
        assertEquals(
            "The same divider recovers its initial thickness and contrast",
            idleDivider,
            dividerStroke(sheetBounds(send), sheetBounds(rename))
        )
    }

    private fun sheetText(label: String) = compose.onNode(inSheet and hasText(label))

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
}
