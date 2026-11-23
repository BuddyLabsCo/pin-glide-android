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
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull

private const val REMOVAL_LOOKUP_TIMEOUT_MILLIS = 2_000L

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

data class TestMessageOutcome(
    val targetAlias: String,
    val result: TestMessageResult,
    val pairingRemoved: Boolean = false
)

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
    suspend fun send(pairingId: String): TestMessageOutcome? = sendTest(pairingId, inventory = null)

    internal suspend fun sendFromHome(
        pairingId: String,
        inventory: PairingService
    ): TestMessageOutcome? = sendTest(pairingId, inventory)

    private suspend fun sendTest(
        pairingId: String,
        inventory: PairingService?
    ): TestMessageOutcome? {
        val receivedAt = clock.millis()
        val session = renewal.sessionGeneration
        var alias = ""
        var authorizedSender: SenderIdentity? = null
        val result = try {
            val origin = store.readPairings()
            alias = origin.items.find { it.pairingId == pairingId }?.alias.orEmpty()
            renewal.withAccess(CredentialAccess.Upload) { identity ->
                if (!renewal.isSessionCurrent(session) ||
                    origin.authority.identity?.sameSender(identity) != true
                ) {
                    return@withAccess null
                }
                authorizedSender = identity
                sendAuthorized(origin, pairingId, identity, session, receivedAt)
            }
        } catch (failure: AuthorizationException) {
            when {
                authorizedSender != null || !renewal.isSessionCurrent(session) -> null

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
            verifyOutcome(
                TestMessageOutcome(alias, it),
                inventory,
                pairingId,
                authorizedSender,
                session
            )
        }
    }

    private suspend fun verifyOutcome(
        outcome: TestMessageOutcome,
        inventory: PairingService?,
        pairingId: String,
        authorizedSender: SenderIdentity?,
        session: Long
    ): TestMessageOutcome? {
        if (outcome.result != TestMessageResult.InvalidPairing || inventory == null) return outcome
        val sender = checkNotNull(authorizedSender)
        val removed = withTimeoutOrNull(REMOVAL_LOOKUP_TIMEOUT_MILLIS) {
            isRemovalConfirmed(pairingId, inventory, sender, session)
        } == true
        return outcome.copy(pairingRemoved = removed).takeIf {
            isCurrentSender(sender, session)
        }
    }

    private suspend fun isRemovalConfirmed(
        pairingId: String,
        inventory: PairingService,
        sender: SenderIdentity,
        session: Long
    ): Boolean = try {
        renewal.withAccess(CredentialAccess.Management) { identity ->
            renewal.isSessionCurrent(session) && identity.sameSender(sender) &&
                inventory.list(identity.management.token).none { it.pairingId == pairingId }
        }
    } catch (_: PairingServiceException) {
        false
    } catch (_: AccessExpiredException) {
        false
    } catch (_: AuthorizationException) {
        false
    } catch (_: IdentityStorageException) {
        false
    }

    private suspend fun isCurrentSender(sender: SenderIdentity, session: Long): Boolean = try {
        val authority = store.readPairings().authority
        authority.refreshState == RefreshState.Active &&
            authority.identity?.sameSender(sender) == true &&
            !renewal.isRegistrationClosed(sender) && renewal.isSessionCurrent(session)
    } catch (_: IdentityStorageException) {
        false
    }

    private fun SenderIdentity.sameSender(other: SenderIdentity): Boolean =
        accountId == other.accountId && senderId == other.senderId

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
