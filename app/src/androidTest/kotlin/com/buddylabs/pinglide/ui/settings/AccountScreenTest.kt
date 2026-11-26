package com.buddylabs.pinglide.ui.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withCompactViewport
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixture = AccountScreenFixture(compose)

    @After
    fun close() {
        fixture.close()
    }

    @Test
    fun profileShowsServiceNicknameAndKeepsLogoutConfirmation() {
        fixture.openAccount()
        AccountLayoutAssertions(compose).assertApprovedLayout("正在获取账号资料…")
        compose.capturePendingAccountAndOpenThenCancelLogout()
        compose.runOnIdle { assertEquals(0, fixture.logoutCount) }
        fixture.respond("示例昵称")
        compose.onNodeWithText("示例昵称").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        captureSettledUiEvidence(
            "settings-account.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("示例昵称")))
        )
        AccountLayoutAssertions(compose).assertApprovedLayout("示例昵称")
        assertEquals(11.sp, compose.textLayout("QQ 账号").layoutInput.style.fontSize)
        compose.onNodeWithText("退出登录").assertHasClickAction().performClick()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(0, fixture.logoutCount) }
        compose.onNodeWithText("退出登录").assertIsDisplayed().performClick()
        compose.onNodeWithText("退出登录？").assertIsDisplayed()
        compose.onNode(
            hasText("退出登录") and hasAnyAncestor(isDialog()) and
                hasAnyAncestor(hasAnyDescendant(hasText("退出登录？")))
        ).performClick()
        compose.runOnIdle { assertEquals(1, fixture.logoutCount) }
    }

    @Test
    fun longNicknameEllipsizesWithoutLosingAccessibleText() {
        fixture.openAccount()
        val nickname = "很长的服务昵称".repeat(20)
        fixture.respond(nickname)
        val name = compose.onNodeWithText(nickname).assertIsDisplayed().assertTextEquals(nickname)
        val layout = compose.textLayout(nickname)
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))
        name.assertHasNoClickAction()
        val bounds = compose.onNodeWithTag("settings-account-profile").getUnclippedBoundsInRoot()
        assertDp("Long nickname card height", bounds.bottom - bounds.top, 76.dp)
    }

    @Test
    fun compactEnglishAccountAtDoubleFontScaleKeepsLogoutAndBackReachable() =
        compose.withCompactViewport(244, 546) {
            fixture.openAccount(AppLanguage.English)
            val loadingLabel = "Loading account information…"
            compose.onNodeWithText(loadingLabel).assertIsDisplayed()
            val loadingLayout = compose.textLayout(loadingLabel)
            assertEquals(2f, loadingLayout.layoutInput.density.fontScale)
            assertFalse(loadingLayout.hasVisualOverflow)
            assertTrue((0 until loadingLayout.lineCount).none(loadingLayout::isLineEllipsized))
            assertEquals(
                loadingLabel.length,
                loadingLayout.getLineEnd(loadingLayout.lineCount - 1, visibleEnd = true)
            )
            fixture.respond("Example nickname")
            compose.onNodeWithText("Example nickname").assertIsDisplayed()
            assertEquals(2f, compose.textLayout("Example nickname").layoutInput.density.fontScale)
            val card = compose.onNodeWithTag("settings-account-profile").getUnclippedBoundsInRoot()
            assertDp("Actual dialog usable width", card.right - card.left, 204.dp)
            val back = compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            val logout = compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed()
            logout.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHasClickAction().performClick()
            compose.onNodeWithText("Log out?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle { assertEquals(0, fixture.logoutCount) }
            back.assertIsDisplayed().performClick()
            compose.onNodeWithText("QQ account").assertIsDisplayed().assertHasClickAction()
        }
}

internal class AccountLayoutAssertions(
    private val compose: AndroidComposeTestRule<*, ComponentActivity>
) {
    fun assertApprovedLayout(profileLabel: String = "QQ 账号") {
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
        assertEquals(cardBounds.left to cardBounds.right, logoutBounds.left to logoutBounds.right)
        val profileStyle = compose.textLayout(profileLabel).layoutInput.style
        assertEquals(13.sp, profileStyle.fontSize)
        assertEquals(FontWeight.Bold, profileStyle.fontWeight)
        assertEquals(13.sp, compose.textLayout("退出登录").layoutInput.style.fontSize)
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
}
