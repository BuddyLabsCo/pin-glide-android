package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.domain.settings.AppLanguage
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
        assertPlainTrailingSlot(sheetText("发送测试消息"))
        sheetText("关闭").performClick()

        val send = openOfflineDetails()
        verifyOfflineArtwork(send, 0xFF52635E.toInt(), 0xFFEEF6F2.toInt())
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
        verifyOfflineArtwork(openOfflineDetails(), 0xFFA7BBB5.toInt(), 0xFF11221F.toInt())
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
        renderReadyHome(compose, targets, scope, nightMode = nightMode, sendTest = sendTest) {
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
        compose.onNode(inSheet and hasContentDescription("办公室电脑接收短信"))
            .assertIsDisplayed().assertIsEnabled().assertIsOn()
        return sheetText("发送测试消息")
            .assertIsDisplayed().assertIsEnabled().assertHasClickAction()
    }

    private fun sheetText(label: String) = compose.onNode(inSheet and hasText(label))

    private fun verifyOfflineArtwork(send: SemanticsNodeInteraction, tint: Int, background: Int) {
        val rename = sheetText("重命名设备").assertIsDisplayed()
        val remove = sheetText("移除这台设备的配对").assertIsDisplayed()
        val sendSlot = trailingSlot(send)
        val renameSlot = trailingSlot(rename)
        assertArrayEquals(
            "Send and Rename use the same chevron",
            pixels(renameSlot),
            pixels(sendSlot)
        )
        with(compose.density) {
            val center = sendSlot.width / 2
            val armOffset = 2.dp.roundToPx()
            val armHeight = 3.dp.roundToPx()
            assertEquals(tint, sendSlot.getPixel(center - armOffset, center - armHeight))
            assertEquals(tint, sendSlot.getPixel(center + 1.dp.roundToPx(), center))
            assertEquals(tint, sendSlot.getPixel(center - armOffset, center + armHeight))
            assertEquals(background, sendSlot.getPixel(center - armOffset, center))
        }
        val bounds = listOf(send, rename, remove).map { it.getUnclippedBoundsInRoot() }
        with(compose.density) {
            bounds.zipWithNext().forEach { (above, below) ->
                assertEquals(1.dp.roundToPx(), (below.top - above.bottom).roundToPx())
            }
        }
    }

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
