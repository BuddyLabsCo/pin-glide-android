package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import com.buddylabs.pinglide.ui.components.withSystemFontScale
import com.buddylabs.pinglide.ui.home.HomePairingStore
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SenderInventoryScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun close() = scope.cancel()

    @Test
    fun approvedInventoryShowsWarningAndGroupsLoadedSendersWithDangerActions() {
        openInventory(AppLanguage.SimplifiedChinese)
        val current = compose.onNodeWithTag("settings-group:本设备").assertIsDisplayed()
        val others = compose.onNodeWithTag("settings-group:其他发送端设备").assertIsDisplayed()
        val currentBounds = current.getUnclippedBoundsInRoot()
        val otherBounds = others.getUnclippedBoundsInRoot()
        assertEquals(60.dp, currentBounds.bottom - currentBounds.top)
        assertEquals(60.dp, otherBounds.bottom - otherBounds.top)
        assertEquals(currentBounds.left, otherBounds.left)
        assertEquals(currentBounds.right, otherBounds.right)
        assertTrue(otherBounds.top > currentBounds.bottom)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("小米 15", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(13.sp, layouts.single().layoutInput.style.fontSize)
        assertEquals(FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
        val revoke = compose.onNodeWithContentDescription("撤销小米 15").assertIsEnabled()
        val bounds = revoke.getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
        val image = revoke.captureToImage().asAndroidBitmap()
        assertEquals(
            ContextCompat.getColor(compose.activity, R.color.error_emblem_container),
            image.getPixel(image.width / 2, 2)
        )
        captureSettledUiEvidence(
            "sender-inventory.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("小米 15")))
        )
        revoke.performClick()
        compose.onNodeWithText("撤销「小米 15」？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("撤销Pixel 9").performClick()
        compose.onNodeWithText("撤销「Pixel 9」？").assertIsDisplayed()
    }

    @Test
    fun englishInventoryAtDoubleFontScaleKeepsRevokeAndBackReachable() =
        compose.withSystemFontScale(2f) {
            openInventory(AppLanguage.English)
            compose.onNodeWithText("This device").assertIsDisplayed()
            compose.onNodeWithContentDescription("Revoke Pixel 9")
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Revoke “Pixel 9”?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Back").assertIsDisplayed().performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
        }

    private fun openInventory(language: AppLanguage) {
        val app = compose.activity.application as PinGlideApplication
        val store = HomePairingStore(emptyList(), Instant.now().plusSeconds(3600).toString())
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val inventory = AccountSenders(
            store,
            renewal,
            { listOf(AccountSender("other", "Pixel 9"), AccountSender("sender", "小米 15")) },
            scope
        )
        val revocation = SenderRevocation(
            store,
            inventory,
            renewal,
            { error("Unexpected QQ authorization") },
            { _, _, _ -> error("Unexpected revocation") },
            scope
        )
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(language) {
                PinGlideTheme {
                    SettingsNavigation(
                        language, {}, { false }, "0.1.0", app.accountSession,
                        inventory, revocation, {}, {}
                    )
                }
            }
        }
        val label = if (language == AppLanguage.English) "Device access management" else "设备访问管理"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("小米 15").isDisplayed() }
    }
}
