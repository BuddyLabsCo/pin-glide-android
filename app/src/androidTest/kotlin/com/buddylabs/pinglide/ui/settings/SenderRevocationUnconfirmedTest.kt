package com.buddylabs.pinglide.ui.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
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
            assertApprovedLayout(heading, retry, leave)
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
            val response = CompletableDeferred<RevocationReply>()
            var submitted = false
            compose.renderRevocationInventory(
                scope,
                AppLanguage.English,
                authorization = { "fresh-proof" },
                service = { _, _, _ ->
                    if (submitted) {
                        response.await()
                    } else {
                        submitted = true
                        RevocationReply.Unconfirmed
                    }
                }
            )
            compose.onNodeWithContentDescription("Revoke Pixel 9").performScrollTo().performClick()
            compose.onNodeWithText("Confirm and verify QQ").performScrollTo().performClick()
            compose.onNodeWithText("Verify with QQ and revoke").performScrollTo().performClick()
            val description =
                "The server did not return the expected result, so revocation of “Pixel 9” " +
                    "could not be confirmed. Please try again later."
            val returnLabel = "Return to device access management"
            for (text in listOf(
                "Revocation unconfirmed",
                description,
                "Retry revocation",
                returnLabel
            )) {
                compose.assertReadableRevocationText(text)
            }
            compose.onNodeWithText(
                "Retry revocation"
            ).performScrollTo().assertIsEnabled().performClick()
            for (text in listOf("Retrying…", returnLabel)) {
                compose.assertReadableRevocationText(text)
                compose.onNodeWithText(text).assertIsNotEnabled()
            }
            compose.runOnIdle { response.complete(RevocationReply.Revoked) }
            compose.onNodeWithText("“Pixel 9” revoked").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Retrying…").assertDoesNotExist()
            compose.onNodeWithText(returnLabel).performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithText("Galaxy S25").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Pixel 9").assertDoesNotExist()
        }
    }

    private fun assertApprovedLayout(
        heading: SemanticsNodeInteraction,
        retry: SemanticsNodeInteraction,
        leave: SemanticsNodeInteraction
    ) {
        val content = compose.onNode(
            hasScrollAction() and hasAnyDescendant(hasText("撤销未确认"))
        )
        val viewport = content.getUnclippedBoundsInRoot()
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val retryBounds = retry.getUnclippedBoundsInRoot()
        val leaveBounds = leave.getUnclippedBoundsInRoot()
        val center = (viewport.left + viewport.right) / 2
        for (text in listOf(
            "撤销未确认",
            "服务器未返回预期结果，暂时无法确认「Pixel 9」的撤销结果。请稍后重试。"
        )) {
            val bounds = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
            val layout = compose.textLayout(text)
            for (line in 0 until layout.lineCount) {
                val lineCenter = (layout.getLineLeft(line) + layout.getLineRight(line)) / 2
                assertDp(
                    "Centered $text line $line",
                    bounds.left + with(compose.density) {
                        lineCenter.toDp()
                    },
                    center
                )
            }
        }
        for (bounds in listOf(retryBounds, leaveBounds)) {
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
            assertDp("Action left edge", bounds.left, viewport.left)
            assertDp("Action right edge", bounds.right, viewport.right)
        }
        assertTrue(retryBounds.top > headingBounds.bottom)
        assertDp("Action spacing", leaveBounds.top - retryBounds.bottom, 8.dp)
        assertDp(
            "Vertically centered group",
            (headingBounds.top - 222.dp + leaveBounds.bottom) / 2,
            (viewport.top + viewport.bottom) / 2 - 10.dp
        )
        val artwork = content.captureToImage().asAndroidBitmap()
        with(compose.density) {
            assertWarningArtwork(artwork, (headingBounds.top - viewport.top - 111.dp).roundToPx())
        }
        assertRefreshArtwork(retry)
    }

    private fun assertWarningArtwork(artwork: android.graphics.Bitmap, centerY: Int) =
        with(compose.density) {
            val context = compose.activity
            val centerX = artwork.width / 2
            val tile = context.getColor(R.color.setup_warning_container)
            val icon = context.getColor(R.color.setup_on_warning)
            val surface = context.getColor(R.color.surface)
            fun sample(horizontal: Int, vertical: Int) = artwork.getPixel(
                centerX + horizontal.dp.roundToPx(),
                centerY + vertical.dp.roundToPx()
            )
            assertEquals("Warning tile", tile, sample(60, 0))
            assertEquals("Outside warning tile", surface, sample(80, 0))
            assertEquals("Cloud-off diagonal", icon, sample(-15, -15))
            assertEquals("Cloud-off slash through cloud", icon, sample(0, 5))
            assertEquals("Cloud cutout", tile, sample(-17, 5))
            assertEquals("Outside cloud", tile, sample(12, -16))
            for (radius in listOf(93.dp, 111.dp)) {
                val ringX = centerX + radius.roundToPx()
                val tolerance = 2.dp.roundToPx()
                assertTrue(
                    "Visible $radius ring",
                    (ringX - tolerance..ringX + tolerance).any {
                        artwork.getPixel(it, centerY) != surface
                    }
                )
            }
        }

    private fun assertRefreshArtwork(retry: SemanticsNodeInteraction) = with(compose.density) {
        val context = compose.activity
        val label = compose.onNodeWithText("重试撤销", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val left = retry.getUnclippedBoundsInRoot().left
        val button = retry.captureToImage().asAndroidBitmap()
        val centerX = (label.left - left - 18.dp).roundToPx()
        val centerY = button.height / 2
        val background = context.getColor(R.color.primary)
        val foreground = context.getColor(R.color.on_primary)
        fun sample(horizontal: Int, vertical: Int) = button.getPixel(
            centerX + horizontal.dp.roundToPx(),
            centerY + vertical.dp.roundToPx()
        )
        assertEquals("Primary button fill", background, button.getPixel(button.width / 4, centerY))
        assertEquals("Refresh arrowhead", foreground, sample(5, -4))
        assertEquals("Refresh arc", foreground, sample(0, -6))
        assertEquals("Refresh center cutout", background, sample(0, 0))
        assertEquals("Refresh open corner", background, sample(-6, -6))
    }
}
