package com.buddylabs.pinglide.ui.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
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
import com.buddylabs.pinglide.ui.home.runHomeTestShell
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt
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
        captureSettledUiEvidence(
            "settings-account.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("示例昵称")))
        )
        assertApprovedLayout()
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
    fun compactEnglishAccountAtDoubleFontScaleKeepsLogoutAndBackReachable() = withCompactViewport {
        openAccount(AppLanguage.English)
        respond("Example nickname")
        compose.onNodeWithText("Example nickname").assertIsDisplayed()
        assertEquals(2f, textLayout("Example nickname").layoutInput.density.fontScale)
        val card = compose.onNodeWithTag("settings-account-profile").getUnclippedBoundsInRoot()
        assertDp("Actual dialog usable width", card.right - card.left, 204.dp)
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

    private fun withCompactViewport(check: () -> Unit) {
        val originalSize = Regex("Override size: (\\d+x\\d+)")
            .find(runHomeTestShell("wm size"))?.groupValues?.get(1)
        val originalFont = runHomeTestShell("settings get system font_scale").trim()
        val originalConfiguration = Configuration(compose.activity.resources.configuration)
        val density = compose.activity.resources.displayMetrics.density
        try {
            runHomeTestShell("settings put system font_scale 2.0")
            runHomeTestShell(
                "wm size ${(244 * density).roundToInt()}x${(546 * density).roundToInt()}"
            )
            compose.waitUntil(5_000) {
                compose.runOnUiThread {
                    val configuration = compose.activity.resources.configuration
                    configuration.screenWidthDp <= 244 && configuration.fontScale == 2f
                }
            }
            check()
        } finally {
            runHomeTestShell("wm size ${originalSize ?: "reset"}")
            if (originalFont == "null") {
                runHomeTestShell("settings delete system font_scale")
            } else {
                runHomeTestShell("settings put system font_scale $originalFont")
            }
            compose.waitUntil(5_000) {
                compose.runOnUiThread {
                    val configuration = compose.activity.resources.configuration
                    configuration.screenWidthDp == originalConfiguration.screenWidthDp &&
                        configuration.fontScale == originalConfiguration.fontScale
                }
            }
        }
    }

    private fun openAccount(language: AppLanguage = AppLanguage.SimplifiedChinese) {
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
        val blue = 0xff0b7ac8.toInt()
        assertTrue("Blue QQ badge", profile.hasColor(17, 24, 29, 47, blue))
        assertTrue("First white Q", profile.hasColor(29, 38, 30, 46, Color.WHITE))
        assertTrue("Second white Q", profile.hasColor(38, 47, 30, 46, Color.WHITE))
        assertGlyph(profile, 28, 29, blue, Color.WHITE, AccountGlyph.Qq)
        val width = with(compose.density) { logout.width.toDp().value.toInt() }
        assertTrue("Logout glyph", logout.hasColor(14, 34, 20, 40, danger))
        assertTrue("Logout label", logout.hasColor(58, 120, 20, 40, danger))
        assertTrue("Logout chevron", logout.hasColor(width - 34, width - 14, 20, 40, danger))
        assertGlyph(logout, 14, 20, container, danger, AccountGlyph.Logout)
        assertGlyph(logout, width - 34, 20, container, danger, AccountGlyph.Chevron)
    }

    private fun assertGlyph(
        image: Bitmap,
        left: Int,
        top: Int,
        background: Int,
        tint: Int,
        glyph: AccountGlyph
    ) {
        val shifts = (-1..1).flatMap { offsetY -> (-1..1).map { offsetX -> offsetX to offsetY } }
        val distances = listOf(glyph.mask, glyph.alternateMask).flatMap { reference ->
            val mask = reference.trimIndent().lines()
            shifts.map { (offsetX, offsetY) ->
                mask.indices.sumOf { row ->
                    val weight = if (glyph == AccountGlyph.Qq && row in 13..14) 5 else 1
                    mask[row].indices.count { column ->
                        image.hasGlyphInk(
                            left + column + offsetX,
                            top + row + offsetY,
                            background,
                            tint
                        ) != (mask[row][column] == '#')
                    } * weight
                }
            }
        }
        assertTrue(
            "${glyph.name} glyph mask: ${distances.min()}",
            distances.min() <= glyph.tolerance
        )
    }

    private fun Bitmap.hasGlyphInk(x: Int, y: Int, background: Int, tint: Int): Boolean =
        with(compose.density) {
            val columns = x.dp.roundToPx() until (x + 1).dp.roundToPx()
            val rows = y.dp.roundToPx() until (y + 1).dp.roundToPx()
            val ink = rows.sumOf { row ->
                columns.count { column ->
                    val pixel = Color.red(getPixel(column, row))
                    abs(pixel - Color.red(tint)) < abs(pixel - Color.red(background))
                }
            }
            ink * 2 >= columns.count() * rows.count()
        }

    private enum class AccountGlyph(
        val mask: String,
        val tolerance: Int,
        val alternateMask: String
    ) {
        Qq(
            """
            ....................
            ....................
            ....................
            ....................
            ....###......###....
            ..######...######...
            ..##..###..##..###..
            .###...##.###...##..
            .###...##.###...##..
            .###...##.###...##..
            .###..###.###..###..
            ..######...######...
            ...#####....#####...
            ......###......###..
            .......#........#...
            ....................
            ....................
            ....................
            """,
            18,
            """
            ....................
            ....................
            ....................
            ....................
            ....###.....####....
            ...#####...######...
            ..##...##.##...##...
            ..##...##.##...###..
            ..##...##.##....##..
            ..##...##.##...###..
            ..##...##.##...##...
            ...#####...######...
            ....####....####....
            .......##......##...
            ....................
            ....................
            ....................
            ....................
            """
        ),
        Logout(
            """
            ....................
            ..########..........
            .#########..........
            .#########..........
            .###....##..........
            .###....##..........
            .###........#.......
            .###........##......
            .###...########.....
            .###..#########.....
            .###........##......
            .###........#.......
            .###................
            .###................
            .###....##..........
            .###....##..........
            .#########..........
            .#########..........
            ..########..........
            ....................
            """,
            15,
            """
            ....................
            ....................
            ..########..........
            ..########..........
            ..##....##..........
            ..##....##..........
            ..##................
            ..##.........#......
            ..##...########.....
            ..##...#########....
            ..##...########.....
            ..##.........#......
            ..##................
            ..##................
            ..##.....#..........
            ..##....##..........
            ..##....##..........
            ..########..........
            ..########..........
            ....................
            """
        ),
        Chevron(
            """
            ....................
            ....................
            ....................
            ....................
            ....................
            .......##...........
            .......###..........
            ........###.........
            .........###........
            .........###........
            ........###.........
            .......###..........
            .......##...........
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            """,
            6,
            """
            ....................
            ....................
            ....................
            ....................
            ....................
            ........#...........
            ........##..........
            ........###.........
            .........###........
            ..........###.......
            .........###........
            ........##..........
            .......##...........
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            ....................
            """
        )
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
