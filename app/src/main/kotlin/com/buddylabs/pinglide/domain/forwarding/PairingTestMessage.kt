package com.buddylabs.pinglide.domain.forwarding

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import java.time.Clock
import java.util.UUID

class TestCiphertext(val nonce: String, val ciphertext: String)

enum class TestMessageResult {
    Received,
    Offline,
    Rejected,
    SendFailed,
    AckTimeout,
    Interrupted,
    RequestError,
    InvalidPairing,
    InvalidPayload,
    UnsupportedEncryption,
    DuplicateTarget,
    Uncertain,
    NoKey,
    LoginRequired
}

data class TestMessageOutcome(val targetAlias: String, val result: TestMessageResult)

class DeliveryItem(val target: OwnPairing, val encrypted: TestCiphertext)

fun interface TestDeliveryService {
    suspend fun send(
        token: String,
        messageId: String,
        selectItems: suspend () -> List<DeliveryItem>
    ): List<TestMessageResult>?
}

class PairingTestMessage(
    private val store: PairingStore,
    private val renewal: CredentialRenewal,
    private val encrypt: (ByteArray, Long) -> TestCiphertext,
    private val service: TestDeliveryService,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun send(pairingId: String): TestMessageOutcome? {
        val receivedAt = clock.millis()
        val session = renewal.sessionGeneration
        var alias = ""
        var authorized = false
        val result = try {
            val origin = store.readPairings()
            alias = origin.items.find { it.pairingId == pairingId }?.alias.orEmpty()
            renewal.withAccess(CredentialAccess.Upload) { identity ->
                if (!renewal.isSessionCurrent(session) ||
                    identity.accountId != origin.authority.identity?.accountId ||
                    identity.senderId != origin.authority.identity.senderId
                ) {
                    return@withAccess null
                }
                authorized = true
                sendAuthorized(origin, pairingId, identity, session, receivedAt)
            }
        } catch (failure: AuthorizationException) {
            when {
                authorized || !renewal.isSessionCurrent(session) -> null

                failure.outcome in
                    setOf(AuthorizationState.LoginRequired, AuthorizationState.RecoveryRequired) ->
                    TestMessageResult.LoginRequired

                else -> TestMessageResult.Uncertain
            }
        } catch (_: AccessExpiredException) {
            TestMessageResult.LoginRequired
        } catch (_: IdentityStorageException) {
            TestMessageResult.NoKey
        } catch (failure: PairingServiceException) {
            if (failure.failure == PairingFailure.LoginRequired) {
                TestMessageResult.LoginRequired
            } else {
                TestMessageResult.Uncertain
            }
        }
        return result?.takeIf { renewal.isSessionCurrent(session) }?.let {
            TestMessageOutcome(alias, it)
        }
    }

    private suspend fun sendAuthorized(
        origin: PairingSnapshot,
        pairingId: String,
        identity: SenderIdentity,
        session: Long,
        receivedAt: Long
    ): TestMessageResult? {
        val initial = store.readPairings()
        if (initial.authority.identity?.upload?.token != identity.upload.token) return null
        val target = initial.items.find { it.pairingId == pairingId && it.keyUsable }
        if (target == null || target.encryptions !in 2 until 4294967296L) {
            return TestMessageResult.NoKey
        }
        if (origin.items.none { it.sameTarget(target) }) return null
        if (!store.reserveEncryption(initial, pairingId)) {
            return if (current(initial, target, session)) TestMessageResult.Uncertain else null
        }
        val key = checkNotNull(target.key).copyOf()
        val encrypted = try {
            encrypt(key, receivedAt)
        } finally {
            key.fill(0)
        }
        return upload(initial, target, encrypted, identity.upload.token, session)
    }

    private suspend fun upload(
        initial: PairingSnapshot,
        target: OwnPairing,
        encrypted: TestCiphertext,
        token: String,
        session: Long
    ): TestMessageResult? {
        val messageId = UUID.randomUUID().toString()
        if (!current(initial, target, session)) return null
        val result = try {
            val item = DeliveryItem(target, encrypted)
            service.send(token, messageId) {
                if (current(initial, target, session)) listOf(item) else emptyList()
            }?.singleOrNull()
        } catch (_: AccessExpiredException) {
            TestMessageResult.LoginRequired
        } catch (failure: PairingServiceException) {
            if (failure.failure == PairingFailure.LoginRequired) {
                renewal.rejectAccess(
                    checkNotNull(initial.authority.identity),
                    RefreshState.LoginRequired
                )
            }
            throw failure
        }
        return if (current(initial, target, session)) result else null
    }

    private suspend fun current(
        initial: PairingSnapshot,
        target: OwnPairing,
        session: Long
    ): Boolean {
        val current = store.readPairings()
        return renewal.isSessionCurrent(session) &&
            current.authority.generation == initial.authority.generation &&
            current.authority.identity?.accountId == initial.authority.identity?.accountId &&
            current.authority.identity?.senderId == initial.authority.identity?.senderId &&
            current.authority.refreshState == RefreshState.Active &&
            current.authority.identity?.let { !renewal.isRegistrationClosed(it) } == true &&
            current.items.any { it.sameTarget(target) }
    }

    private fun OwnPairing.sameTarget(other: OwnPairing): Boolean =
        pairingId == other.pairingId && receiverId == other.receiverId && alias == other.alias &&
            keyUsable && key.contentEquals(other.key)
}
