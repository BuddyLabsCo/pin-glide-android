package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import kotlinx.serialization.Serializable

enum class Presence { Online, Offline, Unknown }

class RemotePairing(
    val pairingId: String,
    val receiverId: String,
    val alias: String,
    val presence: Presence,
    val stateChangedAt: String
)

@Serializable
data class OwnPairing(
    val pairingId: String,
    val receiverId: String,
    val alias: String,
    val enabled: Boolean = false,
    val key: ByteArray? = null
) {
    val keyUsable: Boolean get() = key?.size == 32
}

class PairingSnapshot(
    val authority: IdentitySnapshot,
    val revision: Long = 0,
    val listed: Boolean = false,
    val items: List<OwnPairing> = emptyList()
)

interface PairingStore {
    suspend fun readPairings(): PairingSnapshot

    suspend fun reconcile(expected: PairingSnapshot, items: List<RemotePairing>): Boolean

    suspend fun select(expected: PairingSnapshot, pairingId: String, enabled: Boolean): Boolean
}

fun interface PairingService {
    suspend fun list(managementToken: String): List<RemotePairing>
}

enum class PairingFailure { Unavailable, LoginRequired, Revoked }

class PairingServiceException(val failure: PairingFailure) : Exception("Pairing list unavailable")
