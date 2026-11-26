package com.buddylabs.pinglide.ui.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.RevocationReply
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withSystemFontScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SenderRevocationSuccessTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun otherSenderResultMatchesCenteredPrototypeAndReturnsToInventory() {
        compose.withSystemFontScale(1f) {
            compose.renderRevocationInventory(
                scope,
                AppLanguage.SimplifiedChinese,
                authorization = { "fresh-proof" },
                service = { _, senderId, _ ->
                    assertEquals("pixel", senderId)
                    RevocationReply.Revoked
                }
            )
            compose.onNodeWithContentDescription("撤销Pixel 9").performScrollTo().performClick()
            compose.onNodeWithText("确认，验证 QQ").performClick()
            compose.onNodeWithText("使用 QQ 验证并撤销").performClick()
            val title = "「Pixel 9」已撤销"
            val heading = compose.onNodeWithText(title).assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite
                    )
                )
            val description = "该发送端设备的旧注册已关闭，所有配对已移除。" +
                "再次使用时，需要在该设备重新登录并重新配对。"
            compose.onNodeWithText(description).assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").assertDoesNotExist()
            val returnLabel = "返回设备访问管理"
            val action = compose.onNodeWithText(returnLabel).assertIsDisplayed().assertIsEnabled()
            assertEquals(1f, compose.textLayout(title).layoutInput.density.fontScale)
            assertApprovedLayout(title, description, returnLabel, heading, action)
            captureSettledUiEvidence("sender-revoked.png", heading)
            action.performClick()
            heading.assertDoesNotExist()
            compose.onNodeWithText("设备访问管理").assertIsDisplayed()
            compose.onNodeWithText("小米 15").assertIsDisplayed()
            compose.onNodeWithText("Galaxy S25").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Pixel 9").assertDoesNotExist()
        }
    }

    private fun assertApprovedLayout(
        title: String,
        description: String,
        returnLabel: String,
        heading: SemanticsNodeInteraction,
        action: SemanticsNodeInteraction
    ) {
        val viewportNode = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(title)))
        val viewport = viewportNode.getUnclippedBoundsInRoot()
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val actionBounds = action.getUnclippedBoundsInRoot()
        assertTrue(actionBounds.bottom - actionBounds.top >= 48.dp)
        assertDp("Return left edge", actionBounds.left, viewport.left + 8.dp)
        assertDp("Return right edge", actionBounds.right, viewport.right - 8.dp)
        assertDp(
            "Vertically centered group",
            (headingBounds.top - 90.dp + actionBounds.bottom) / 2,
            (viewport.top + viewport.bottom) / 2
        )
        val artwork = viewportNode.captureToImage().asAndroidBitmap()
        assertCenteredText(title, viewport, artwork, R.color.on_surface)
        assertCenteredText(description, viewport, artwork, R.color.on_surface_variant)
        assertCenteredText(returnLabel, viewport, artwork, R.color.on_primary)
        val badgeY = with(compose.density) {
            (headingBounds.top - viewport.top - 54.dp).roundToPx()
        }
        assertLockArtwork(artwork, badgeY)
        val button = action.captureToImage().asAndroidBitmap()
        assertEquals(
            compose.activity.getColor(R.color.primary),
            button.getPixel(button.width / 4, button.height / 2)
        )
    }

    private fun assertCenteredText(
        text: String,
        viewport: DpRect,
        artwork: Bitmap,
        colorResource: Int
    ) = with(compose.density) {
        val bounds = compose.onNodeWithText(text, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertDp(
            "Centered text bounds for $text",
            (bounds.left + bounds.right) / 2,
            (viewport.left + viewport.right) / 2
        )
        val layout = compose.textLayout(text)
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
        val foreground = compose.activity.getColor(colorResource)
        val textTop = (bounds.top - viewport.top).roundToPx()
        for (line in 0 until layout.lineCount) {
            var left = artwork.width
            var right = -1
            val lineTop = (textTop + layout.getLineTop(line).toInt()).coerceAtLeast(0)
            val lineBottom = (textTop + layout.getLineBottom(line).toInt())
                .coerceAtMost(artwork.height)
            val lineHeight = lineBottom - lineTop
            val pixels = IntArray(artwork.width * lineHeight)
            artwork.getPixels(pixels, 0, artwork.width, 0, lineTop, artwork.width, lineHeight)
            for ((index, pixel) in pixels.withIndex()) {
                if (pixel == foreground) {
                    val horizontal = index % artwork.width
                    left = minOf(left, horizontal)
                    right = maxOf(right, horizontal)
                }
            }
            assertTrue("Visible $text line $line", left <= right)
            assertEquals(
                "Centered ink for $text line $line",
                artwork.width / 2f,
                (left + right) / 2f,
                10.dp.toPx()
            )
        }
    }

    private fun assertLockArtwork(artwork: Bitmap, badgeY: Int) = with(compose.density) {
        val tile = compose.activity.getColor(R.color.error_emblem_container)
        val lock = compose.activity.getColor(R.color.error_emblem)
        fun sample(horizontal: Int, vertical: Int) = artwork.getPixel(
            artwork.width / 2 + horizontal.dp.roundToPx(),
            badgeY + vertical.dp.roundToPx()
        )
        assertEquals("Badge tile", tile, sample(24, 0))
        assertEquals("Outside badge", compose.activity.getColor(R.color.surface), sample(40, 0))
        assertEquals("Lock body", lock, sample(-6, 5))
        assertEquals("Lock keyhole cutout", tile, sample(1, 3))
        assertEquals("Lock shackle", lock, sample(-5, -7))
        assertEquals("Lock shackle cutout", tile, sample(1, -6))
    }

    @Test
    fun englishResultForAnotherSenderRemainsReachableAtDoubleFontScale() {
        compose.withSystemFontScale(2f) {
            compose.renderRevocationInventory(
                scope,
                AppLanguage.English,
                authorization = { "fresh-proof" },
                service = { _, senderId, _ ->
                    assertEquals("samsung", senderId)
                    RevocationReply.Revoked
                }
            )
            compose.onNodeWithContentDescription(
                "Revoke Galaxy S25"
            ).performScrollTo().performClick()
            compose.onNodeWithText("Confirm and verify QQ").performScrollTo().performClick()
            compose.onNodeWithText("Verify with QQ and revoke").performScrollTo().performClick()
            for (text in listOf(
                "“Galaxy S25” revoked",
                "This Sender’s old registration is closed and all its Pairings are removed. " +
                    "To use it again, sign in and pair again on that device.",
                "Return to device access management"
            )) {
                compose.assertReadableRevocationText(text)
            }
            compose.onNodeWithText("Return to device access management").performClick()
            compose.onNodeWithText("Device access management").assertIsDisplayed()
        }
    }
}
