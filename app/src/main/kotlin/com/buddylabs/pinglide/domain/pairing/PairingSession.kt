package com.buddylabs.pinglide.domain.pairing

import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class PairingHost { Onboarding, Home }

enum class PairingEntryPage { Idle, Scanner, Consent }

enum class PairingEntryProblem { InvalidQr, Closed, Unsupported, Capacity, Unavailable, Camera }

data class PairingEntryState(
    val page: PairingEntryPage = PairingEntryPage.Idle,
    val host: PairingHost = PairingHost.Home,
    val pending: Boolean = false,
    val problem: PairingEntryProblem? = null,
    val alias: String = "",
    val aliasError: PairingAliasError? = null,
    val remainingSeconds: Long = 0,
    val confirmed: Boolean = false,
    val duplicatePairingId: String? = null,
    val highlightUntil: Long = 0
)

class PairingQr(
    val sessionId: String,
    val receiverId: String,
    val publicKey: ByteArray,
    val psk: ByteArray
) {
    fun matches(other: PairingQr): Boolean = sessionId == other.sessionId &&
        receiverId == other.receiverId && publicKey.contentEquals(other.publicKey) &&
        psk.contentEquals(other.psk)

    fun clear() {
        publicKey.fill(0)
        psk.fill(0)
    }
}

fun interface PairingQrDecoder {
    fun decode(text: String): PairingQr
}

class InvalidPairingQrException : Exception("Invalid Pairing QR")

class PairingSession(
    val sessionId: String,
    val receiverId: String,
    val senderId: String?,
    val pairingId: String?,
    val mode: String,
    val state: String,
    val expiresAt: Instant,
    val senderConfirmed: Boolean,
    val receiverConfirmed: Boolean
)

interface PairingSessionService {
    suspend fun authorize(token: String, sessionId: String): PairingSession

    suspend fun cancel(token: String, sessionId: String)

    fun watch(token: String, sessionId: String): Flow<PairingSession>
}

class PairingEntryException(val problem: PairingEntryProblem) : Exception("Pairing unavailable")
