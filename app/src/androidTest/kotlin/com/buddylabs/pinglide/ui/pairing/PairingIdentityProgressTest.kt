package com.buddylabs.pinglide.ui.pairing

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingActivation
import com.buddylabs.pinglide.domain.pairing.PairingConnection
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
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
import java.util.concurrent.ConcurrentLinkedQueue
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
    private val connectionClosed = AtomicBoolean(false)
    private val authorizedSessions = ConcurrentLinkedQueue<String>()
    private val cancelledSessions = ConcurrentLinkedQueue<String>()
    private var expireDuringActivation = false
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
        override suspend fun authorize(token: String, sessionId: String): PairingSession {
            authorizedSessions.add(sessionId)
            return when (sessionId) {
                session.sessionId -> session

                "fresh-session" -> PairingSession(
                    sessionId, "receiver", "sender", "fresh-pairing", "new_receiver",
                    "awaiting_confirmation", session.expiresAt, false, false
                )

                else -> error("Unexpected session")
            }
        }

        override suspend fun cancel(token: String, sessionId: String) {
            cancelledSessions.add(sessionId)
        }
        override fun watch(token: String, sessionId: String) =
            if (finishCompletion.isCompleted) flowOf(active) else emptyFlow()
        override fun connect(token: String, sessionId: String) = object : PairingConnection {
            override val events = flow {
                emit(PairingEvent.State(session))
                if (expireDuringActivation) {
                    emit(
                        PairingEvent.State(
                            PairingSession(
                                session.sessionId, session.receiverId, session.senderId,
                                session.pairingId, session.mode, "expired", session.expiresAt,
                                false, false
                            )
                        )
                    )
                }
                if (successfulHandshakeEnabled) emit(PairingEvent.Relay(byteArrayOf(1)))
                awaitCancellation()
            }

            override fun send(payload: ByteArray) {
                initSent.set(true)
            }

            override fun close() {
                connectionClosed.set(true)
            }
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
                { sessionId ->
                    PairingQr(sessionId, "receiver", ByteArray(65), ByteArray(32) { 1 })
                },
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
    fun activationExpiryReleasesSessionAndRequiresDifferentQr() {
        expireDuringActivation = true
        beginGeneration()
        finishGeneration.countDown()
        compose.waitUntil(5_000) { model.state.value.problem == PairingEntryProblem.Closed }
        assertEquals(PairingEntryPage.Progress, model.state.value.page)
        assertExpiredResult()
        compose.waitUntil(5_000) { cancelledSessions.isNotEmpty() }
        assertTrue(initSent.get())
        assertTrue(connectionClosed.get())
        assertTrue(handshakeClosed.get())
        val released = runBlocking { store.readPairings() }
        assertTrue(released.items.isEmpty() && released.provisional.isEmpty())
        assertEquals(listOf(session.sessionId), cancelledSessions.toList())
        assertEquals(listOf(session.sessionId, session.sessionId), authorizedSessions.toList())

        compose.onNodeWithText("Scan a new QR code").performClick()
        compose.waitUntil(5_000) { model.state.value.page == PairingEntryPage.Scanner }
        compose.runOnIdle { model.scan(session.sessionId, "My device") }
        compose.waitUntil(5_000) { model.state.value.problem == PairingEntryProblem.Closed }
        assertExpiredResult()
        assertEquals(listOf(session.sessionId, session.sessionId), authorizedSessions.toList())
        assertEquals(listOf(session.sessionId), cancelledSessions.toList())

        compose.onNodeWithText("Scan a new QR code").performClick()
        compose.waitUntil(5_000) { model.state.value.page == PairingEntryPage.Scanner }
        compose.runOnIdle { model.scan("fresh-session", "New device") }
        compose.waitUntil(5_000) { model.state.value.page == PairingEntryPage.Consent }
        compose.onNodeWithText("Confirm Pairing").assertIsDisplayed()
        compose.onNodeWithText("Confirm and establish key").assertIsDisplayed()
        compose.onNodeWithText("QR code expired").assertDoesNotExist()
        assertEquals(
            listOf(session.sessionId, session.sessionId, "fresh-session"),
            authorizedSessions.toList()
        )
    }

    private fun assertExpiredResult() {
        compose.onNode(
            hasText("QR code expired") and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        ).assertIsDisplayed()
        compose.onNodeWithText("Pairing incomplete").assertDoesNotExist()
        compose.onNodeWithText("Exchanging encryption keys").assertDoesNotExist()
        compose.onNodeWithText("Pair later").assertIsDisplayed()
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
                        state.page == PairingEntryPage.Success ||
                        state.problem == PairingEntryProblem.Closed
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
        compose.runOnIdle { model.scan(session.sessionId, "My device") }
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
