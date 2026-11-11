package com.buddylabs.pinglide.domain.pairing

import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class PairingHost { Onboarding, Home }

enum class PairingEntryPage { Idle, Scanner, Consent, Progress, Success }

enum class PairingStage { Opening, Identity, Key, Confirm }

enum class PairingEntryProblem {
    InvalidQr,
    Closed,
    Unsupported,
    Capacity,
    Unavailable,
    Unconfirmed,
    Camera
}

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
    val highlightUntil: Long = 0,
    val stage: PairingStage = PairingStage.Opening,
    val activatedPairingId: String? = null
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

internal fun PairingSession.validateConsent(
    scanned: PairingQr,
    expectedSenderId: String?,
    previous: PairingSession?,
    now: Instant
) {
    if (sessionId != scanned.sessionId || receiverId != scanned.receiverId ||
        senderId != expectedSenderId || pairingId.isNullOrBlank()
    ) {
        throw PairingEntryException(PairingEntryProblem.InvalidQr)
    }
    if (mode !in setOf("new_receiver", "add_sender")) {
        throw PairingEntryException(PairingEntryProblem.Unsupported)
    }
    if (previous?.sessionId == sessionId &&
        (
            previous.expiresAt != expiresAt || previous.mode != mode ||
                previous.pairingId != pairingId
            )
    ) {
        throw PairingEntryException(PairingEntryProblem.Closed)
    }
    if (senderConfirmed || receiverConfirmed || state != "awaiting_confirmation" ||
        !expiresAt.isAfter(now) || expiresAt.isAfter(now.plusSeconds(300))
    ) {
        throw PairingEntryException(PairingEntryProblem.Closed)
    }
}

interface PairingSessionService {
    suspend fun authorize(token: String, sessionId: String): PairingSession

    suspend fun cancel(token: String, sessionId: String)

    fun watch(token: String, sessionId: String): Flow<PairingSession>

    fun connect(token: String, sessionId: String): PairingConnection =
        throw PairingEntryException(PairingEntryProblem.Unavailable)

    suspend fun complete(token: String, sessionId: String): PairingSession =
        throw PairingEntryException(PairingEntryProblem.Unavailable)
}

class PairingEntryException(val problem: PairingEntryProblem) : Exception("Pairing unavailable")
