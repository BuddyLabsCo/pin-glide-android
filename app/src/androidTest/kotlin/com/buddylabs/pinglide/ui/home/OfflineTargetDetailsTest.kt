package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.domain.settings.AppLanguage
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

class OfflineTargetDetailsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val inSheet =
        hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
    private val lightTint = 0xFF52635E.toInt()
    private val lightSurface = 0xFFEEF6F2.toInt()

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsSendsOnlyToTheSelectedOfflinePairing() {
        val sentTo = mutableListOf<String>()
        val delivery = CompletableDeferred<TestMessageOutcome>()
        openHome(Configuration.UI_MODE_NIGHT_NO) { pairingId ->
            sentTo += pairingId
            delivery.await()
        }

        compose.onNode(hasText("我的电脑")).performClick()
        sheetText("我的电脑").assertIsDisplayed()
        compose.onNode(inSheet and hasText("在线 · 状态变化：", substring = true))
            .assertIsDisplayed()
        assertPlainTrailingSlot(availableSendAction())
        sheetText("关闭").performClick()

        val send = openOfflineDetails()
        assertChevron(send, lightTint, lightSurface)
        assertOfflineDividers(lightSurface)
        send.performClick()

        val sending = sheetText("正在发送…").assertIsDisplayed().assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(500)
        assertPlainTrailingSlot(sending)
        compose.runOnIdle {
            assertEquals(listOf("office"), sentTo)
            delivery.complete(TestMessageOutcome("办公室电脑", TestMessageResult.Offline))
        }
        sheetText("办公室电脑：目标设备离线，测试消息未送达").assertIsDisplayed()
        sheetText("发送测试消息").assertIsEnabled()
    }

    @Test
    fun darkDetailsShowsOfflineStatusAndTestAction() {
        openHome(Configuration.UI_MODE_NIGHT_YES)
        val surface = 0xFF11221F.toInt()
        assertChevron(openOfflineDetails(), 0xFFA7BBB5.toInt(), surface)
        assertOfflineDividers(surface)
    }

    @Test
    fun turningOffAutomaticReceiveKeepsTheTestChevronAndActionAvailable() {
        openHome(Configuration.UI_MODE_NIGHT_NO)
        compose.onNode(hasText("我的电脑")).performClick()
        receiveSwitch("我的电脑").assertIsEnabled().performClick().assertIsOff()
        assertChevron(availableSendAction(), lightTint, lightSurface)
    }

    @Test
    fun unusableKeyDimsTheTestChevron() {
        openHome(Configuration.UI_MODE_NIGHT_NO, missingKeyId = "home")
        compose.onNode(hasText("我的电脑")).performClick()
        compose.onNode(inSheet and hasText("密钥丢失或损坏", substring = true))
            .assertIsDisplayed()
        receiveSwitch("我的电脑").assertIsNotEnabled()
        val send = sheetText("发送测试消息").assertIsDisplayed().assertIsNotEnabled()
        assertChevron(send, lightTint, lightSurface, opacity = 0.38f)
    }

    @Test
    fun savingARenameShowsTheTestChevron() {
        openHome(Configuration.UI_MODE_NIGHT_NO)
        compose.onNode(hasText("我的电脑")).performClick()
        sheetText("重命名设备").performClick()
        compose.onNode(inSheet and hasSetTextAction()).performTextReplacement("家用设备")
        sheetText("保存").assertIsEnabled().performClick()
        compose.onNode(inSheet and hasText("家用设备") and hasStateDescription("设备名称已更新"))
            .assertIsDisplayed()
        sheetText("家用设备").assertIsDisplayed()
        receiveSwitch("家用设备").assertIsOn()
        assertChevron(availableSendAction(), lightTint, lightSurface)
    }

    @Test
    fun offlinePresenceFormatsElapsedMinutesInSimplifiedChinese() {
        val changedAt = Instant.parse("2026-01-01T00:00:00Z")
        val target = TargetDevice(
            pairingId = "office",
            alias = "办公室电脑",
            enabled = true,
            keyUsable = true,
            presence = Presence.Offline,
            stateChangedAt = changedAt.toString()
        )
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                Text(targetPresence(target, changedAt.plusSeconds(125)))
            }
        }
        compose.onNodeWithText("离线 · 状态变化：2分钟前").assertIsDisplayed()
    }

    private fun openHome(
        nightMode: Int,
        missingKeyId: String? = null,
        sendTest: suspend (String) -> TestMessageOutcome? = { error("Unexpected test message") }
    ) {
        val now = Instant.now()
        val targets = listOf(
            RemotePairing("home", "receiver-home", "我的电脑", Presence.Online, now.toString()),
            RemotePairing(
                "office",
                "receiver-office",
                "办公室电脑",
                Presence.Offline,
                now.minusSeconds(125).toString()
            )
        )
        renderReadyHome(
            compose,
            targets,
            scope,
            nightMode = nightMode,
            onRename = { _, _ -> },
            sendTest = sendTest,
            missingKeyId = missingKeyId
        ) {
            error("Unexpected Pairing removal")
        }
    }

    private fun openOfflineDetails(): SemanticsNodeInteraction {
        val alias = hasText("办公室电脑")
        compose.onNode(alias).performClick()
        compose.onNode(inSheet and alias).assertIsDisplayed()
        val changedMinutesAgo = Regex("离线 · 状态变化：[0-9]+分钟前")
        val offlineTime = SemanticsMatcher("offline status with a localized change time") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                .any { it.text.matches(changedMinutesAgo) }
        }
        compose.onNode(inSheet and offlineTime).assertIsDisplayed()
        receiveSwitch("办公室电脑").assertIsDisplayed().assertIsEnabled().assertIsOn()
        return availableSendAction()
    }

    private fun sheetText(label: String) = compose.onNode(inSheet and hasText(label))

    private fun receiveSwitch(alias: String) =
        compose.onNode(inSheet and hasContentDescription("${alias}接收短信"))

    private fun availableSendAction() = sheetText("发送测试消息")
        .assertIsDisplayed().assertIsEnabled().assertHasClickAction()

    private fun assertChevron(
        row: SemanticsNodeInteraction,
        tint: Int,
        background: Int,
        opacity: Float = 1f
    ) {
        val slot = trailingSlot(row)
        val expected = Bitmap.createBitmap(slot.width, slot.height, Bitmap.Config.ARGB_8888)
        expected.eraseColor(background)
        val chevron = checkNotNull(compose.activity.getDrawable(R.drawable.ic_chevron)).mutate()
        chevron.setTint(tint)
        chevron.alpha = (255 * opacity).toInt()
        chevron.setBounds(0, 0, slot.width, slot.height)
        chevron.draw(Canvas(expected))

        assertEquals(background, slot.getPixel(0, 0))
        val reference = pixels(expected)
        val actual = pixels(slot)
        val referenceInk = reference.map { colorDistance(it, background) }
        val actualInk = actual.map { colorDistance(it, background) }
        val maskError = referenceInk.indices.sumOf { abs(referenceInk[it] - actualInk[it]) }
        assertTrue(
            "The trailing artwork matches the chevron mask",
            maskError < referenceInk.sum() / 4
        )
        val referenceStroke = reference.maxBy { colorDistance(it, background) }
        val actualStroke = actual.maxBy { colorDistance(it, background) }
        assertTrue(
            "The chevron uses the expected theme tint",
            colorDistance(actualStroke, referenceStroke) <= 24
        )
    }

    private fun assertOfflineDividers(background: Int) {
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        with(compose.density) {
            val thickness = 1.dp.roundToPx()
            for (label in listOf("发送测试消息", "重命名设备", "移除这台设备的配对")) {
                val row = sheetText(label).fetchSemanticsNode()
                val position = row.layoutInfo.coordinates.positionOnScreen()
                val column = position.x.roundToInt() + row.size.width - 28.dp.roundToPx()
                val boundary = position.y.roundToInt()
                val searchRadius = 16.dp.roundToPx()
                val dividerLines = (boundary - searchRadius until boundary + searchRadius)
                    .filter { line ->
                        val pixel = image.getPixel(column, line)
                        image.getPixel(image.width / 2, line) == pixel &&
                            ColorUtils.calculateContrast(pixel, background) >= 1.2
                    }
                assertEquals("Visible divider pixels above $label", thickness, dividerLines.size)
                val firstLine = dividerLines.first()
                assertEquals(background, image.getPixel(column, firstLine - 1))
                assertEquals(background, image.getPixel(column, firstLine + thickness))
            }
        }
    }

    private fun colorDistance(first: Int, second: Int) = abs(Color.red(first) - Color.red(second)) +
        abs(Color.green(first) - Color.green(second)) +
        abs(Color.blue(first) - Color.blue(second))

    private fun assertPlainTrailingSlot(row: SemanticsNodeInteraction) {
        val slot = trailingSlot(row)
        val background = slot.getPixel(slot.width - 1, slot.height / 2)
        assertTrue(
            "The trailing slot has no visible icon",
            pixels(slot).all { ColorUtils.calculateContrast(it, background) < 1.12 }
        )
    }

    private fun trailingSlot(row: SemanticsNodeInteraction): Bitmap {
        val image = row.captureToImage().asAndroidBitmap()
        val inset = with(compose.density) { 14.dp.roundToPx() }
        val iconSize = with(compose.density) { 20.dp.roundToPx() }
        return Bitmap.createBitmap(
            image,
            image.width - inset - iconSize,
            (image.height - iconSize) / 2,
            iconSize,
            iconSize
        )
    }

    private fun pixels(image: Bitmap) = IntArray(image.width * image.height).also {
        image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
    }
}
