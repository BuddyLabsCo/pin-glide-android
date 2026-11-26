package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        compose.onNodeWithText(
            "该发送端设备的旧注册已关闭，所有配对已移除。" +
                "再次使用时，需要在该设备重新登录并重新配对。"
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        val action = compose.onNodeWithText("返回设备访问管理").assertIsDisplayed().assertIsEnabled()
        val viewportNode = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText(title)))
        val viewport = viewportNode.getUnclippedBoundsInRoot()
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val actionBounds = action.getUnclippedBoundsInRoot()
        assertTrue(actionBounds.bottom - actionBounds.top >= 48.dp)
        assertEquals(
            (viewport.left + viewport.right).value / 2,
            (actionBounds.left + actionBounds.right).value / 2,
            1f
        )
        assertEquals(
            (viewport.top + viewport.bottom).value / 2,
            (headingBounds.top - 90.dp + actionBounds.bottom).value / 2,
            3f
        )
        val artwork = viewportNode.captureToImage().asAndroidBitmap()
        val badgeY = with(compose.density) {
            (headingBounds.top - viewport.top - 54.dp).roundToPx()
        }
        assertEquals(
            compose.activity.getColor(R.color.error_emblem_container),
            artwork.getPixel(
                artwork.width / 2 + with(compose.density) {
                    24.dp.roundToPx()
                },
                badgeY
            )
        )
        val button = action.captureToImage().asAndroidBitmap()
        assertEquals(
            compose.activity.getColor(R.color.primary),
            button.getPixel(button.width / 4, button.height / 2)
        )
        captureSettledUiEvidence("sender-revoked.png", heading)
        action.performClick()
        heading.assertDoesNotExist()
        compose.onNodeWithText("设备访问管理").assertIsDisplayed()
        compose.onNodeWithText("小米 15").assertIsDisplayed()
        compose.onNodeWithText("Galaxy S25").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pixel 9").assertDoesNotExist()
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
                val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                val clipped = node.fetchSemanticsNode().boundsInRoot
                with(compose.density) {
                    assertEquals(
                        (bounds.bottom - bounds.top).value,
                        clipped.height.toDp().value,
                        1f
                    )
                    assertEquals((bounds.right - bounds.left).value, clipped.width.toDp().value, 1f)
                }
            }
            compose.onNodeWithText("Return to device access management").performClick()
            compose.onNodeWithText("Device access management").assertIsDisplayed()
        }
    }
}
