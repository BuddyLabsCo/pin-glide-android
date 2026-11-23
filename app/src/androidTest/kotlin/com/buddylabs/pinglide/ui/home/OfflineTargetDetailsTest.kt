package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OfflineTargetDetailsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsShowsOfflineStatusAndTestAction() {
        verifyOfflineDetails(Configuration.UI_MODE_NIGHT_NO)
    }

    @Test
    fun darkDetailsShowsOfflineStatusAndTestAction() {
        verifyOfflineDetails(Configuration.UI_MODE_NIGHT_YES)
    }

    private fun verifyOfflineDetails(nightMode: Int) {
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
        renderReadyHome(compose, targets, scope, nightMode = nightMode) {
            error("Unexpected Pairing removal")
        }

        val alias = hasText("办公室电脑")
        compose.onNode(alias).performClick()
        val inSheet = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        compose.onNode(inSheet and alias).assertIsDisplayed()
        compose.onNode(inSheet and hasText("离线 · 状态变化：2分钟前")).assertIsDisplayed()
        compose.onNode(inSheet and hasContentDescription("办公室电脑接收短信"))
            .assertIsDisplayed().assertIsEnabled().assertIsOn()
        val testAction = compose.onNode(inSheet and hasText("发送测试消息"))
            .assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        assertTrailingChevron(testAction)
    }

    private fun assertTrailingChevron(row: SemanticsNodeInteraction) {
        val image = row.captureToImage().asAndroidBitmap()
        val center = image.height / 2
        val inset = with(compose.density) { 14.dp.roundToPx() }
        val iconSize = with(compose.density) { 20.dp.roundToPx() }
        val top = center - iconSize / 2
        val right = image.width - inset
        val background = image.getPixel(right - 1, center)
        assertTrue(
            "The test message row displays a trailing chevron",
            (right - iconSize until right).any { column ->
                (top until top + iconSize).any { line ->
                    image.getPixel(column, line) != background
                }
            }
        )
    }
}
