package com.buddylabs.pinglide.domain.pairing

import kotlinx.coroutines.flow.Flow

sealed interface PairingEvent {
    class State(val session: PairingSession) : PairingEvent
    class Relay(val payload: ByteArray) : PairingEvent
}

interface PairingConnection : AutoCloseable {
    val events: Flow<PairingEvent>

    fun send(payload: ByteArray)
}

interface PairingHandshake : AutoCloseable {
    val init: ByteArray
    val messageKey: ByteArray

    fun confirm(payload: ByteArray): ByteArray
}

fun interface PairingCryptography {
    fun start(qr: PairingQr): PairingHandshake
}
