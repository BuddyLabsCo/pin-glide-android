package com.buddylabs.pinglide.ui.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.domain.identity.RevocationReply
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withSystemFontScale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SenderRevocationUnconfirmedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun unconfirmedShowsCenteredExplanationAndRetryFeedback() {
        val retryResponse = CompletableDeferred<RevocationReply>()
        var submitted = false
        var proofRequests = 0
        compose.withSystemFontScale(1f) {
            compose.renderRevocationInventory(
                scope,
                AppLanguage.SimplifiedChinese,
                authorization = {
                    proofRequests++
                    "fresh-proof"
                },
                service = { _, _, _ ->
                    if (submitted) {
                        retryResponse.await()
                    } else {
                        submitted = true
                        RevocationReply.Unconfirmed
                    }
                }
            )
            compose.onNodeWithContentDescription("撤销Pixel 9").performScrollTo().performClick()
            compose.onNodeWithText("确认，验证 QQ").performClick()
            compose.onNodeWithText("使用 QQ 验证并撤销").performClick()
            val heading = compose.onNodeWithText("撤销未确认").assertIsDisplayed()
            heading.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithText(
                "服务器未返回预期结果，暂时无法确认「Pixel 9」的撤销结果。请稍后重试。"
            ).assertIsDisplayed()
            val retry = compose.onNodeWithText("重试撤销").assertIsDisplayed().assertIsEnabled()
            val leave = compose.onNodeWithText("返回设备访问管理").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("设备访问管理").assertDoesNotExist()
            val retryBounds = retry.getUnclippedBoundsInRoot()
            val leaveBounds = leave.getUnclippedBoundsInRoot()
            assertTrue(retryBounds.bottom - retryBounds.top >= 48.dp)
            assertTrue(leaveBounds.bottom - leaveBounds.top >= 48.dp)
            assertTrue(leaveBounds.top > retryBounds.bottom)
            val layout = compose.textLayout("撤销未确认")
            assertDp(
                "Centered heading",
                heading.getUnclippedBoundsInRoot().left + with(compose.density) {
                    ((layout.getLineLeft(0) + layout.getLineRight(0)) / 2).toDp()
                },
                (retryBounds.left + retryBounds.right) / 2
            )
            captureSettledUiEvidence("sender-revoke-unconfirmed.png", heading)
            retry.performClick()
            val pending = compose.onNodeWithText("重试中…").assertIsDisplayed().assertIsNotEnabled()
            leave.assertIsNotEnabled()
            compose.runOnIdle { assertEquals(1, proofRequests) }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            pending.assertIsDisplayed()
            captureSettledUiEvidence("sender-revoke-retrying.png", pending)
            compose.runOnIdle { retryResponse.complete(RevocationReply.Unconfirmed) }
            retry.assertIsEnabled()
            leave.performClick()
            compose.onNodeWithText("Pixel 9").assertIsDisplayed()
        }
    }

    @Test
    fun englishExplanationAndActionsRemainReachableAtDoubleFontScale() {
        compose.withSystemFontScale(2f) {
            compose.renderRevocationInventory(
                scope,
                AppLanguage.English,
                authorization = { "fresh-proof" },
                service = { _, _, _ -> RevocationReply.Unconfirmed }
            )
            compose.onNodeWithContentDescription("Revoke Pixel 9").performScrollTo().performClick()
            compose.onNodeWithText("Confirm and verify QQ").performScrollTo().performClick()
            compose.onNodeWithText("Verify with QQ and revoke").performScrollTo().performClick()
            val description =
                "The server did not return the expected result, so revocation of “Pixel 9” " +
                    "could not be confirmed. Please try again later."
            for (text in listOf(
                "Revocation unconfirmed",
                description,
                "Retry revocation",
                "Return to device access management"
            )) {
                val node = compose.onNodeWithText(text, useUnmergedTree = true)
                    .performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                val clipped = node.fetchSemanticsNode().boundsInRoot
                with(compose.density) {
                    assertDp(
                        "Unclipped $text width",
                        clipped.width.toDp(),
                        bounds.right - bounds.left
                    )
                    assertDp(
                        "Unclipped $text height",
                        clipped.height.toDp(),
                        bounds.bottom - bounds.top
                    )
                }
                val layout = compose.textLayout(text)
                assertFalse(
                    "$text overflows vertically: ${layout.size}, ${layout.lineCount} lines",
                    layout.didOverflowHeight
                )
                for (line in 0 until layout.lineCount) {
                    assertTrue(
                        "$text line $line extends beyond ${layout.size.width}px",
                        layout.getLineLeft(line) >= -1f &&
                            layout.getLineRight(line) <= layout.size.width + 1f
                    )
                }
            }
            compose.onNodeWithText("Return to device access management").performClick()
            compose.onNodeWithText("Pixel 9").performScrollTo().assertIsDisplayed()
        }
    }
}
