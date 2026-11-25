package com.buddylabs.pinglide.ui.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountService
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.LogoutStore
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
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

class LogoutConfirmationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun approvedLogoutDialogKeepsCancelAndImmediateConfirmationActions() {
        var logouts = 0
        renderAccount(AppLanguage.SimplifiedChinese) { logouts++ }
        val logout = compose.onNodeWithText("退出登录")
        logout.performClick()
        val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "退出登录？")
        val dialog = compose.onNode(pane).assertIsDisplayed()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNodeWithText("鹊信会立即停止本机转发，并尝试撤销服务端凭据。设备配对和本地配对密钥会保留。")
            .assertIsDisplayed()
        val cancel = compose.onNode(hasText("取消") and hasAnyAncestor(pane)).assertIsDisplayed()
        val confirm = compose.onNode(hasText("退出登录") and hasAnyAncestor(pane)).assertIsDisplayed()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        val confirmBounds = confirm.getUnclippedBoundsInRoot()
        assertEquals(cancelBounds.top, confirmBounds.top)
        assertTrue(cancelBounds.right < confirmBounds.left)
        assertTrue(cancelBounds.bottom - cancelBounds.top >= 48.dp)
        assertTrue(confirmBounds.bottom - confirmBounds.top >= 48.dp)
        val artwork = dialog.captureToImage().asAndroidBitmap()
        with(compose.density) {
            assertEquals(
                ContextCompat.getColor(compose.activity, R.color.primary_container),
                artwork.getPixel(30.dp.roundToPx(), 48.dp.roundToPx())
            )
        }
        captureSettledUiEvidence("logout-confirm.png", dialog)
        cancel.performClick()
        dialog.assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, logouts) }
        logout.performClick()
        confirm.performClick()
        dialog.assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, logouts) }
    }

    @Test
    fun englishKeyboardFocusReturnsToLogoutActionAfterBackAndCancel() {
        var logouts = 0
        renderAccount(AppLanguage.English) { logouts++ }
        val logout = compose.onNodeWithText("Log out")
        pressKey(KeyEvent.KEYCODE_TAB)
        logout.performSemanticsAction(SemanticsActions.RequestFocus)
        logout.assertIsFocused()
        pressKey(KeyEvent.KEYCODE_ENTER)
        val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Log out?")
        val cancel = compose.onNode(hasText("Cancel") and hasAnyAncestor(pane))
        val confirm = compose.onNode(hasText("Log out") and hasAnyAncestor(pane))
        cancel.assertIsFocused()
        pressKey(KeyEvent.KEYCODE_TAB)
        confirm.assertIsFocused()
        pressKey(KeyEvent.KEYCODE_BACK)
        compose.onNode(pane).assertDoesNotExist()
        awaitLogoutFocus()

        pressKey(KeyEvent.KEYCODE_ENTER)
        cancel.assertIsFocused()
        pressKey(KeyEvent.KEYCODE_ENTER)
        compose.onNode(pane).assertDoesNotExist()
        awaitLogoutFocus()
        compose.runOnIdle { assertEquals(0, logouts) }
    }

    @Test
    fun englishLogoutActionsRemainReachableAtDoubleFontScale() {
        var logouts = 0
        compose.withSystemFontScale(2f) {
            renderAccount(AppLanguage.English) { logouts++ }
            val logout = compose.onNodeWithText("Log out")
            logout.performScrollTo().performClick()
            val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Log out?")
            val cancel = compose.onNode(hasText("Cancel") and hasAnyAncestor(pane))
            val confirm = compose.onNode(hasText("Log out") and hasAnyAncestor(pane))
            compose.onNodeWithText(
                "Pin Glide will immediately stop forwarding on this device and try to revoke its " +
                    "service credentials. Device Pairings and local Pairing keys will be kept."
            ).performScrollTo().assertIsDisplayed()
            confirm.performScrollTo().assertIsDisplayed()
            cancel.performScrollTo().assertIsDisplayed().performClick()
            compose.onNode(pane).assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, logouts) }
            logout.performScrollTo().performClick()
            confirm.performScrollTo().assertIsDisplayed().performClick()
            compose.onNode(pane).assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, logouts) }
        }
    }

    private fun awaitLogoutFocus() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Log out") and isFocused()).fetchSemanticsNodes().size == 1
        }
    }

    private fun pressKey(keyCode: Int) =
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)

    private fun renderAccount(language: AppLanguage, onLogout: () -> Unit) {
        val app = compose.activity.application as PinGlideApplication
        val account = sampleAccount()
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
                        account = account,
                        senders = app.accountSenders,
                        revocation = app.senderRevocation,
                        onLogout = onLogout,
                        onDismiss = {}
                    )
                }
            }
        }
        val label = if (language == AppLanguage.English) "QQ account" else "QQ 账号"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithText("示例昵称").assertIsDisplayed()
    }

    private fun sampleAccount(): AccountSession {
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
        return AccountSession(
            store = object : LogoutStore {
                override suspend fun logout(): RefreshCredential? =
                    error("Unexpected storage logout")
            },
            renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope),
            service = object : AccountService {
                override suspend fun profile(identity: SenderIdentity) = "示例昵称"

                override suspend fun logout(credential: RefreshCredential) {
                    error("Unexpected service logout")
                }
            },
            scope = scope
        )
    }
}
