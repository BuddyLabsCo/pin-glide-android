package com.buddylabs.pinglide.domain.forwarding

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import java.util.UUID

class TestCiphertext(val nonce: String, val ciphertext: String)

enum class TestMessageResult {
    Received,
    Offline,
    InvalidPairing,
    InvalidPayload,
    UnsupportedEncryption,
    DuplicateTarget,
    Uncertain,
    NoKey,
    LoginRequired
}

data class TestMessageOutcome(val targetAlias: String, val result: TestMessageResult)

fun interface TestDeliveryService {
    suspend fun send(
        token: String,
        messageId: String,
        target: OwnPairing,
        encrypted: TestCiphertext
    ): TestMessageResult
}

class PairingTestMessage(
    private val store: PairingStore,
    private val renewal: CredentialRenewal,
    private val encrypt: (ByteArray) -> TestCiphertext,
    private val service: TestDeliveryService
) {
    suspend fun send(pairingId: String): TestMessageOutcome? {
        var alias = ""
        val result = try {
            val initial = store.readPairings()
            val target = initial.items.find { it.pairingId == pairingId && it.keyUsable }
            alias = target?.alias.orEmpty()
            if (target == null || target.encryptions >= 4294967296L) {
                TestMessageResult.NoKey
            } else {
                if (!store.reserveEncryption(initial, pairingId)) {
                    TestMessageResult.NoKey
                } else {
                    val key = checkNotNull(target.key).copyOf()
                    val encrypted = try {
                        encrypt(key)
                    } finally {
                        key.fill(0)
                    }
                    upload(initial, target, encrypted)
                }
            }
        } catch (_: AuthorizationException) {
            TestMessageResult.LoginRequired
        } catch (_: AccessExpiredException) {
            TestMessageResult.Uncertain
        } catch (_: IdentityStorageException) {
            TestMessageResult.NoKey
        } catch (_: PairingServiceException) {
            TestMessageResult.LoginRequired
        }
        return result?.let { TestMessageOutcome(alias, it) }
    }

    private suspend fun upload(
        initial: PairingSnapshot,
        target: OwnPairing,
        encrypted: TestCiphertext
    ): TestMessageResult? {
        val messageId = UUID.randomUUID().toString()
        return renewal.withAccess(CredentialAccess.Upload) { identity ->
            if (identity.accountId != initial.authority.identity?.accountId ||
                identity.senderId != initial.authority.identity.senderId || !current(target)
            ) {
                return@withAccess null
            }
            val result = try {
                service.send(identity.upload.token, messageId, target, encrypted)
            } catch (failure: PairingServiceException) {
                if (failure.failure == PairingFailure.LoginRequired) {
                    renewal.rejectAccess(identity, RefreshState.LoginRequired)
                }
                throw failure
            }
            if (current(target)) result else null
        }
    }

    private suspend fun current(target: OwnPairing): Boolean = store.readPairings().items.any {
        it.pairingId == target.pairingId && it.receiverId == target.receiverId && it.keyUsable &&
            checkNotNull(it.key).contentEquals(target.key)
    }
}
