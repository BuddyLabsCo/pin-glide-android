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
    val enabled: Boolean = true,
    val key: ByteArray? = null,
    val keyFailed: Boolean = false
) {
    val keyUsable: Boolean get() = !keyFailed && key?.size == 32
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

    suspend fun rename(managementToken: String, pairingId: String, alias: String): RemotePairing =
        throw PairingServiceException(PairingFailure.Unavailable)

    suspend fun remove(managementToken: String, pairingId: String): Unit =
        throw PairingServiceException(PairingFailure.Unavailable)
}

enum class PairingFailure { Unavailable, LoginRequired, Revoked, Gone }

enum class PairingChange { Renamed, Removed, Gone, InvalidAlias, Unavailable }

class PairingServiceException(val failure: PairingFailure) :
    Exception("Pairing operation unavailable")

fun validPairingAlias(alias: String, otherAliases: List<String>): Boolean {
    val trimmed = alias.trim()
    return trimmed.isNotBlank() && trimmed.codePointCount(0, trimmed.length) in 1..40 &&
        trimmed.codePoints().noneMatch { it in 0xD800..0xDFFF } &&
        otherAliases.none { it.equals(trimmed, ignoreCase = true) }
}
