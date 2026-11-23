package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeCapacityScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val removedPairingIds = mutableListOf<String>()

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun removingAPairingRestoresAddDevice() {
        openHome(Configuration.UI_MODE_NIGHT_NO)
        assertFull(Color(0xFFB45309))
        val target = "客厅设备"
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText(target))
        compose.onNodeWithText(target).performClick()
        compose.onNodeWithText("移除这台设备的配对").performClick()
        compose.onNodeWithText("移除配对").performClick()

        val notice = compose.onNodeWithText("配对已移除")
            .assertIsDisplayed()
            .assert(!hasAnyAncestor(hasScrollAction()))
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        assertTrue(
            "Removal notice is below the device list",
            list.getUnclippedBoundsInRoot().bottom <= notice.getUnclippedBoundsInRoot().top
        )
        compose.onNodeWithText("添加设备").assertIsDisplayed().assertHasClickAction()
        list.performScrollToNode(hasText("4 台"))
        compose.onNodeWithText("4 台").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("pairing-4"), removedPairingIds) }
    }

    @Test
    fun deviceDetailsGroupsActionsInPrototypeOrder() {
        openHome(Configuration.UI_MODE_NIGHT_NO)
        val target = compose.onNodeWithText("我的电脑")
        target.performClick()
        val inSheet = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
        val dragHandle = compose.onNode(hasContentDescription("拖动手柄") and inSheet)
            .assertIsDisplayed().assertHasClickAction()
        val receive = compose.onNode(hasContentDescription("我的电脑接收短信") and inSheet)
            .assertIsDisplayed().assertHasClickAction().assertIsOn()
        val actions = listOf("发送测试消息", "重命名设备", "移除这台设备的配对", "关闭")
            .map { compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction() }
        val controls = listOf(receive) + actions
        val minimumTouchSize = with(compose.density) { 48.dp.toPx() }
        (listOf(dragHandle) + controls).forEach { control ->
            val touchBounds = control.fetchSemanticsNode().touchBoundsInRoot
            assertTrue("Touch width is at least 48 dp", touchBounds.width >= minimumTouchSize)
            assertTrue("Touch height is at least 48 dp", touchBounds.height >= minimumTouchSize)
        }
        val bounds = controls.map { it.getUnclippedBoundsInRoot() }
        assertTrue(
            "Receive, test, rename, remove, and close appear in that order",
            bounds.zipWithNext().all { (above, below) -> above.bottom <= below.top }
        )
        actions.last().performClick().assertDoesNotExist()
        target.assertIsDisplayed()
    }

    @Test
    fun fullHomeUsesDarkCapacityColor() {
        openHome(Configuration.UI_MODE_NIGHT_YES)
        assertFull(Color(0xFFFFCA68))
    }

    private fun assertFull(expectedColor: Color) {
        val count = compose.onNodeWithText("已满5台").assertIsDisplayed()
        compose.onNodeWithText("添加设备").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        count.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(expectedColor, layouts.single().layoutInput.style.color)
    }

    private fun openHome(nightMode: Int) {
        val now = Instant.now()
        val targets = listOf("我的电脑", "办公室电脑", "书房设备", "备用设备", "客厅设备")
            .mapIndexed { index, name ->
                val changedAt = if (index == 1) now.minusSeconds(150) else now
                RemotePairing(
                    "pairing-$index",
                    "receiver-$index",
                    name,
                    Presence.Online,
                    changedAt.toString()
                )
            }
        renderReadyHome(compose, targets, scope, nightMode = nightMode) { pairingId ->
            removedPairingIds += pairingId
        }
    }
}

internal class HomePairingStore(
    targets: List<RemotePairing>,
    expiresAt: String,
    private var listed: Boolean = true,
    missingKeyId: String? = null
) : IdentityStore,
    PairingStore {
    private val authority = IdentitySnapshot(
        generation = 1,
        identity = SenderIdentity(
            "account",
            "sender",
            ManagementCredential("management", expiresAt),
            UploadCredential("upload", expiresAt),
            RefreshCredential("refresh", expiresAt)
        )
    )
    private var revision = 0L
    private val pairings = targets.associate { target ->
        target.pairingId to OwnPairing(
            target.pairingId,
            target.receiverId,
            target.alias,
            key = if (target.pairingId == missingKeyId) null else ByteArray(32)
        )
    }.toMutableMap()

    override suspend fun read() = authority

    override suspend fun readPairings() =
        PairingSnapshot(authority, revision, listed, pairings.values.toList())

    override suspend fun reconcile(expected: PairingSnapshot, items: List<RemotePairing>): Boolean {
        val aliases = items.associate { it.pairingId to it.alias }
        pairings.keys.retainAll(aliases.keys)
        pairings.replaceAll { pairingId, pairing ->
            pairing.copy(alias = aliases.getValue(pairingId))
        }
        listed = true
        revision++
        return true
    }

    override suspend fun select(
        expected: PairingSnapshot,
        pairingId: String,
        enabled: Boolean
    ): Boolean {
        val pairing = pairings.getValue(pairingId)
        pairings[pairingId] = pairing.copy(enabled = enabled)
        revision++
        return true
    }

    override suspend fun accept(
        expectedGeneration: Long,
        identity: SenderIdentity,
        attempt: LoginAttempt
    ): Boolean = error("Unexpected login")

    override suspend fun updateRenewal(
        expected: IdentitySnapshot,
        replacement: IdentitySnapshot,
        attempt: LoginAttempt
    ): Boolean = error("Unexpected renewal")
}
