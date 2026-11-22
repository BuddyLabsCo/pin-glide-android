package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRemovalDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun confirmationOverHomePlacesBothActionsOnOneRowAndCancelReturnsToDetails() {
        renderHome()
        openConfirmation("我的电脑", "移除这台设备的配对")
        dialogText("移除“我的电脑”？").assertIsDisplayed()
        dialogText("当前设备会停止向目标设备发送短信。目标设备和其他设备的配对不会被关闭。")
            .assertIsDisplayed()
        compose.onNodeWithText("添加设备").assertIsDisplayed()
        compose.onNodeWithText("移除这台设备的配对").assertDoesNotExist()

        val cancel = dialogText("取消").assertIsDisplayed()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        val confirmBounds = dialogText("移除配对").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(cancelBounds.top, confirmBounds.top)
        assertTrue(cancelBounds.right < confirmBounds.left)
        assertTrue(cancelBounds.bottom - cancelBounds.top >= 48.dp)
        assertTrue(confirmBounds.bottom - confirmBounds.top >= 48.dp)
        cancel.performClick()
        dialogText("移除“我的电脑”？").assertDoesNotExist()
        compose.onNodeWithText("移除这台设备的配对").assertIsDisplayed()
    }

    @Test
    fun pendingRemovalCannotBeDismissedAndFailureCanBeRetried() {
        val firstResponse = CompletableDeferred<Unit>()
        val requestedIds = mutableListOf<String>()
        renderHome(AppLanguage.English) { pairingId ->
            requestedIds += pairingId
            if (requestedIds.size == 1) {
                firstResponse.await()
                throw PairingServiceException(PairingFailure.Unavailable)
            }
        }
        openConfirmation("My computer", "Remove this target’s Pairing")
        dialogText("Remove Pairing").performClick()
        dialogText("Submitting, please wait").assertIsDisplayed()
        dialogText("Cancel").assertIsNotEnabled()
        dialogText("Remove Pairing").assertIsNotEnabled()
        pressBack()
        dialogText("Submitting, please wait").assertIsDisplayed()

        firstResponse.complete(Unit)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Retry removal")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Forwarding to this target is off", substring = true))
            .assertIsDisplayed()
        dialogText("Retry removal").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Pairing removed")).fetchSemanticsNodes().isNotEmpty()
        }
        dialogText("Retry removal").assertDoesNotExist()
        compose.onNodeWithText("My computer").assertDoesNotExist()
        compose.onNodeWithText("Office computer").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("home", "home"), requestedIds) }
    }

    private fun dialogText(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun openConfirmation(name: String, removeAction: String) {
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithText(removeAction).performClick()
    }

    private fun renderHome(
        language: AppLanguage = AppLanguage.SimplifiedChinese,
        remove: suspend (String) -> Unit = { error("Cancel must not remove a Pairing") }
    ) {
        val now = Instant.now()
        val chinese = language == AppLanguage.SimplifiedChinese
        val homeName = if (chinese) "我的电脑" else "My computer"
        val officeName = if (chinese) "办公室电脑" else "Office computer"
        val targets = listOf(
            RemotePairing("home", "receiver-home", homeName, Presence.Online, now.toString()),
            RemotePairing(
                "office",
                "receiver-office",
                officeName,
                Presence.Offline,
                now.minusSeconds(1_320).toString()
            )
        )
        val store = HomePairingStore(targets, now.plusSeconds(3600).toString())
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val remote = targets.associateBy { it.pairingId }.toMutableMap()
        val service = object : PairingService {
            override suspend fun list(managementToken: String) = remote.values.toList()
            override suspend fun remove(managementToken: String, pairingId: String) {
                remove(pairingId)
                checkNotNull(remote.remove(pairingId))
            }
        }
        val pairings = OwnPairings(store, service, renewal, scope)
        runBlocking(Dispatchers.Main) { pairings.refresh() }
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val app = compose.activity.application as PinGlideApplication
        val home = HomeViewModel(pairings, app.senderRestoration()) { null }
        compose.setContent {
            val state by home.state.collectAsState()
            LocalizedContent(language) {
                PinGlideTheme {
                    HomeScreen(
                        state,
                        smsGranted = true,
                        smsRecovery = SmsRecovery.Idle,
                        home = home,
                        onRestoreSms = {},
                        onAdd = {},
                        onSettings = {}
                    )
                }
            }
        }
    }
}
