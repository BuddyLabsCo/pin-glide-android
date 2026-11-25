package com.buddylabs.pinglide.ui.home

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.domain.pairing.OwnPairing
import com.buddylabs.pinglide.domain.pairing.PairingSnapshot
import com.buddylabs.pinglide.domain.pairing.PairingStore
import com.buddylabs.pinglide.domain.pairing.RemotePairing

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
    private val provisional = mutableMapOf<String, OwnPairing>()
    private val pairings = targets.associate { target ->
        target.pairingId to OwnPairing(
            target.pairingId,
            target.receiverId,
            target.alias,
            key = if (target.pairingId == missingKeyId) null else ByteArray(32)
        )
    }.toMutableMap()

    override suspend fun read() = authority

    override suspend fun readPairings() = PairingSnapshot(
        authority,
        revision,
        listed,
        pairings.values.toList(),
        provisional.values.toList()
    )

    override suspend fun saveProvisional(expected: PairingSnapshot, pairing: OwnPairing): Boolean {
        if (expected.revision != revision || expected.authority != authority) return false
        if (!pairing.keyUsable || pairing.provisionalSessionId.isNullOrBlank() ||
            pairing.pairingId in pairings || pairing.pairingId in provisional ||
            pairings.values.any { it.receiverId == pairing.receiverId } ||
            !validMetadata(pairing.pairingId, pairing.receiverId, pairing.alias) ||
            pairing.encryptions !in 2..4294967296L
        ) {
            throw IdentityStorageException()
        }
        provisional[pairing.pairingId] = pairing
        revision++
        return true
    }

    override suspend fun reconcile(expected: PairingSnapshot, items: List<RemotePairing>): Boolean {
        if (revision != expected.revision || expected.authority != authority) return false
        if (items.map { it.pairingId }.distinct().size != items.size ||
            items.map { it.receiverId }.distinct().size != items.size ||
            items.any { item ->
                val retained = pairings[item.pairingId] ?: provisional[item.pairingId]
                !validMetadata(item.pairingId, item.receiverId, item.alias) ||
                    (retained != null && retained.receiverId != item.receiverId)
            }
        ) {
            throw IdentityStorageException()
        }
        val remote = items.associateBy { it.pairingId }
        pairings.keys.retainAll(remote.keys)
        pairings.replaceAll { pairingId, pairing ->
            pairing.copy(alias = remote.getValue(pairingId).alias)
        }
        for (item in items) {
            val pending = provisional.remove(item.pairingId) ?: continue
            pairings[item.pairingId] = pending.copy(alias = item.alias, provisionalSessionId = null)
        }
        listed = true
        revision++
        return true
    }

    private fun validMetadata(pairingId: String, receiverId: String, alias: String): Boolean =
        pairingId.isNotBlank() && receiverId.isNotBlank() &&
            alias.isNotBlank() && alias.codePointCount(0, alias.length) <= 40

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

    override suspend fun reserveEncryption(expected: PairingSnapshot, pairingId: String): Boolean {
        val pairing = pairings[pairingId] ?: return false
        if (expected.revision != revision || !pairing.keyUsable) return false
        pairings[pairingId] = pairing.copy(encryptions = pairing.encryptions + 1)
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
