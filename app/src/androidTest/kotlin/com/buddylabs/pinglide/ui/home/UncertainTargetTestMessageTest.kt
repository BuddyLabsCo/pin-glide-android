package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.forwarding.TestMessageResult
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.ui.home.PendingTargetTestMessageTest.TestMessageFeedbackEvents
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsShowsUnconfirmedDeliveryOverTheSendDivider() =
        assertUnconfirmedDelivery(Configuration.UI_MODE_NIGHT_NO)

    @Test
    fun darkDetailsShowsUnconfirmedDeliveryOverTheSendDivider() =
        assertUnconfirmedDelivery(Configuration.UI_MODE_NIGHT_YES)

    private fun assertUnconfirmedDelivery(nightMode: Int) {
        val delivery = CompletableDeferred<TestMessageOutcome>()
        val sentTo = mutableListOf<String>()
        val now = Instant.now().toString()
        renderReadyHome(
            compose,
            listOf(
                RemotePairing("home", "receiver-home", TARGET, Presence.Online, now),
                RemotePairing("office", "receiver-office", "办公室电脑", Presence.Offline, now)
            ),
            scope,
            nightMode = nightMode,
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
        val feedback = TestMessageFeedbackEvents(NOTICE)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setOnAccessibilityEventListener(feedback)
        try {
            send.performClick()
            sheetText("正在发送…").assertIsNotEnabled()
            compose.runOnIdle {
                delivery.complete(TestMessageOutcome(TARGET, TestMessageResult.Uncertain))
            }
            compose.waitUntil(3_000L) {
                compose.onAllNodes(inSheet and hasText(NOTICE)).fetchSemanticsNodes().isNotEmpty()
            }
            val notice = sheetText(NOTICE).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
            assertEquals(sendBounds.center.x, notice.center.x, 1f)
            assertTrue(notice.top < sendBounds.bottom && notice.bottom > renameBounds.top)
            assertEquals(renameBounds, rename.fetchSemanticsNode().boundsInWindow)
            sheetText(TARGET).assertIsDisplayed()
            send.assertIsEnabled()
            sheetText("测试消息已发送").assertDoesNotExist()
            compose.waitUntil(3_000L) { feedback.status.isNotEmpty() }
            assertTrue(feedback.status.single().isVisibleToUser)
            assertTrue(feedback.announcementsOrToasts.isEmpty())
            compose.runOnIdle { assertEquals(listOf("home"), sentTo) }
        } finally {
            automation.setOnAccessibilityEventListener(null)
        }
    }

    private fun sheetText(text: String) = compose.onNode(inSheet and hasText(text))

    private companion object {
        const val TARGET = "我的电脑"
        const val NOTICE = "投递未确认：无法获取投递结果"
    }
}
