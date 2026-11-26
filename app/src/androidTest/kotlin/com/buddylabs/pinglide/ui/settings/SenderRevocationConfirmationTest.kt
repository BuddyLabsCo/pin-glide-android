package com.buddylabs.pinglide.ui.settings

import android.graphics.Color
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.domain.identity.QqAuthorizationException
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RevocationReply
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.identity.SenderRevocationService
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
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
        renderInventory(AppLanguage.SimplifiedChinese)
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
            renderInventory(AppLanguage.English)
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
            ).performScrollTo().assertIsDisplayed()
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
        renderInventory(
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
        compose.onNodeWithText(
            "已取消 QQ 验证。尚未提交撤销，请重新验证或返回设备访问管理。"
        ).assertIsDisplayed()
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

    @Test
    fun submittingShowsSelectedSenderAndDisabledWaitUntilResponse() {
        val response = CompletableDeferred<RevocationReply>()
        renderInventory(
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
        val root = compose.onNode(isRoot() and hasAnyDescendant(hasText("正在提交撤销")))
        val viewport = root.getUnclippedBoundsInRoot()
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val waitBounds = wait.getUnclippedBoundsInRoot()
        assertDp(
            "Centered heading",
            (headingBounds.left + headingBounds.right) / 2,
            (viewport.left + viewport.right) / 2
        )
        assertTrue(waitBounds.top > headingBounds.bottom)
        assertTrue(waitBounds.bottom - waitBounds.top >= 48.dp)
        assertDp("Wait left inset", waitBounds.left - viewport.left, 20.dp)
        assertDp("Wait right inset", viewport.right - waitBounds.right, 20.dp)
        val statusBounds = compose.onNodeWithText("正在提交撤销").getUnclippedBoundsInRoot()
        val emblemTop = statusBounds.top - 222.dp
        val groupCenter = (emblemTop + waitBounds.bottom) / 2
        val viewportCenter = (viewport.top + viewport.bottom) / 2
        assertTrue(
            "Vertically centered group",
            kotlin.math.abs(
                (groupCenter - viewportCenter).value
            ) <= 16f
        )
        root.captureToImage()
        val artwork =
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        with(compose.density) {
            val centerX = artwork.width / 2
            val centerY = (emblemTop + 111.dp - viewport.top).roundToPx()
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
            renderInventory(
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

    private fun renderInventory(
        language: AppLanguage,
        authorization: QqAuthorization = { error("Confirmation must not open QQ") },
        service: SenderRevocationService = { _, _, _ ->
            error("Confirmation must not submit revocation")
        }
    ) {
        val expiry = "2099-01-01T00:00:00Z"
        val identity = SenderIdentity(
            "example-account",
            "example-sender",
            ManagementCredential("example-management", expiry),
            UploadCredential("example-upload", expiry),
            RefreshCredential("example-refresh", expiry)
        )
        val store = object : IdentityStore {
            override suspend fun read() = IdentitySnapshot(identity = identity)

            override suspend fun updateRenewal(
                expected: IdentitySnapshot,
                replacement: IdentitySnapshot,
                attempt: LoginAttempt
            ): Boolean = error("Unexpected renewal")

            override suspend fun accept(
                expectedGeneration: Long,
                identity: SenderIdentity,
                attempt: LoginAttempt
            ): Boolean = error("Unexpected login")
        }
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val inventory = AccountSenders(
            store,
            renewal,
            {
                listOf(
                    AccountSender("example-sender", "小米 15"),
                    AccountSender("pixel", "Pixel 9"),
                    AccountSender("samsung", "Galaxy S25")
                )
            },
            scope
        )
        val revocation = SenderRevocation(
            store,
            inventory,
            renewal,
            authorization,
            service,
            scope
        )
        val app = compose.activity.application as PinGlideApplication
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(language) {
                PinGlideTheme {
                    SettingsNavigation(
                        language = language,
                        onLanguage = {},
                        onBatterySettings = { false },
                        installedVersion = "0.1.0",
                        account = app.accountSession,
                        senders = inventory,
                        revocation = revocation,
                        onLogout = { error("Unexpected logout") },
                        onDismiss = {}
                    )
                }
            }
        }
        compose.onNodeWithText(
            if (language == AppLanguage.English) "Device access management" else "设备访问管理"
        ).performScrollTo().performClick()
        compose.onNodeWithText("Pixel 9").performScrollTo().assertIsDisplayed()
    }
}
