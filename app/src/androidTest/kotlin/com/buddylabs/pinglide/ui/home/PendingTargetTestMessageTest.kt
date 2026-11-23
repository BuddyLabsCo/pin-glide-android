package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
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
    private val inSheet =
        hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))

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
        sheetText("发送测试消息").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { click ->
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
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val normalDivider = dividerInkAbove(image, sending)
        val pendingDivider = dividerInkAbove(image, sheetText("重命名设备"))
        assertTrue(
            "The pending divider has about 38% of its neighbor's contrast ($pendingDivider / $normalDivider)",
            pendingDivider > 0 &&
                pendingDivider.toDouble() in normalDivider * 0.30..normalDivider * 0.46
        )
        compose.runOnIdle {
            delivery.complete(TestMessageOutcome("我的电脑", TestMessageResult.Received))
        }
        sheetText("发送测试消息").assertIsEnabled()
    }

    private fun sheetText(label: String) = compose.onNode(inSheet and hasText(label))

    private fun dividerInkAbove(image: Bitmap, row: SemanticsNodeInteraction): Int {
        val node = row.fetchSemanticsNode()
        val position = node.layoutInfo.coordinates.positionOnScreen()
        val column =
            position.x.roundToInt() + node.size.width - with(compose.density) { 28.dp.roundToPx() }
        val boundary = position.y.roundToInt()
        val radius = with(compose.density) { 16.dp.roundToPx() }
        val background = image.getPixel(column, boundary + radius)
        return (boundary - radius..boundary + radius).maxOf { line ->
            val pixel = image.getPixel(column, line)
            abs(Color.red(pixel) - Color.red(background)) +
                abs(Color.green(pixel) - Color.green(background)) +
                abs(Color.blue(pixel) - Color.blue(background))
        }
    }
}
