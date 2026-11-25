package com.buddylabs.pinglide.ui.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
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
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
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
        compose.onNodeWithText("验证后撤销设备").assertIsDisplayed()
        compose.onNodeWithText("使用 QQ 验证并撤销").assertIsDisplayed()
        compose.onNodeWithText(
            "你已确认撤销“Pixel 9”。请验证当前登录的 QQ 账号，验证通过后将立即提交撤销。"
        ).assertIsDisplayed()
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
        }
    }

    private fun renderInventory(language: AppLanguage) {
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
            { error("Confirmation must not open QQ") },
            { _, _, _ -> error("Confirmation must not submit revocation") },
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
