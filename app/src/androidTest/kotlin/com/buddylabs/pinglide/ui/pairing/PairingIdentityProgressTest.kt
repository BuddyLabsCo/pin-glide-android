package com.buddylabs.pinglide.ui.pairing

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingActivation
import com.buddylabs.pinglide.domain.pairing.PairingConnection
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingEvent
import com.buddylabs.pinglide.domain.pairing.PairingHandshake
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.pairing.PairingQr
import com.buddylabs.pinglide.domain.pairing.PairingSession
import com.buddylabs.pinglide.domain.pairing.PairingSessionService
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStage
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.home.HomePairingStore
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PairingIdentityProgressTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val generationStarted = CountDownLatch(1)
    private val finishGeneration = CountDownLatch(1)
    private val initSent = AtomicBoolean(false)
    private val inputIntact = AtomicBoolean(false)
    private val handshakeClosed = AtomicBoolean(false)
    private var successfulHandshakeEnabled = false
    private val completionRequested = CompletableDeferred<Unit>()
    private val finishCompletion = CompletableDeferred<Unit>()
    private val inventoryRequested = CompletableDeferred<Unit>()
    private val finishInventory = CompletableDeferred<Unit>()
    private val reconciliationRequested = CompletableDeferred<PairingSnapshot>()
    private val finishReconciliation = CompletableDeferred<Unit>()
    private val reconciliationCommitted = CompletableDeferred<PairingSnapshot>()
    private lateinit var renewal: CredentialRenewal
    private val session = PairingSession(
        "session", "receiver", "sender", "pairing", "new_receiver", "awaiting_confirmation",
        Instant.now().plusSeconds(270), false, false
    )
    private val active = PairingSession(
        sessionId = session.sessionId,
        receiverId = session.receiverId,
        senderId = session.senderId,
        pairingId = session.pairingId,
        mode = session.mode,
        state = "credential_ready",
        expiresAt = session.expiresAt,
        senderConfirmed = true,
        receiverConfirmed = true
    )
    private val store = HomePairingStore(emptyList(), Instant.now().plusSeconds(3600).toString())
    private val pairingStore = object : PairingStore by store {
        override suspend fun reconcile(
            expected: PairingSnapshot,
            items: List<RemotePairing>
        ): Boolean {
            if (!finishInventory.isCompleted) return store.reconcile(expected, items)
            reconciliationRequested.complete(store.readPairings())
            finishReconciliation.await()
            return store.reconcile(expected, items).also { accepted ->
                if (accepted) reconciliationCommitted.complete(store.readPairings())
            }
        }
    }
    private val sessions = object : PairingSessionService {
        override suspend fun authorize(token: String, sessionId: String) = session
        override suspend fun cancel(token: String, sessionId: String) = Unit
        override fun watch(token: String, sessionId: String) =
            if (finishCompletion.isCompleted) flowOf(active) else emptyFlow()
        override fun connect(token: String, sessionId: String) = object : PairingConnection {
            override val events = flow {
                emit(PairingEvent.State(session))
                if (successfulHandshakeEnabled) emit(PairingEvent.Relay(byteArrayOf(1)))
                awaitCancellation()
            }

            override fun send(payload: ByteArray) {
                initSent.set(true)
            }

            override fun close() = Unit
        }

        override suspend fun complete(token: String, sessionId: String): PairingSession {
            check(successfulHandshakeEnabled)
            completionRequested.complete(Unit)
            finishCompletion.await()
            return active
        }
    }
    private val model = PairingEntryViewModel(
        { entryScope ->
            renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, entryScope)
            val pairings = OwnPairings(pairingStore, {
                if (finishCompletion.isCompleted) {
                    inventoryRequested.complete(Unit)
                    finishInventory.await()
                    listOf(RemotePairing("pairing", "receiver", "My device", Presence.Online, ""))
                } else {
                    emptyList()
                }
            }, renewal, entryScope)
            val activation = PairingActivation(pairingStore, pairings, sessions, { input ->
                generationStarted.countDown()
                assertTrue(
                    "Generation was not released",
                    finishGeneration.await(10, TimeUnit.SECONDS)
                )
                inputIntact.set(input.psk.all { it == 1.toByte() })
                object : PairingHandshake {
                    override val init = byteArrayOf(1)
                    override val messageKey = ByteArray(32)
                    override fun confirm(payload: ByteArray): ByteArray {
                        check(successfulHandshakeEnabled)
                        return byteArrayOf(2)
                    }
                    override fun close() {
                        handshakeClosed.set(true)
                    }
                }
            }, renewal)
            PairingEntry(
                pairingStore,
                pairings,
                sessions,
                { PairingQr("session", "receiver", ByteArray(65), ByteArray(32) { 1 }) },
                renewal,
                entryScope,
                activation = activation
            )
        },
        { error("Unexpected test delivery") }
    )

    @After
    fun stopFixture() {
        model.viewModelScope.cancel()
        finishGeneration.countDown()
    }

    @Test
    fun identityHeadingRemainsVisibleUntilGenerationCompletes() {
        beginGeneration()
        finishGeneration.countDown()
        compose.waitUntil(5_000) { model.state.value.stage == PairingStage.Key && initSent.get() }
        compose.onNodeWithText("Exchanging encryption keys").assertIsDisplayed()
        compose.onNodeWithText("Generating device identity").assertDoesNotExist()
    }

    @Test
    fun abandoningDuringGenerationClosesHandshakeWithoutSending() {
        beginGeneration()
        compose.runOnIdle { model.abandon() }
        releaseGenerationAndAssertClosedWithoutSending()
        assertTrue(inputIntact.get())
        assertEquals(PairingEntryPage.Idle, model.state.value.page)
    }

    @Test
    fun replacingAuthorizationSessionDuringGenerationClosesHandshakeWithoutSending() {
        beginGeneration()
        compose.runOnIdle { assertTrue(renewal.resumeSession(renewal.sessionGeneration)) }
        releaseGenerationAndAssertClosedWithoutSending()
    }

    @Test
    fun confirmationRemainsVisibleUntilActivationIsReconciled() {
        successfulHandshakeEnabled = true
        beginGeneration(AppLanguage.SimplifiedChinese)
        finishGeneration.countDown()
        compose.waitUntil(5_000) { completionRequested.isCompleted }
        assertConfirmationVisible()

        finishCompletion.complete(Unit)
        compose.waitUntil(5_000) { inventoryRequested.isCompleted }
        assertConfirmationVisible()

        finishInventory.complete(Unit)
        compose.waitUntil(5_000) { reconciliationRequested.isCompleted }
        val pending = runBlocking { reconciliationRequested.await() }
        assertTrue(pending.items.isEmpty())
        assertEquals(session.pairingId, pending.provisional.single().pairingId)
        assertConfirmationVisible()

        finishReconciliation.complete(Unit)
        compose.waitUntil(5_000) { reconciliationCommitted.isCompleted }
        val committed = runBlocking { reconciliationCommitted.await() }
        val pairing = committed.items.single()
        assertEquals(session.pairingId, pairing.pairingId)
        assertEquals(session.receiverId, pairing.receiverId)
        assertEquals("My device", pairing.alias)
        assertTrue(pairing.keyUsable && pairing.provisionalSessionId == null)
        assertTrue(committed.provisional.isEmpty())
        compose.waitUntil(5_000) { model.state.value.page == PairingEntryPage.Success }
        assertEquals(session.pairingId, model.state.value.activatedPairingId)
        compose.onNodeWithText("配对完成").assertIsDisplayed()
        compose.onNodeWithText("正在确认配对信息").assertDoesNotExist()
    }

    @Test
    fun failureResultDisplaysPreservationAssuranceAndHostActions() {
        val state = mutableStateOf(
            PairingEntryState(
                page = PairingEntryPage.Progress,
                problem = PairingEntryProblem.Unavailable
            )
        )
        var left = false
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                PinGlideTheme { PairingResultScreen(state.value, model) { left = true } }
            }
        }
        compose.onNode(
            hasText("配对未完成") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).assertIsDisplayed()
        compose.onNodeWithText("配对尚未生效").assertIsDisplayed()
        compose.onNodeWithText("连接中断或确认超时，未能添加这台设备。已有设备的配对不受影响，请重新扫描后再试。")
            .assertIsDisplayed()
        compose.onNodeWithText("重新扫描").assertIsDisplayed()
        compose.onNodeWithText("返回首页").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("consentEvidence") == "true") {
            compose.onRoot().captureToImage()
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            captureConsentEvidence("pairing-failure.png")
        }
        compose.onNodeWithText("返回首页").performClick()
        compose.runOnIdle {
            assertTrue(left)
            state.value = state.value.copy(host = PairingHost.Onboarding)
        }
        compose.onNodeWithText("未提交新配对").assertIsDisplayed()
        compose.onNodeWithText("连接中断或确认超时。原来的配对与密钥仍然有效，不会留下半完成的关联。")
            .assertIsDisplayed()
        compose.onNodeWithText("稍后配对").assertIsDisplayed()
        compose.onNodeWithText("重新扫描").performClick()
        compose.runOnIdle { assertEquals(PairingEntryPage.Scanner, model.state.value.page) }
    }

    private fun assertConfirmationVisible() {
        compose.onNodeWithText("正在确认配对信息").assertIsDisplayed()
        compose.onNodeWithText("正在打开配对通道").assertDoesNotExist()
        compose.onNodeWithText("正在生成设备身份").assertDoesNotExist()
        compose.onNodeWithText("正在交换加密密钥").assertDoesNotExist()
        compose.onNodeWithText("配对完成").assertDoesNotExist()
        compose.onNodeWithText("保持接收端上的鹊信窗口打开。完成后会自动进入配对完成页面。")
            .assertIsDisplayed()
        compose.assertAccessiblePairingProgress()
    }

    private fun releaseGenerationAndAssertClosedWithoutSending() {
        finishGeneration.countDown()
        compose.waitUntil(5_000) { initSent.get() || handshakeClosed.get() }
        assertFalse(initSent.get())
        assertTrue(handshakeClosed.get())
    }

    private fun beginGeneration(language: AppLanguage = AppLanguage.English) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val state by model.state.collectAsState()
            LocalizedContent(language) {
                PinGlideTheme {
                    if (state.page == PairingEntryPage.Consent ||
                        state.page == PairingEntryPage.Progress ||
                        state.page == PairingEntryPage.Success
                    ) {
                        PairingScreen(state, model) {}
                    }
                }
            }
        }
        compose.runOnIdle {
            val home = HomeState()
            model.cameraGranted(model.requestCamera(PairingHost.Onboarding, home), home)
        }
        compose.waitUntil(5_000) { model.state.value.page == PairingEntryPage.Scanner }
        compose.runOnIdle { model.scan("fixture", "My device") }
        compose.waitUntil(5_000) { model.state.value.page == PairingEntryPage.Consent }
        val chinese = language == AppLanguage.SimplifiedChinese
        compose.onNodeWithText(
            if (chinese) "确认并建立密钥" else "Confirm and establish key"
        ).performClick()

        assertTrue(generationStarted.await(5, TimeUnit.SECONDS))
        compose.onNodeWithText(
            if (chinese) "正在生成设备身份" else "Generating device identity"
        ).assertIsDisplayed()
        compose.onNodeWithText(
            if (chinese) "正在交换加密密钥" else "Exchanging encryption keys"
        ).assertDoesNotExist()
        assertFalse(initSent.get())
    }
}
