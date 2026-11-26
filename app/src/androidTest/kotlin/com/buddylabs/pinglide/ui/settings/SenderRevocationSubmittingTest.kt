package com.buddylabs.pinglide.ui.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.RevocationReply
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withSystemFontScale
import kotlin.math.roundToInt
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

class SenderRevocationSubmittingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun submittingShowsSelectedSenderAndDisabledWaitUntilResponse() {
        val response = CompletableDeferred<RevocationReply>()
        compose.renderRevocationInventory(
            scope,
            AppLanguage.SimplifiedChinese,
            authorization = { "fresh-proof" },
            service = { _, _, _ -> response.await() }
        )
        compose.onNodeWithContentDescription("撤销Pixel 9").performScrollTo().performClick()
        compose.onNodeWithText("确认，验证 QQ").performClick()
        compose.onNodeWithText("使用 QQ 验证并撤销").performClick()
        val heading = compose.onNodeWithText(
            "正在提交撤销",
            useUnmergedTree = true
        ).assertIsDisplayed()
        val wait = compose.onNodeWithText("请稍候…").assertIsDisplayed().assertIsNotEnabled()
        val description = "正在撤销「Pixel 9」…"
        compose.onNodeWithText(description, useUnmergedTree = true).assertIsDisplayed()
        assertSubmittingAnnouncement("正在提交撤销", description)
        val content = compose.onNode(
            hasScrollAction() and hasAnyDescendant(hasText("正在提交撤销"))
        )
        val viewport = content.getUnclippedBoundsInRoot()
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val waitBounds = wait.getUnclippedBoundsInRoot()
        val layout = compose.textLayout("正在提交撤销")
        val lineCenter = (layout.getLineLeft(0) + layout.getLineRight(0)) / 2
        assertDp(
            "Centered heading",
            headingBounds.left + with(compose.density) { lineCenter.toDp() },
            (viewport.left + viewport.right) / 2
        )
        assertTrue(waitBounds.top > headingBounds.bottom)
        assertTrue(waitBounds.bottom - waitBounds.top >= 48.dp)
        assertDp("Wait left edge", waitBounds.left, viewport.left)
        assertDp("Wait right edge", waitBounds.right, viewport.right)
        val statusBounds = compose.onNodeWithText("正在提交撤销").getUnclippedBoundsInRoot()
        val emblemTop = statusBounds.top - 222.dp
        val groupCenter = (emblemTop + waitBounds.bottom) / 2
        assertDp(
            "Vertically centered group",
            groupCenter,
            (viewport.top + viewport.bottom) / 2 - 10.dp
        )
        val status = compose.onNodeWithText("正在提交撤销").fetchSemanticsNode()
        val position = status.layoutInfo.coordinates.positionOnScreen()
        content.captureToImage()
        val artwork =
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        with(compose.density) {
            val centerX =
                position.x.roundToInt() + status.size.width / 2
            val centerY = position.y.roundToInt() - 111.dp.roundToPx()
            val lockColor = ContextCompat.getColor(compose.activity, R.color.on_primary_container)
            assertEquals(
                "Lock body",
                lockColor,
                artwork.getPixel(
                    centerX - 10.dp.roundToPx(),
                    centerY + 6.dp.roundToPx()
                )
            )
            assertTrue(
                "Lock keyhole",
                artwork.getPixel(
                    centerX + 3.dp.roundToPx(),
                    centerY + 5.dp.roundToPx()
                ) != lockColor
            )
            val surface = ContextCompat.getColor(compose.activity, R.color.surface)
            assertTrue(
                "Badge tile",
                artwork.getPixel(centerX + 60.dp.roundToPx(), centerY) != surface
            )
            assertEquals(
                "Outside badge",
                surface,
                artwork.getPixel(
                    centerX + 80.dp.roundToPx(),
                    centerY
                )
            )
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        wait.assertIsDisplayed().assertIsNotEnabled()
        captureSettledUiEvidence("sender-revoke-submitting.png", heading)
        compose.runOnIdle { response.complete(RevocationReply.WrongAccount) }
        wait.assertDoesNotExist()
        compose.onNodeWithText("验证后撤销设备").assertIsDisplayed()
    }

    @Test
    fun englishSubmittingRemainsReachableAtDoubleFontScale() {
        compose.withSystemFontScale(2f) {
            val response = CompletableDeferred<RevocationReply>()
            compose.renderRevocationInventory(
                scope,
                AppLanguage.English,
                authorization = { "fresh-proof" },
                service = { _, _, _ -> response.await() }
            )
            compose.onNodeWithContentDescription("Revoke Pixel 9").performScrollTo().performClick()
            compose.onNodeWithText("Confirm and verify QQ").performScrollTo().performClick()
            compose.onNodeWithText("Verify with QQ and revoke").performScrollTo().performClick()
            val title = "Submitting revocation"
            val description = "Revoking “Pixel 9”…"
            assertSubmittingAnnouncement(title, description)
            for (text in listOf(title, description, "Please wait…")) {
                val node = compose.onNodeWithText(text, useUnmergedTree = true)
                    .performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                val clipped = node.fetchSemanticsNode().boundsInRoot
                with(compose.density) {
                    assertDp(
                        "Unclipped $text height",
                        clipped.height.toDp(),
                        bounds.bottom - bounds.top
                    )
                    assertDp(
                        "Unclipped $text width",
                        clipped.width.toDp(),
                        bounds.right - bounds.left
                    )
                }
                val layout = compose.textLayout(text)
                assertFalse(
                    "$text overflows: ${layout.size}, ${layout.lineCount} lines",
                    layout.hasVisualOverflow
                )
            }
            val wait = compose.onNodeWithText("Please wait…").assertIsNotEnabled()
            val waitBounds = wait.getUnclippedBoundsInRoot()
            assertTrue(waitBounds.bottom - waitBounds.top >= 48.dp)
            compose.runOnIdle { response.complete(RevocationReply.WrongAccount) }
            wait.assertDoesNotExist()
            compose.onNodeWithText(
                "Verify to revoke the device"
            ).performScrollTo().assertIsDisplayed()
        }
    }

    private fun assertSubmittingAnnouncement(title: String, description: String) {
        val liveStatus = SemanticsMatcher.expectValue(
            SemanticsProperties.LiveRegion,
            LiveRegionMode.Polite
        )
        compose.onAllNodes(liveStatus).assertCountEquals(1)
        compose.onNode(liveStatus)
            .assert(hasText(title) and hasText(description))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onAllNodes(hasText(title), useUnmergedTree = true).assertCountEquals(1)
    }
}
