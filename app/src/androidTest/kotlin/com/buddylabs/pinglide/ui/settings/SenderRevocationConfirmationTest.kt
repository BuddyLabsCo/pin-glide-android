package com.buddylabs.pinglide.ui.settings

import android.graphics.Color
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.QqAuthorizationException
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

class SenderRevocationConfirmationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun approvedDialogNamesTheSelectedSenderAndKeepsCancelAndOwnerProof() {
        compose.renderRevocationInventory(scope, AppLanguage.SimplifiedChinese)
        val revoke = compose.onNodeWithContentDescription("撤销Pixel 9")
        revoke.performScrollTo().performClick()
        val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "撤销「Pixel 9」？")
        val dialog = compose.onNode(pane).assertIsDisplayed()
        compose.onNodeWithText(
            "这会永久关闭该发送端设备的旧注册并移除它的所有配对。" +
                "再次使用时，需要在该设备重新登录并重新配对。其他发送端设备不受影响。"
        ).assertIsDisplayed()
        val cancel = compose.onNode(hasText("取消") and hasAnyAncestor(pane)).assertIsDisplayed()
        val confirm = compose.onNodeWithText("确认，验证 QQ").assertIsDisplayed()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        val confirmBounds = confirm.getUnclippedBoundsInRoot()
        assertEquals(cancelBounds.top, confirmBounds.top)
        assertTrue(cancelBounds.right < confirmBounds.left)
        assertTrue(cancelBounds.bottom - cancelBounds.top >= 48.dp)
        assertTrue(confirmBounds.bottom - confirmBounds.top >= 48.dp)
        val artwork = dialog.captureToImage().asAndroidBitmap()
        val button = confirm.captureToImage().asAndroidBitmap()
        with(compose.density) {
            assertEquals(
                ContextCompat.getColor(compose.activity, R.color.error_emblem_container),
                artwork.getPixel(30.dp.roundToPx(), 48.dp.roundToPx())
            )
            assertEquals(
                ContextCompat.getColor(compose.activity, R.color.error_emblem_container),
                button.getPixel(button.width / 2, 6.dp.roundToPx())
            )
        }
        captureSettledUiEvidence("sender-revoke-confirm.png", dialog)
        cancel.performClick()
        dialog.assertDoesNotExist()
        revoke.assertIsDisplayed().performClick()
        confirm.performClick()
        dialog.assertDoesNotExist()
        val eyebrow = compose.onNodeWithText("需要所有者证明").assertIsDisplayed()
        val heading = compose.onNodeWithText("验证后撤销设备").assertIsDisplayed()
        val verify = compose.onNodeWithText("使用 QQ 验证并撤销").assertIsDisplayed()
        val proofCancel = compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText(
            "你已确认撤销「Pixel 9」。请验证当前登录的 QQ 账号，验证通过后将立即提交撤销。"
        ).assertIsDisplayed()
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val verifyBounds = verify.getUnclippedBoundsInRoot()
        val cancelActionBounds = proofCancel.getUnclippedBoundsInRoot()
        assertTrue(eyebrow.getUnclippedBoundsInRoot().bottom <= headingBounds.top)
        val headingCenter = (headingBounds.left + headingBounds.right) / 2
        val verifyCenter = (verifyBounds.left + verifyBounds.right) / 2
        assertTrue(kotlin.math.abs((headingCenter - verifyCenter).value) < 2f)
        assertTrue(verifyBounds.top - headingBounds.bottom > 120.dp)
        assertTrue(verifyBounds.bottom - verifyBounds.top >= 48.dp)
        assertTrue(cancelActionBounds.bottom - cancelActionBounds.top >= 48.dp)
        assertTrue(cancelActionBounds.top >= verifyBounds.bottom)
        assertOwnerProofArtworkAndAnchoring()
        captureSettledUiEvidence("sender-owner-proof.png", heading)
        proofCancel.performClick()
        compose.onNodeWithText("Pixel 9").assertIsDisplayed()
    }

    private fun assertOwnerProofArtworkAndAnchoring() {
        val verifyBounds = compose.onNodeWithText("使用 QQ 验证并撤销").getUnclippedBoundsInRoot()
        val cancelActionBounds = compose.onNodeWithText("取消").getUnclippedBoundsInRoot()
        val root = compose.onNode(isRoot() and hasAnyDescendant(hasText("验证后撤销设备")))
        val viewport = root.getUnclippedBoundsInRoot()
        assertDp("Cancel bottom inset", viewport.bottom - cancelActionBounds.bottom, 24.dp)
        assertDp("Verify to Cancel gap", cancelActionBounds.top - verifyBounds.bottom, 8.dp)
        val header = compose.onNodeWithText("验证 QQ 账号").getUnclippedBoundsInRoot()
        val emblemCenter = ((header.top + header.bottom) / 2 + verifyBounds.top) / 2 - 70.dp
        root.captureToImage()
        val screenshot =
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        with(compose.density) {
            val centerX = screenshot.width / 2
            val centerY = (emblemCenter - viewport.top).roundToPx()
            val tileEdges = (centerX - 60.dp.roundToPx()..centerX + 60.dp.roundToPx())
                .filter { isBrandTile(screenshot.getPixel(it, centerY)) }
            assertTrue("Centered brand tile is visible", tileEdges.isNotEmpty())
            assertDp("Tile left", tileEdges.first().toDp(), centerX.toDp() - 46.dp)
            assertDp("Tile right", (tileEdges.last() + 1).toDp(), centerX.toDp() + 46.dp)
            val tilePixels = (centerY - 60.dp.roundToPx()..centerY + 60.dp.roundToPx())
                .filter { isBrandTile(screenshot.getPixel(centerX, it)) }
            assertTrue("Centered brand tile is visible", tilePixels.isNotEmpty())
            assertDp("Tile top", tilePixels.first().toDp(), centerY.toDp() - 46.dp)
            assertDp("Tile bottom", (tilePixels.last() + 1).toDp(), centerY.toDp() + 46.dp)
            val bird = screenshot.getPixel(centerX, centerY)
            assertTrue("Pin Glide bird is visible", Color.red(bird) < 80 && Color.blue(bird) > 150)
        }
    }

    @Test
    fun englishActionsRemainReachableAtDoubleFontScaleAndBackCancels() {
        compose.withSystemFontScale(2f) {
            compose.renderRevocationInventory(
                scope,
                AppLanguage.English,
                authorization = { throw QqAuthorizationException(cancelled = true) }
            )
            val revoke = compose.onNodeWithContentDescription("Revoke Pixel 9")
            revoke.performScrollTo().performClick()
            val pane = SemanticsMatcher.expectValue(
                SemanticsProperties.PaneTitle,
                "Revoke “Pixel 9”?"
            )
            val cancel = compose.onNode(hasText("Cancel") and hasAnyAncestor(pane))
            val confirm = compose.onNodeWithText("Confirm and verify QQ")
            confirm.performScrollTo().assertIsDisplayed()
            cancel.performScrollTo().assertIsDisplayed().performClick()
            compose.onNode(pane).assertDoesNotExist()
            revoke.performScrollTo().performClick()
            compose.onNode(pane).assertIsDisplayed()
            cancel.performScrollTo().assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNode(pane).assertDoesNotExist()
            compose.onNodeWithContentDescription("Revoke Pixel 9").performScrollTo().performClick()
            confirm.performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(
                "Verify to revoke the device"
            ).performScrollTo().assertIsDisplayed()
            val description = "You confirmed revoking “Pixel 9”. Verify the QQ Account currently " +
                "signed in. Successful verification immediately submits the revocation."
            val descriptionNode = compose.onNodeWithText(
                description
            ).performScrollTo().assertIsDisplayed()
            val descriptionBounds = descriptionNode.getUnclippedBoundsInRoot()
            val viewport = compose.onNode(isRoot() and hasAnyDescendant(hasText(description)))
                .getUnclippedBoundsInRoot()
            assertTrue(descriptionBounds.top >= viewport.top)
            assertTrue(descriptionBounds.bottom <= viewport.bottom)
            val clipped = descriptionNode.fetchSemanticsNode().boundsInRoot
            with(compose.density) {
                assertDp(
                    "Unclipped description height",
                    clipped.height.toDp(),
                    descriptionBounds.bottom - descriptionBounds.top
                )
                assertDp(
                    "Unclipped description width",
                    clipped.width.toDp(),
                    descriptionBounds.right - descriptionBounds.left
                )
            }
            assertFalse(compose.textLayout(description).hasVisualOverflow)
            compose.onNodeWithText(
                "Verify with QQ and revoke"
            ).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(
                "QQ verification was cancelled. Revocation was not submitted."
            ).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Verify with QQ and revoke")
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Pixel 9").performScrollTo().assertIsDisplayed()
        }
    }

    private fun isBrandTile(pixel: Int): Boolean = Color.red(pixel) in 235..241 &&
        Color.green(pixel) in 248..254 &&
        Color.blue(pixel) in 243..249

    @Test
    fun verificationRequestsFreshProofForOnlyTheSelectedSender() {
        val proof = CompletableDeferred<String>()
        var attempts = 0
        val submissions = mutableListOf<Pair<String, String>>()
        compose.renderRevocationInventory(
            scope,
            AppLanguage.SimplifiedChinese,
            authorization = {
                attempts++
                if (attempts == 1) throw QqAuthorizationException(cancelled = true)
                proof.await()
            },
            service = { _, senderId, code ->
                submissions.add(senderId to code)
                RevocationReply.WrongAccount
            }
        )
        compose.onNodeWithContentDescription("撤销Pixel 9").performScrollTo().performClick()
        compose.onNodeWithText("确认，验证 QQ").performClick()
        compose.runOnIdle { assertEquals(0, attempts) }
        val verify = compose.onNodeWithText("使用 QQ 验证并撤销")
        verify.performClick()
        assertCancelledOwnerProof()
        compose.runOnIdle { assertTrue(submissions.isEmpty()) }
        verify.performClick()
        compose.onNodeWithText("正在验证 QQ 账号…").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(2, attempts)
            assertTrue(submissions.isEmpty())
            proof.complete("fresh-proof")
        }
        compose.onNodeWithText(
            "请使用当前登录的 QQ 账号验证。所选设备未被撤销，当前账号未改变。"
        ).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("pixel" to "fresh-proof"), submissions) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("Pixel 9").assertIsDisplayed()
    }

    private fun assertCancelledOwnerProof() {
        val message = "QQ 验证已取消，未提交撤销。"
        val warning = compose.onNodeWithTag("owner-proof-warning").assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive
                )
            )
        val text = compose.onNodeWithText(message).assertIsDisplayed()
        val description = compose.onNodeWithText(
            "你已确认撤销「Pixel 9」。请验证当前登录的 QQ 账号，验证通过后将立即提交撤销。"
        ).assertIsDisplayed()
        val verify = compose.onNodeWithText("使用 QQ 验证并撤销")
            .assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("取消").assertIsDisplayed().assertIsEnabled()
        val bounds = warning.getUnclippedBoundsInRoot()
        val verifyBounds = verify.getUnclippedBoundsInRoot()
        assertDp(
            "Centered warning",
            (bounds.left + bounds.right) / 2,
            (verifyBounds.left + verifyBounds.right) / 2
        )
        assertTrue(bounds.right - bounds.left < verifyBounds.right - verifyBounds.left)
        assertTrue(bounds.top > description.getUnclippedBoundsInRoot().bottom)
        assertTrue(bounds.bottom < verifyBounds.top)
        assertTrue(text.getUnclippedBoundsInRoot().left - bounds.left >= 40.dp)
        val image = warning.captureToImage().asAndroidBitmap()
        val color = ContextCompat.getColor(compose.activity, R.color.setup_on_warning)
        with(compose.density) {
            assertEquals(
                ContextCompat.getColor(compose.activity, R.color.setup_warning_container),
                image.getPixel(image.width / 2, 2.dp.roundToPx())
            )
            assertTrue(
                "Warning icon is visible",
                (13.dp.roundToPx() until 31.dp.roundToPx()).any { x ->
                    (14.dp.roundToPx() until 32.dp.roundToPx()).any { y ->
                        image.getPixel(x, y) == color
                    }
                }
            )
        }
        captureSettledUiEvidence("sender-owner-proof-cancelled.png", warning)
    }

    @Test
    fun failedAndWrongAccountProofKeepFeedbackAndActionsReachableAtDoubleFontScale() {
        compose.withSystemFontScale(2f) {
            var attempts = 0
            val submissions = mutableListOf<Pair<String, String>>()
            compose.renderRevocationInventory(
                scope,
                AppLanguage.English,
                authorization = {
                    if (attempts++ == 0) throw QqAuthorizationException()
                    "fresh-proof"
                },
                service = { _, senderId, code ->
                    submissions.add(senderId to code)
                    RevocationReply.WrongAccount
                }
            )
            compose.onNodeWithContentDescription("Revoke Pixel 9").performScrollTo().performClick()
            compose.onNodeWithText("Confirm and verify QQ").performScrollTo().performClick()
            val verify = compose.onNodeWithText("Verify with QQ and revoke")
            verify.performScrollTo().performClick()
            assertLargeOwnerProofWarning(
                "QQ verification did not complete. The selected device was not revoked. " +
                    "Please try again."
            )
            compose.runOnIdle { assertTrue(submissions.isEmpty()) }
            verify.performScrollTo().performClick()
            assertLargeOwnerProofWarning(
                "Verify with the QQ Account currently signed in. The selected device was not " +
                    "revoked and your Account has not changed."
            )
            compose.runOnIdle {
                assertEquals(2, attempts)
                assertEquals(listOf("pixel" to "fresh-proof"), submissions)
            }
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
            compose.onNodeWithText("Pixel 9").performScrollTo().assertIsDisplayed()
        }
    }

    private fun assertLargeOwnerProofWarning(message: String) {
        compose.onNodeWithText(
            "You confirmed revoking “Pixel 9”. Verify the QQ Account currently signed in. " +
                "Successful verification immediately submits the revocation."
        ).performScrollTo().assertIsDisplayed()
        val warning = compose.onNodeWithTag("owner-proof-warning").performScrollTo()
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive
                )
            )
        val text = compose.onNodeWithText(message).assertIsDisplayed()
        val bounds = text.getUnclippedBoundsInRoot()
        val clipped = text.fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertDp("Warning text height", clipped.height.toDp(), bounds.bottom - bounds.top)
            assertDp("Warning text width", clipped.width.toDp(), bounds.right - bounds.left)
        }
        assertFalse(compose.textLayout(message).hasVisualOverflow)
        val image = warning.captureToImage().asAndroidBitmap()
        assertEquals(
            ContextCompat.getColor(compose.activity, R.color.setup_warning_container),
            image.getPixel(image.width / 2, 2)
        )
        compose.onNodeWithText("Verify with QQ and revoke")
            .performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
}
