package com.buddylabs.pinglide.ui.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountService
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.LogoutStore
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.home.HomePairingStore
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val responses = Channel<Result<String?>>(Channel.UNLIMITED)
    private var logoutCount = 0

    @After
    fun close() {
        scope.cancel()
        responses.close()
    }

    @Test
    fun profileShowsServiceNicknameAndKeepsLogoutConfirmation() {
        openAccount()
        compose.onNodeWithText("正在获取账号资料…").assertIsDisplayed()
        respond("示例昵称")
        compose.onNodeWithText("示例昵称").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        assertApprovedLayout()
        captureSettledUiEvidence(
            "settings-account.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("示例昵称")))
        )
        compose.onNodeWithText("退出登录").assertHasClickAction().performClick()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, logoutCount) }
        compose.onNodeWithText("退出登录").assertIsDisplayed().performClick()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNode(
            hasText("退出登录") and hasAnyAncestor(isDialog()) and
                hasAnyAncestor(hasAnyDescendant(hasText("退出登录？")))
        ).performClick()
        compose.runOnIdle { assertEquals(1, logoutCount) }
    }

    @Test
    fun profileFailureShowsFallbackAndSystemToast() {
        openAccount()
        compose.onNodeWithText("正在获取账号资料…").assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeAndWaitForEvent(
            {
                check(
                    responses.trySend(
                        Result.failure(IllegalStateException("Profile unavailable"))
                    ).isSuccess
                )
                compose.waitUntil(5_000) { compose.onNodeWithText("QQ 账号").isDisplayed() }
            },
            { event ->
                event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.text.any { it.toString() == "网络错误，请稍后重试" }
            },
            5_000L
        )
        compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        compose.onNodeWithText("正在获取账号资料…").assertDoesNotExist()
    }

    @Test
    fun longNicknameEllipsizesWithoutLosingAccessibleText() {
        openAccount()
        val nickname = "很长的服务昵称".repeat(20)
        respond(nickname)
        val name = compose.onNodeWithText(nickname).assertIsDisplayed().assertTextEquals(nickname)
        val layout = textLayout(nickname)
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))
        name.assertHasNoClickAction()
        val bounds = compose.onNodeWithTag("settings-account-profile").getUnclippedBoundsInRoot()
        assertDp("Long nickname card height", bounds.bottom - bounds.top, 76.dp)
    }

    @Test
    fun compactEnglishAccountAtDoubleFontScaleKeepsLogoutAndBackReachable() {
        openAccount(AppLanguage.English, compact = true)
        respond("Example nickname")
        compose.onNodeWithText("Example nickname").assertIsDisplayed()
        val back = compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        val logout = compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed()
        logout.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction().performClick()
        compose.onNodeWithText("Log out?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, logoutCount) }
        back.assertIsDisplayed().performClick()
        compose.onNodeWithText("QQ account").assertIsDisplayed().assertHasClickAction()
    }

    private fun openAccount(
        language: AppLanguage = AppLanguage.SimplifiedChinese,
        compact: Boolean = false
    ) {
        val app = compose.activity.application as PinGlideApplication
        val store = HomePairingStore(emptyList(), Instant.now().plusSeconds(3600).toString())
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val account = AccountSession(
            object : LogoutStore {
                override suspend fun logout(): RefreshCredential? = error("Unexpected logout")
            },
            renewal,
            object : AccountService {
                override suspend fun profile(identity: SenderIdentity): String? =
                    responses.receive().getOrThrow()
                override suspend fun logout(credential: RefreshCredential): Unit =
                    error("Unexpected service logout")
            },
            scope
        )
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val systemConfiguration = LocalConfiguration.current
            val fontScale = if (compact) 2f else 1f
            val configuration = remember(systemConfiguration) {
                Configuration(systemConfiguration).apply { this.fontScale = fontScale }
            }
            val density = LocalDensity.current.density * if (compact) 1.5f else 1f
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale)
            ) {
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
                            onLogout = { logoutCount++ },
                            onDismiss = {}
                        )
                    }
                }
            }
        }
        val label = if (language == AppLanguage.English) "QQ account" else "QQ 账号"
        compose.onNodeWithText(label).performClick()
    }

    private fun respond(nickname: String) {
        check(responses.trySend(Result.success(nickname)).isSuccess)
        compose.waitUntil(5_000) { compose.onNodeWithText(nickname).isDisplayed() }
    }

    private fun assertApprovedLayout() {
        val title = compose.onNodeWithText("设置").assertIsDisplayed().getUnclippedBoundsInRoot()
        val back = compose.onNodeWithContentDescription("返回").getUnclippedBoundsInRoot()
        val profile = compose.onNodeWithTag("settings-account-profile").assertIsDisplayed()
        val logout = compose.onNodeWithTag("settings-account-logout").assertIsDisplayed()
        val cardBounds = profile.getUnclippedBoundsInRoot()
        val logoutBounds = logout.getUnclippedBoundsInRoot()
        assertDp("Back width", back.right - back.left, 48.dp)
        assertDp("Header to card", cardBounds.top - (title.top + title.bottom) / 2, 42.dp)
        assertDp("Profile card height", cardBounds.bottom - cardBounds.top, 76.dp)
        assertDp("Logout card height", logoutBounds.bottom - logoutBounds.top, 60.dp)
        assertDp("Card gap", logoutBounds.top - cardBounds.bottom, 14.dp)
        assertEquals(cardBounds.left, logoutBounds.left)
        assertEquals(cardBounds.right, logoutBounds.right)
        val nicknameStyle = textLayout("示例昵称").layoutInput.style
        assertEquals(13.sp, nicknameStyle.fontSize)
        assertEquals(FontWeight.Bold, nicknameStyle.fontWeight)
        assertEquals(11.sp, textLayout("QQ 账号").layoutInput.style.fontSize)
        assertEquals(13.sp, textLayout("退出登录").layoutInput.style.fontSize)
        assertArtwork(
            profile.captureToImage().asAndroidBitmap(),
            logout.captureToImage().asAndroidBitmap()
        )
    }

    private fun assertArtwork(profile: Bitmap, logout: Bitmap) {
        val context = compose.activity
        val surface = ContextCompat.getColor(context, R.color.surface)
        val container = ContextCompat.getColor(context, R.color.surface_container)
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val danger = if (dark) 0xffffb4ab.toInt() else 0xffa33b34.toInt()
        for (image in listOf(profile, logout)) {
            assertEquals(surface, image.getPixel(0, 0))
            assertEquals(container, image.getPixel(4, image.height / 2))
        }
        assertTrue("Blue QQ badge", profile.hasColor(17, 24, 29, 47, 0xff0b7ac8.toInt()))
        assertTrue("First white Q", profile.hasColor(29, 38, 30, 46, Color.WHITE))
        assertTrue("Second white Q", profile.hasColor(38, 47, 30, 46, Color.WHITE))
        val width = with(compose.density) { logout.width.toDp().value.toInt() }
        assertTrue("Logout glyph", logout.hasColor(14, 34, 20, 40, danger))
        assertTrue("Logout label", logout.hasColor(58, 120, 20, 40, danger))
        assertTrue("Logout chevron", logout.hasColor(width - 34, width - 14, 20, 40, danger))
    }

    private fun Bitmap.hasColor(left: Int, right: Int, top: Int, bottom: Int, color: Int): Boolean =
        with(compose.density) {
            val columns = left.dp.roundToPx() until right.dp.roundToPx()
            val rows = top.dp.roundToPx() until bottom.dp.roundToPx()
            rows.any { row -> columns.any { column -> getPixel(column, row) == color } }
        }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertDp(label: String, actual: Dp, expected: Dp) {
        assertTrue("$label: $actual", abs((actual - expected).value) <= 2f)
    }
}
