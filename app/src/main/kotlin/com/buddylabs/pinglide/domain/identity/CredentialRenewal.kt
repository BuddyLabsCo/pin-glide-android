package com.buddylabs.pinglide.domain.identity

import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CredentialRenewal(
    private val store: IdentityStore,
    private val service: RenewalService,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) {
    private val gate = Mutex()
    private var flight: Deferred<IdentitySnapshot>? = null

    @Volatile
    private var closedSenders = emptyMap<Pair<String, String>, RegistrationClosure>()
    private val mutableState = MutableStateFlow(AuthorizationState.Checking)
    val state = mutableState.asStateFlow()

    private data class RegistrationClosure(
        val destination: RefreshState,
        val terminalSnapshot: IdentitySnapshot?
    )

    suspend fun recover() {
        try {
            authorize(CredentialAccess.Management)
        } catch (_: AuthorizationException) {
            return
        }
    }

    suspend fun rejectAccess(identity: SenderIdentity, failure: RefreshState) {
        withContext(NonCancellable) {
            gate.withLock {
                if (failure == RefreshState.RegistrationRevoked) {
                    retainRevocation(identity, failure)
                }
                val current = store.read()
                if (failure == RefreshState.RegistrationRevoked) {
                    closeRegistration(current, identity)
                    requireAuthority(store.read())
                    return@withLock
                }
                if (current.identity?.management?.token !=
                    identity.management.token
                ) {
                    return@withLock
                }
                requireAuthority(current)
                require(failure == RefreshState.LoginRequired)
                val replacement = current.copy(refreshState = failure)
                val saved = store.updateRenewal(current, replacement)
                requireAuthority(if (saved) replacement else store.read())
            }
        }
    }

    suspend fun <Result> withAccess(
        access: CredentialAccess,
        work: suspend (SenderIdentity) -> Result
    ): Result {
        val authorized = authorize(access)
        return try {
            execute(authorized, work)
        } catch (_: AccessExpiredException) {
            val renewed = authorize(access, authorized.generation)
            execute(renewed, work)
        }
    }

    private suspend fun <Result> execute(
        authorized: IdentitySnapshot,
        work: suspend (SenderIdentity) -> Result
    ): Result {
        val identity = checkNotNull(authorized.identity)
        val result = try {
            work(identity)
        } catch (_: CurrentSenderRevokedException) {
            rejectAccess(identity, RefreshState.RegistrationRevoked)
            throw AuthorizationException(AuthorizationState.TemporarilyUnavailable)
        }
        gate.withLock {
            val current = store.read()
            requireAuthority(current)
            if (current.identity?.senderId != identity.senderId ||
                current.identity.accountId != identity.accountId
            ) {
                throw AuthorizationException(AuthorizationState.TemporarilyUnavailable)
            }
        }
        return result
    }

    suspend fun confirmSelfRevocation(expected: IdentitySnapshot, revokedSenderId: String) {
        val identity = checkNotNull(expected.identity)
        if (revokedSenderId != identity.senderId) return
        withContext(NonCancellable) {
            gate.withLock {
                retainRevocation(
                    identity,
                    RefreshState.RegistrationClosed,
                    IdentitySnapshot(
                        generation = Math.addExact(expected.generation, 1),
                        refreshState = RefreshState.RegistrationRevoked,
                        closedSenderIds = expected.closedSenderIds + revokedSenderId
                    )
                )
                readAuthority()
            }
        }
    }

    suspend fun beginRegistration(): IdentitySnapshot = gate.withLock {
        val current = readAuthority()
        if (!current.refreshState.closesRegistration) throw IdentityStorageException()
        val next = current.copy(
            generation = Math.addExact(current.generation, 1),
            refreshState = RefreshState.Active
        )
        if (!store.updateRenewal(current, next)) throw IdentityStorageException()
        mutableState.value = AuthorizationState.LoginRequired
        next
    }

    fun isRegistrationClosed(identity: SenderIdentity): Boolean =
        (identity.accountId to identity.senderId) in closedSenders

    private fun retainRevocation(
        identity: SenderIdentity,
        destination: RefreshState,
        terminalSnapshot: IdentitySnapshot? = null
    ) {
        val owner = identity.accountId to identity.senderId
        val retained = closedSenders[owner]
        if (retained?.destination != RefreshState.RegistrationClosed) {
            val closure = RegistrationClosure(
                destination,
                retained?.terminalSnapshot ?: terminalSnapshot
            )
            closedSenders = closedSenders + (owner to closure)
        }
    }

    private suspend fun readAuthority(): IdentitySnapshot {
        val current = store.read()
        if (current.refreshState == RefreshState.RegistrationRevoked &&
            closedSenders.values.any {
                it.destination == RefreshState.RegistrationClosed && it.terminalSnapshot == current
            }
        ) {
            return withContext(NonCancellable) {
                val next = current.copy(
                    generation = Math.addExact(current.generation, 1),
                    refreshState = RefreshState.RegistrationClosed
                )
                if (!store.updateRenewal(current, next)) throw IdentityStorageException()
                mutableState.value = AuthorizationState.RegistrationClosed
                next
            }
        }
        val identity = current.identity ?: return current
        if (!isRegistrationClosed(identity)) return current
        withContext(NonCancellable) { closeRegistration(current, identity) }
        return store.read()
    }

    private suspend fun closeRegistration(current: IdentitySnapshot, expected: SenderIdentity) {
        if (current.identity?.senderId != expected.senderId ||
            current.identity.accountId != expected.accountId
        ) {
            return
        }
        mutableState.value = AuthorizationState.RegistrationRevoked
        val owner = expected.accountId to expected.senderId
        val closure = checkNotNull(closedSenders[owner])
        val destination = closure.destination
        val next = IdentitySnapshot(
            generation = Math.addExact(current.generation, 1),
            refreshState = destination,
            closedSenderIds = current.closedSenderIds + expected.senderId
        )
        closedSenders = closedSenders + (owner to closure.copy(terminalSnapshot = next))
        if (!store.updateRenewal(current, next)) {
            requireAuthority(store.read())
            throw AuthorizationException(AuthorizationState.StorageUnavailable)
        }
        mutableState.value = if (destination == RefreshState.RegistrationClosed) {
            AuthorizationState.RegistrationClosed
        } else {
            AuthorizationState.RegistrationRevoked
        }
    }

    suspend fun authorize(
        access: CredentialAccess,
        rejectedGeneration: Long? = null
    ): IdentitySnapshot {
        try {
            while (true) {
                val pending = gate.withLock {
                    val current = readAuthority()
                    requireAuthority(current)
                    if (current.renewal == null &&
                        !needsRenewal(current, access, rejectedGeneration)
                    ) {
                        mutableState.value = AuthorizationState.Ready
                        return current
                    }
                    mutableState.value = AuthorizationState.Renewing
                    flight?.takeUnless { it.isCompleted } ?: scope.async {
                        runRenewal(current)
                    }.also { flight = it }
                }
                pending.await()
            }
        } catch (_: IdentityStorageException) {
            unavailable(AuthorizationState.StorageUnavailable)
        }
    }

    private fun needsRenewal(
        snapshot: IdentitySnapshot,
        access: CredentialAccess,
        rejectedGeneration: Long?
    ): Boolean {
        val identity = checkNotNull(snapshot.identity)
        val expiresAt = when (access) {
            CredentialAccess.Management -> identity.management.expiresAt
            CredentialAccess.Upload -> identity.upload.expiresAt
        }
        return snapshot.generation == rejectedGeneration ||
            !Instant.parse(expiresAt).isAfter(clock.instant().plusSeconds(300))
    }

    private fun requireAuthority(snapshot: IdentitySnapshot) {
        when {
            snapshot.refreshState == RefreshState.RegistrationClosed ->
                unavailable(AuthorizationState.RegistrationClosed)

            snapshot.refreshState == RefreshState.RegistrationRevoked ->
                unavailable(AuthorizationState.RegistrationRevoked)

            snapshot.identity?.let(::isRegistrationClosed) == true ->
                unavailable(AuthorizationState.RegistrationRevoked)

            snapshot.refreshState == RefreshState.RecoveryRequired ->
                unavailable(AuthorizationState.RecoveryRequired)

            snapshot.identity == null || snapshot.refreshState == RefreshState.LoginRequired ->
                unavailable(AuthorizationState.LoginRequired)
        }
    }

    private suspend fun runRenewal(original: IdentitySnapshot): IdentitySnapshot {
        val failure = try {
            val result = renew(original)
            gate.withLock {
                val current = store.read()
                requireAuthority(current)
                if (current.renewal == null) mutableState.value = AuthorizationState.Ready
            }
            return result
        } catch (unavailable: AuthorizationException) {
            unavailable.outcome
        } catch (_: IdentityStorageException) {
            AuthorizationState.StorageUnavailable
        }
        return gate.withLock {
            val current = try {
                store.read()
            } catch (_: IdentityStorageException) {
                unavailable(AuthorizationState.StorageUnavailable)
            }
            if (current.generation != original.generation) return@withLock current
            requireAuthority(current)
            unavailable(failure)
        }
    }

    private suspend fun renew(original: IdentitySnapshot): IdentitySnapshot {
        val identity = checkNotNull(original.identity)
        if (!Instant.parse(identity.refresh.expiresAt).isAfter(clock.instant())) {
            return invalidate(original, RefreshState.LoginRequired)
        }
        val operation = original.renewal?.let {
            if (!it.canRetransmit(clock.instant())) {
                throw AuthorizationException(AuthorizationState.RecoveryRequired)
            }
            it.copy(attempt = 1)
        } ?: RenewalOperation(
            UUID.randomUUID().toString(),
            0,
            original.generation,
            clock.instant().toString(),
            identity.refresh.expiresAt
        )
        val pending = original.copy(renewal = operation)
        val saved = gate.withLock {
            requireAuthority(store.read())
            store.updateRenewal(original, pending)
        }
        if (!saved) return store.read()
        return send(pending)
    }

    private suspend fun send(pending: IdentitySnapshot): IdentitySnapshot {
        val operation = checkNotNull(pending.renewal)
        val identity = checkNotNull(pending.identity)
        gate.withLock {
            val current = store.read()
            requireAuthority(current)
            if (current.generation != pending.generation || current.renewal != operation) {
                return current
            }
        }
        val now = clock.instant()
        if (!Instant.parse(operation.originalExpiresAt).isAfter(now)) {
            return invalidate(pending, RefreshState.LoginRequired)
        }
        if (operation.attempt == 1 && !operation.withinRecoveryWindow(now)) {
            throw AuthorizationException(AuthorizationState.RecoveryRequired)
        }
        return try {
            val reply = service.renew(identity.refresh, operation)
            validateReply(reply, operation, identity)
            val accepted = pending.copy(
                generation = Math.addExact(pending.generation, 1),
                identity = reply.identity,
                renewal = null
            )
            gate.withLock {
                requireAuthority(store.read())
                if (store.updateRenewal(pending, accepted)) accepted else store.read()
            }
        } catch (failure: RenewalServiceException) {
            handleFailure(pending, failure.failure)
        }
    }

    private fun validateReply(
        reply: RenewalReply,
        operation: RenewalOperation,
        predecessor: SenderIdentity
    ) {
        try {
            require(
                reply.operationId == operation.operationId &&
                    reply.attempt == operation.attempt.toLong()
            )
            require(
                reply.identity.accountId == predecessor.accountId &&
                    reply.identity.senderId == predecessor.senderId
            )
            reply.identity.validate(clock.instant().plusSeconds(300), requireFresh = true)
            require(reply.identity.refresh.token != predecessor.refresh.token)
        } catch (_: IllegalArgumentException) {
            throw RenewalServiceException(RenewalFailure.Uncertain)
        } catch (_: DateTimeException) {
            throw RenewalServiceException(RenewalFailure.Uncertain)
        }
    }

    private suspend fun handleFailure(
        pending: IdentitySnapshot,
        failure: RenewalFailure
    ): IdentitySnapshot {
        if (failure != RenewalFailure.Revoked) {
            val current = store.read()
            if (current.generation != pending.generation || current.renewal != pending.renewal ||
                current.refreshState != pending.refreshState
            ) {
                return current
            }
        }
        return when (failure) {
            RenewalFailure.Uncertain -> {
                if (checkNotNull(pending.renewal).canRetransmit(clock.instant())) {
                    renew(pending)
                } else {
                    throw AuthorizationException(AuthorizationState.TemporarilyUnavailable)
                }
            }

            RenewalFailure.Unavailable -> invalidate(pending, RefreshState.RecoveryRequired)

            RenewalFailure.RecoveryRequired -> invalidate(pending, RefreshState.RecoveryRequired)

            RenewalFailure.LoginRequired -> invalidate(pending, RefreshState.LoginRequired)

            RenewalFailure.Revoked -> {
                rejectAccess(checkNotNull(pending.identity), RefreshState.RegistrationRevoked)
                store.read()
            }
        }
    }

    private suspend fun invalidate(
        original: IdentitySnapshot,
        refreshState: RefreshState
    ): IdentitySnapshot = gate.withLock {
        requireAuthority(store.read())
        val replacement = original.copy(refreshState = refreshState)
        if (store.updateRenewal(original, replacement)) replacement else store.read()
    }

    private fun unavailable(outcome: AuthorizationState): Nothing {
        mutableState.value = outcome
        throw AuthorizationException(outcome)
    }
}
