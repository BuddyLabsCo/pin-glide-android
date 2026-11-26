package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlinx.coroutines.CompletableDeferred
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
    private val response = CompletableDeferred<List<AccountSender>>()

    @After
    fun close() = scope.cancel()

    @Test
    fun initialFailureShowsWarningAndFullWidthTonalRetryBeforeSuccessfulInventory() {
        var attempts = 0
        val initialResponse = CompletableDeferred<List<AccountSender>>()
        openInventory(AppLanguage.SimplifiedChinese, load = false, list = {
            if (attempts++ == 0) initialResponse.await() else response.await()
        })
        compose.onNodeWithText("正在获取设备列表…").assertIsDisplayed()
        check(initialResponse.completeExceptionally(IllegalStateException("Network unavailable")))
        val message = "网络错误，无法获取设备列表，请稍后重试。"
        compose.waitUntil(5_000) { compose.onNodeWithText(message).isDisplayed() }
        compose.onNodeWithText("本设备").assertDoesNotExist()
        compose.onNodeWithText("暂无其他发送端设备").assertDoesNotExist()
        val warning = compose.onNodeWithTag("sender-inventory-error").assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        val retry = compose.onNodeWithText("重试").assertIsDisplayed().assertIsEnabled()
        val warningBounds = warning.getUnclippedBoundsInRoot()
        val retryBounds = retry.getUnclippedBoundsInRoot()
        assertEquals(warningBounds.left, retryBounds.left)
        assertEquals(warningBounds.right, retryBounds.right)
        assertTrue(retryBounds.bottom - retryBounds.top >= 48.dp)
        val warningImage = warning.captureToImage().asAndroidBitmap()
        val retryImage = retry.captureToImage().asAndroidBitmap()
        assertEquals(
            ContextCompat.getColor(compose.activity, R.color.setup_warning_container),
            warningImage.getPixel(warningImage.width / 2, 2)
        )
        assertEquals(
            ContextCompat.getColor(compose.activity, R.color.secondary_container),
            retryImage.getPixel(retryImage.width / 2, 10)
        )
        captureSettledUiEvidence(
            "sender-inventory-error.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText(message)))
        )
        retry.performClick()
        compose.onNodeWithText("正在获取设备列表…").assertIsDisplayed()
        respond(listOf(AccountSender("sender", "小米 15")))
        compose.waitUntil(5_000) { compose.onNodeWithText("小米 15").isDisplayed() }
        warning.assertDoesNotExist()
        retry.assertDoesNotExist()
        compose.onNodeWithText("暂无其他发送端设备").assertIsDisplayed()
    }

    @Test
    fun refreshFailureRetainsSendersButDisablesRevocationUntilRetrySucceeds() {
        val refreshResponse = CompletableDeferred<List<AccountSender>>()
        var attempts = 0
        val inventory = openInventory(AppLanguage.SimplifiedChinese, list = {
            when (attempts++) {
                0 -> response.await()
                1 -> refreshResponse.await()
                else -> listOf(AccountSender("sender", "小米 15"), AccountSender("other", "Pixel 10"))
            }
        })
        val current = compose.onNodeWithContentDescription("撤销小米 15").assertIsEnabled()
        val other = compose.onNodeWithContentDescription("撤销Pixel 9").assertIsEnabled()
        compose.runOnIdle { inventory.refresh() }
        compose.onNodeWithTag("sender-inventory-loading").assertIsDisplayed()
        check(refreshResponse.completeExceptionally(IllegalStateException("Network unavailable")))
        val message = "网络错误，无法获取设备列表，请稍后重试。"
        compose.waitUntil(5_000) { compose.onNodeWithText(message).isDisplayed() }
        val warning = compose.onNodeWithTag("sender-inventory-error").assertIsDisplayed()
        val retry = compose.onNodeWithText("重试").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("小米 15").assertIsDisplayed()
        compose.onNodeWithText("Pixel 9").assertIsDisplayed()
        current.assertIsNotEnabled()
        other.assertIsNotEnabled()
        retry.performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("Pixel 10").isDisplayed() }
        warning.assertDoesNotExist()
        retry.assertDoesNotExist()
        other.assertDoesNotExist()
        current.assertIsEnabled()
        compose.onNodeWithContentDescription("撤销Pixel 10").assertIsEnabled()
    }

    @Test
    fun englishInitialFailureAtDoubleFontScaleKeepsWarningAndRetryReachable() =
        compose.withSystemFontScale(2f) {
            val initialResponse = CompletableDeferred<List<AccountSender>>()
            var attempts = 0
            openInventory(AppLanguage.English, load = false, list = {
                if (attempts++ == 0) initialResponse.await() else response.await()
            })
            compose.onNodeWithTag("sender-inventory-loading").performScrollTo().assertIsDisplayed()
            check(
                initialResponse.completeExceptionally(IllegalStateException("Network unavailable"))
            )
            val message = "Could not get the device list. Check your connection and try again."
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
            }
            val warning = compose.onNodeWithTag("sender-inventory-error").performScrollTo()
                .assertIsDisplayed().assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite
                    )
                )
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(message, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(!layouts.single().hasVisualOverflow)
            val retry = compose.onNodeWithText("Retry").performScrollTo()
                .assertIsDisplayed().assertIsEnabled()
            val warningBounds = warning.getUnclippedBoundsInRoot()
            val retryBounds = retry.getUnclippedBoundsInRoot()
            assertEquals(warningBounds.left, retryBounds.left)
            assertEquals(warningBounds.right, retryBounds.right)
            assertTrue(retryBounds.bottom - retryBounds.top >= 48.dp)
            compose.onNodeWithText("This device").assertDoesNotExist()
            retry.performClick()
            respond(listOf(AccountSender("sender", "小米 15")))
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("小米 15").fetchSemanticsNodes().isNotEmpty()
            }
            warning.assertDoesNotExist()
            retry.assertDoesNotExist()
            compose.onNodeWithContentDescription("Revoke 小米 15")
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
        }

    @Test
    fun initialLoadingShowsApprovedStatusCardWithoutSenderRowsUntilResponse() {
        openInventory(AppLanguage.SimplifiedChinese, load = false)
        compose.onNodeWithText("正在获取设备列表…").assertIsDisplayed()
        compose.onNodeWithText("本设备").assertDoesNotExist()
        compose.onNodeWithText("其他发送端设备").assertDoesNotExist()
        captureSettledUiEvidence(
            "sender-inventory-loading.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("正在获取设备列表…")))
        )
        val card = compose.onNodeWithTag("sender-inventory-loading").assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        val bounds = card.getUnclippedBoundsInRoot()
        assertEquals(45.dp, bounds.bottom - bounds.top)
        val image = card.captureToImage().asAndroidBitmap()
        assertEquals(
            ContextCompat.getColor(compose.activity, R.color.surface_container),
            image.getPixel(image.width / 2, 2)
        )
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("正在获取设备列表…", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(12.sp, layouts.single().layoutInput.style.fontSize)
        assertEquals(18.sp, layouts.single().layoutInput.style.lineHeight)
        respond(listOf(AccountSender("sender", "小米 15")))
        compose.waitUntil(5_000) { compose.onNodeWithText("小米 15").isDisplayed() }
        card.assertDoesNotExist()
        compose.onNodeWithTag("settings-group:本设备").assertIsDisplayed()
        compose.onNodeWithTag("settings-group:其他发送端设备").assertIsDisplayed()
        compose.onNodeWithText("暂无其他发送端设备").assertIsDisplayed()
    }

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
    fun emptyOtherSendersMatchApprovedLayoutAndKeepCurrentDeviceRevocationAvailable() {
        openInventory(AppLanguage.SimplifiedChinese, listOf(AccountSender("sender", "小米 15")))
        compose.onNodeWithText(
            "撤销需要验证当前 QQ 账号。所选发送端设备的注册和配对将被永久移除，其他发送端设备不受影响。"
        ).assertIsDisplayed()
        val current = compose.onNodeWithTag("settings-group:本设备").getUnclippedBoundsInRoot()
        val others = compose.onNodeWithTag("settings-group:其他发送端设备")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val empty = compose.onNodeWithText("暂无其他发送端设备").assertIsDisplayed()
        captureSettledUiEvidence(
            "sender-inventory-empty.png",
            compose.onNode(isRoot() and hasAnyDescendant(hasText("暂无其他发送端设备")))
        )
        assertEquals(60.dp, current.bottom - current.top)
        assertEquals(54.dp, others.bottom - others.top)
        assertEquals(current.left, others.left)
        assertEquals(current.right, others.right)
        assertTrue(others.top > current.bottom)
        val textBounds = empty.getUnclippedBoundsInRoot()
        assertEquals(18.dp, textBounds.left - others.left)
        assertEquals(18.dp, textBounds.top - others.top)
        val layouts = mutableListOf<TextLayoutResult>()
        empty.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(12.sp, style.fontSize)
        assertEquals(18.sp, style.lineHeight)
        assertEquals(
            Color(ContextCompat.getColor(compose.activity, R.color.on_surface_variant)),
            style.color
        )
        compose.onNodeWithContentDescription("撤销小米 15")
            .assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("撤销「小米 15」？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("撤销小米 15").assertIsEnabled()
    }

    @Test
    fun englishEmptyInventoryAtDoubleFontScaleKeepsCurrentDeviceRevocationReachable() =
        compose.withSystemFontScale(2f) {
            openInventory(AppLanguage.English, listOf(AccountSender("sender", "小米 15")))
            compose.onNodeWithText("No other Sender devices").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Revoke 小米 15")
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Revoke “小米 15”?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
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

    private fun respond(senders: List<AccountSender>) {
        check(response.complete(senders))
    }

    private fun openInventory(
        language: AppLanguage,
        senders: List<AccountSender> = listOf(
            AccountSender("other", "Pixel 9"),
            AccountSender("sender", "小米 15")
        ),
        load: Boolean = true,
        list: suspend () -> List<AccountSender> = { response.await() }
    ): AccountSenders {
        if (load) respond(senders)
        val app = compose.activity.application as PinGlideApplication
        val store = HomePairingStore(emptyList(), Instant.now().plusSeconds(3600).toString())
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val inventory = AccountSenders(
            store,
            renewal,
            { list() },
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
        if (load) compose.waitUntil(5_000) { compose.onNodeWithText("小米 15").isDisplayed() }
        return inventory
    }
}
