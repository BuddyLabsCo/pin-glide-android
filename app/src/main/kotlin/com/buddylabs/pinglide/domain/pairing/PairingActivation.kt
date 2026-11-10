package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.RefreshState
import java.time.Clock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeout

class PairingActivation(
    private val store: PairingStore,
    private val pairings: OwnPairings,
    private val service: PairingSessionService,
    private val crypto: PairingCryptography,
    private val renewal: CredentialRenewal,
    private val clock: Clock = Clock.systemUTC(),
    private val onActivated: () -> Unit = {}
) {
    suspend fun activate(
        qr: PairingQr,
        session: PairingSession,
        alias: String,
        stage: (PairingStage) -> Unit
    ): String {
        val initial = store.readPairings()
        val exchange = Exchange(initial, qr, session, alias, stage)
        try {
            exchange.run()
        } catch (_: TimeoutCancellationException) {
            if (!exchange.saved) unavailable()
        } catch (failure: PairingEntryException) {
            if (!exchange.saved) throw failure
        } catch (failure: PairingServiceException) {
            if (!exchange.saved) throw failure
        } catch (failure: AccessExpiredException) {
            if (!exchange.saved) throw failure
        } catch (failure: AuthorizationException) {
            if (!exchange.saved) throw failure
        } catch (failure: IdentityStorageException) {
            if (!exchange.saved) throw failure
        } finally {
            exchange.close()
        }
        return resume(session.pairingId ?: unavailable())
    }

    suspend fun recover() {
        val initial = try {
            store.readPairings()
        } catch (_: IdentityStorageException) {
            return
        }
        for (pending in initial.provisional.filter { it.keyUsable }) {
            try {
                requireOwner(initial)
                resume(pending.pairingId)
            } catch (_: PairingEntryException) {
                continue
            }
        }
    }

    suspend fun resume(pairingId: String): String = try {
        resumeCurrent(pairingId)
    } catch (failure: PairingEntryException) {
        if (failure.problem == PairingEntryProblem.Closed) throw failure
        unconfirmed()
    } catch (_: PairingServiceException) {
        unconfirmed()
    } catch (_: AccessExpiredException) {
        unconfirmed()
    } catch (_: AuthorizationException) {
        unconfirmed()
    } catch (_: IdentityStorageException) {
        unconfirmed()
    }

    private suspend fun resumeCurrent(pairingId: String): String {
        val initial = store.readPairings()
        val pending = initial.provisional.find { it.pairingId == pairingId && it.keyUsable }
        val closed = pending != null && inspectRetained(initial, pending)
        requireOwner(initial)
        pairings.synchronize()
        val current = requireOwner(initial)
        val active = current.items.find {
            it.pairingId == pairingId && it.keyUsable &&
                (pending == null || it.receiverId == pending.receiverId)
        }
        if (active != null && !pairings.state.value.failed) {
            if (pending != null && active.alias != pending.alias) unconfirmed()
            onActivated()
            return pairingId
        }
        if (closed && !pairings.state.value.failed) {
            throw PairingEntryException(PairingEntryProblem.Closed)
        }
        unconfirmed()
    }

    private suspend fun inspectRetained(initial: PairingSnapshot, pending: OwnPairing): Boolean =
        try {
            withTimeout(10_000) {
                renewal.withAccess(CredentialAccess.Management) { identity ->
                    requireOwner(initial)
                    try {
                        val current = service.watch(
                            identity.management.token,
                            checkNotNull(pending.provisionalSessionId)
                        ).firstOrNull() ?: unavailable()
                        validateRetained(current, pending, identity.senderId)
                        if (!isActive(current) && current.receiverConfirmed &&
                            !current.senderConfirmed
                        ) {
                            report(initial, current)
                        }
                    } catch (failure: PairingServiceException) {
                        if (failure.failure == PairingFailure.LoginRequired) {
                            renewal.rejectAccess(identity, RefreshState.LoginRequired)
                        }
                        throw failure
                    }
                }
            }
            false
        } catch (_: TimeoutCancellationException) {
            false
        } catch (failure: PairingEntryException) {
            failure.problem == PairingEntryProblem.Closed
        } catch (_: PairingServiceException) {
            false
        } catch (_: AccessExpiredException) {
            false
        }

    private fun validateRetained(current: PairingSession, pending: OwnPairing, senderId: String) {
        if (current.sessionId != pending.provisionalSessionId ||
            current.pairingId != pending.pairingId || current.receiverId != pending.receiverId ||
            current.senderId != senderId
        ) {
            unavailable()
        }
        validate(current, current)
    }

    private inner class Exchange(
        val initial: PairingSnapshot,
        val qr: PairingQr,
        val session: PairingSession,
        val alias: String,
        val stage: (PairingStage) -> Unit
    ) : AutoCloseable {
        var saved = false
        private var handshake: PairingHandshake? = null

        suspend fun run() = withTimeout(remaining(session)) {
            renewal.withAccess(CredentialAccess.Management) { identity ->
                requireOwner(initial)
                try {
                    service.connect(
                        identity.management.token,
                        session.sessionId
                    ).use { connection ->
                        connection.events.first { event -> handle(event, connection) }
                    }
                } catch (failure: PairingServiceException) {
                    if (failure.failure == PairingFailure.LoginRequired) {
                        renewal.rejectAccess(identity, RefreshState.LoginRequired)
                    }
                    throw failure
                }
            }
        }

        private suspend fun handle(event: PairingEvent, connection: PairingConnection): Boolean {
            requireOwner(initial)
            return when (event) {
                is PairingEvent.State -> {
                    validate(event.session, session)
                    if (isActive(event.session)) {
                        if (!saved) unavailable()
                        true
                    } else {
                        start(connection)
                        false
                    }
                }

                is PairingEvent.Relay -> confirm(event, connection)
            }
        }

        private fun start(connection: PairingConnection) {
            if (handshake != null) return
            stage(PairingStage.Identity)
            val exchange = crypto.start(qr)
            handshake = exchange
            stage(PairingStage.Key)
            connection.send(exchange.init)
        }

        private suspend fun confirm(
            event: PairingEvent.Relay,
            connection: PairingConnection
        ): Boolean {
            if (saved) unavailable()
            val exchange = handshake ?: unavailable()
            val confirmation = try {
                exchange.confirm(event.payload)
            } finally {
                event.payload.fill(0)
            }
            stage(PairingStage.Confirm)
            val current = requireOwner(initial)
            val pending = OwnPairing(
                session.pairingId ?: unavailable(),
                session.receiverId,
                alias,
                key = exchange.messageKey.copyOf(),
                provisionalSessionId = session.sessionId
            )
            if (!store.saveProvisional(current, pending)) unavailable()
            saved = true
            try {
                connection.send(confirmation)
            } finally {
                confirmation.fill(0)
            }
            return isActive(report(initial, session))
        }

        override fun close() {
            handshake?.close()
        }
    }

    private suspend fun report(initial: PairingSnapshot, session: PairingSession): PairingSession =
        renewal.withAccess(CredentialAccess.Management) { identity ->
            val current = requireOwner(initial)
            val pending = current.provisional.find {
                it.provisionalSessionId == session.sessionId && it.pairingId == session.pairingId &&
                    it.receiverId == session.receiverId && it.keyUsable
            } ?: unconfirmed()
            val original = initial.provisional.find { it.pairingId == pending.pairingId }
            if (original != null && !checkNotNull(pending.key).contentEquals(original.key)) {
                unconfirmed()
            }
            try {
                service.complete(identity.management.token, session.sessionId).also {
                    validate(it, session)
                }
            } catch (failure: PairingServiceException) {
                if (failure.failure == PairingFailure.LoginRequired) {
                    renewal.rejectAccess(identity, RefreshState.LoginRequired)
                }
                throw failure
            }
        }

    private suspend fun requireOwner(initial: PairingSnapshot): PairingSnapshot {
        val current = store.readPairings()
        val identity = current.authority.identity ?: unavailable()
        val original = initial.authority.identity ?: unavailable()
        if (identity.accountId != original.accountId || identity.senderId != original.senderId ||
            current.authority.refreshState != RefreshState.Active
        ) {
            unavailable()
        }
        return current
    }

    private fun validate(current: PairingSession, initial: PairingSession) {
        if (current.sessionId != initial.sessionId || current.receiverId != initial.receiverId ||
            current.senderId != initial.senderId || current.pairingId != initial.pairingId ||
            current.mode != initial.mode || current.expiresAt != initial.expiresAt
        ) {
            unavailable()
        }
        if (current.mode != "new_receiver" && current.mode != "add_sender") unavailable()
        if (!isActive(current) &&
            (
                current.state != "awaiting_confirmation" || !current.expiresAt.isAfter(
                    clock.instant()
                )
                )
        ) {
            throw PairingEntryException(PairingEntryProblem.Closed)
        }
    }

    private fun remaining(session: PairingSession): Long =
        (session.expiresAt.toEpochMilli() - clock.millis()).coerceAtLeast(1)

    private fun isActive(session: PairingSession): Boolean = session.senderConfirmed &&
        session.receiverConfirmed && when (session.mode) {
            "new_receiver" ->
                session.state in
                    setOf("credential_ready", "credential_issued", "completed")

            "add_sender" -> session.state == "completed"

            else -> false
        }

    private fun unconfirmed(): Nothing =
        throw PairingEntryException(PairingEntryProblem.Unconfirmed)

    private fun unavailable(): Nothing =
        throw PairingEntryException(PairingEntryProblem.Unavailable)
}
