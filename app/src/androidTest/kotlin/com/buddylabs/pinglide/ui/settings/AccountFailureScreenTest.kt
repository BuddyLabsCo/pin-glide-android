package com.buddylabs.pinglide.ui.settings

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountFailureScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixture = AccountScreenFixture(compose)
    private val chineseError = "网络错误，请稍后重试"
    private val englishError = "Network error. Try again later."

    @After
    fun close() = fixture.close()

    @Test
    fun failedProfileShowsOneAccessibleCapsuleAtApprovedPositionUntilTimeout() {
        fixture.openAccount()
        compose.onNodeWithText("正在获取账号资料…").assertIsDisplayed()
        val nativeToasts = AtomicInteger()
        val packageName = compose.activity.packageName
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.className?.toString() == "android.widget.Toast" &&
                event.packageName?.toString() == packageName
            ) {
                nativeToasts.incrementAndGet()
            }
        }
        try {
            fixture.fail(chineseError)
            assertApprovedFailureLayout()
            captureSettledUiEvidence(
                "settings-account-error.png",
                compose.onNodeWithText(chineseError)
            )
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText(chineseError).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1_100)
            compose.onNodeWithText(chineseError).assertDoesNotExist()
            assertEquals("No duplicate native Toast", 0, nativeToasts.get())
            compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        } finally {
            automation.setOnAccessibilityEventListener(null)
        }
    }

    @Test
    fun compactEnglishFailureAtDoubleFontScaleKeepsNoticeAndActionsReachable() =
        compose.withAccountViewport(366, 260) {
            fixture.openAccount(AppLanguage.English)
            fixture.fail(englishError)
            val notice = compose.onNodeWithText(englishError).assertIsDisplayed()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite
                    )
                )
            val bounds = notice.getUnclippedBoundsInRoot()
            val profile = compose.onNodeWithTag(
                "settings-account-profile"
            ).getUnclippedBoundsInRoot()
            val root = compose.onNode(isRoot() and hasAnyDescendant(hasText(englishError)))
                .getUnclippedBoundsInRoot()
            assertEquals(2f, compose.textLayout(englishError).layoutInput.density.fontScale)
            assertTrue(
                "Notice fits in the viewport",
                bounds.top >= root.top && bounds.bottom <= root.bottom
            )
            assertTrue("Notice does not cover the account card", bounds.bottom <= profile.top)
            captureSettledUiEvidence("settings-account-error-compact.png", notice)
            val logout = compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed()
            logout.assertHasClickAction().performClick()
            compose.onNodeWithText("Log out?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(0, fixture.logoutCount) }
            compose.onNodeWithText(englishError).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(
                "Back"
            ).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("QQ account").performScrollTo().assertHasClickAction()
        }

    @Test
    fun veryShortWindowKeepsFailureVisibleAndBackReachable() =
        compose.withAccountViewport(366, 164) {
            fixture.openAccount(AppLanguage.English)
            fixture.fail(englishError)
            val notice = compose.onNodeWithText(englishError).assertIsDisplayed()
            val bounds = notice.getUnclippedBoundsInRoot()
            val root = compose.onNode(isRoot() and hasAnyDescendant(hasText(englishError)))
                .getUnclippedBoundsInRoot()
            assertTrue(
                "Complete notice is inside the viewport",
                bounds.top >= root.top && bounds.bottom <= root.bottom
            )
            val back = compose.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
            assertTrue(
                "Notice does not cover Back",
                bounds.bottom <= back.top || bounds.top >= back.bottom
            )
            captureSettledUiEvidence("settings-account-error-short.png", notice)
            compose.onNodeWithText("Log out").performScrollTo().assertHasClickAction()
            compose.onNodeWithContentDescription(
                "Back"
            ).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("QQ account").performScrollTo().assertHasClickAction()
        }

    @Test
    fun failureArrivingAfterScrollingToLogoutBringsNoticeIntoView() =
        compose.withAccountViewport(366, 164) {
            fixture.openAccount(AppLanguage.English)
            compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed()
            assertTrue(
                "Header was scrolled out of view",
                !compose.onNodeWithContentDescription("Back").isDisplayed()
            )
            fixture.fail(englishError)
            val notice = compose.onNodeWithText(englishError).assertIsDisplayed()
            val full = notice.getUnclippedBoundsInRoot()
            val clipped = notice.fetchSemanticsNode().boundsInRoot
            assertDp(
                "Full notice height",
                with(compose.density) { clipped.height.toDp() },
                full.bottom - full.top
            )
            notice.assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            compose.onNodeWithText("Log out").performScrollTo().assertHasClickAction()
        }

    @Test
    fun retainedReadFailingDuringRestorationShowsNewNoticeOnce() {
        val restoration = StateRestorationTester(compose)
        fixture.openAccount(restoration = restoration)
        compose.onNodeWithText("正在获取账号资料…").assertIsDisplayed()
        fixture.failDuringRestoration()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) { compose.onNodeWithText(chineseError).isDisplayed() }
        compose.onNodeWithText(chineseError).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        compose.onAllNodes(hasText(chineseError)).assertCountEquals(1)
        compose.runOnIdle { assertEquals(1, fixture.readCount) }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText(chineseError).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText(chineseError).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(chineseError).assertDoesNotExist()
    }

    @Test
    fun restoredFailureUsesRemainingTimeoutAndDoesNotResurrectAfterDismissal() {
        val restoration = StateRestorationTester(compose)
        fixture.openAccount(restoration = restoration)
        fixture.fail(chineseError)
        SystemClock.sleep(1_200)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(chineseError).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, fixture.readCount) }
        compose.mainClock.advanceTimeBy(3_100)
        compose.onNodeWithText(chineseError).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(chineseError).assertDoesNotExist()
        compose.onNodeWithText("QQ 账号").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, fixture.readCount) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("QQ 账号").performClick()
        fixture.fail(chineseError)
        compose.runOnIdle { assertEquals(2, fixture.readCount) }
        compose.onNodeWithText(chineseError).assertIsDisplayed()
    }

    private fun assertApprovedFailureLayout() {
        compose.onNodeWithText("正在获取账号资料…").assertDoesNotExist()
        compose.onNodeWithText("QQ 账号").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("退出登录").assertHasClickAction()
        AccountLayoutAssertions(compose).assertApprovedLayout()
        val notice = compose.onNodeWithText(chineseError).assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        compose.onAllNodes(hasText(chineseError)).assertCountEquals(1)
        val bounds = notice.getUnclippedBoundsInRoot()
        val root = compose.onNode(isRoot() and hasAnyDescendant(hasText(chineseError)))
            .getUnclippedBoundsInRoot()
        assertDp("Centered capsule", (bounds.left + bounds.right) / 2, (root.left + root.right) / 2)
        assertDp("Viewport bottom offset", root.bottom - bounds.bottom, 164.dp)
        assertDp("Capsule height", bounds.bottom - bounds.top, 42.dp)
        val style = compose.textLayout(chineseError).layoutInput.style
        assertEquals(12.sp, style.fontSize)
        assertEquals(FontWeight(650), style.fontWeight)
        assertEquals(TextAlign.Center, style.textAlign)
        assertEquals(Color(ContextCompat.getColor(compose.activity, R.color.surface)), style.color)
        val image = notice.captureToImage().asAndroidBitmap()
        val dark = ContextCompat.getColor(compose.activity, R.color.surface) ==
            0xff0b1715.toInt()
        val expected = if (dark) 0xffc8d4cf.toInt() else 0xff2d3b38.toInt()
        val actual = image[image.width / 2, 4]
        for (shift in listOf(0, 8, 16)) {
            assertTrue(
                "Capsule tint",
                abs(((actual shr shift) and 255) - ((expected shr shift) and 255)) <= 9
            )
        }
    }
}
