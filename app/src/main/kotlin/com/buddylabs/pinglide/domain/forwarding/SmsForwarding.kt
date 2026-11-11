package com.buddylabs.pinglide.domain.forwarding

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
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
import java.util.UUID

class SmsForwarding(
    private val store: PairingStore,
    private val renewal: CredentialRenewal,
    private val service: TestDeliveryService,
    private val encrypt: (ByteArray, String, String) -> TestCiphertext,
    private val permitted: () -> Boolean,
    private val extraction: OtpExtraction
) {
    suspend fun forward(smsText: String) {
        val session = renewal.sessionGeneration
        try {
            if (!permitted() || !renewal.isSessionCurrent(session)) return
            val origin = store.readPairings()
            val targets = origin.items.filter { it.enabled && it.keyUsable }
            if (targets.isEmpty()) return
            val otp = extraction.extract(smsText)
            if (!permitted() || !renewal.isSessionCurrent(session)) return
            renewal.withAccess(CredentialAccess.Upload) { identity ->
                if (identity.accountId == origin.authority.identity?.accountId &&
                    identity.senderId == origin.authority.identity.senderId &&
                    renewal.isSessionCurrent(session)
                ) {
                    deliver(smsText, otp, targets, identity, session)
                }
            }
        } catch (_: AuthorizationException) {
            return
        } catch (_: IdentityStorageException) {
            return
        } catch (_: PairingServiceException) {
            return
        }
    }

    private suspend fun deliver(
        smsText: String,
        otp: String,
        targets: List<OwnPairing>,
        identity: SenderIdentity,
        session: Long
    ) {
        val initial = store.readPairings()
        if (initial.authority.identity?.upload?.token != identity.upload.token) return
        val encrypted = targets.mapNotNull { target ->
            val current = store.readPairings()
            if (!current.authorized(initial, session)) return
            val usable = current.items.find { it.sameTarget(target) }
                ?.takeIf { it.encryptions in 2 until 4294967296L } ?: return@mapNotNull null
            if (!store.reserveEncryption(current, usable.pairingId)) return@mapNotNull null
            val key = checkNotNull(usable.key).copyOf()
            try {
                DeliveryItem(target, encrypt(key, smsText, otp))
            } catch (_: IdentityStorageException) {
                null
            } finally {
                key.fill(0)
            }
        }
        if (encrypted.isEmpty()) return
        val messageId = UUID.randomUUID().toString()
        try {
            service.send(identity.upload.token, messageId) {
                val latest = store.readPairings()
                if (latest.authorized(initial, session)) {
                    encrypted.filter { item -> latest.items.any { it.sameTarget(item.target) } }
                } else {
                    emptyList()
                }
            }
        } catch (_: AccessExpiredException) {
            return
        } catch (failure: PairingServiceException) {
            if (failure.failure == PairingFailure.LoginRequired) {
                renewal.rejectAccess(identity, RefreshState.LoginRequired, session)
            }
            throw failure
        }
    }

    private fun PairingSnapshot.authorized(initial: PairingSnapshot, session: Long): Boolean =
        permitted() && renewal.isSessionCurrent(session) &&
            authority.generation == initial.authority.generation &&
            authority.identity?.accountId == initial.authority.identity?.accountId &&
            authority.identity?.senderId == initial.authority.identity?.senderId &&
            authority.identity?.let { !renewal.isRegistrationClosed(it) } == true &&
            authority.refreshState == RefreshState.Active

    private fun OwnPairing.sameTarget(other: OwnPairing): Boolean =
        pairingId == other.pairingId && receiverId == other.receiverId && enabled && keyUsable &&
            key.contentEquals(other.key)
}
