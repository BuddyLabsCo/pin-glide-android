package com.buddylabs.pinglide.domain.identity

import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val clock: Clock = Clock.systemUTC(),
    private val onAuthorityReset: () -> Unit = {}
) {
    private val gate = Mutex()
    private var flight: Deferred<IdentitySnapshot>? = null
    private var logoutFlight: Deferred<RefreshCredential?>? = null
    private var closureCommit = LoginAttempt()

    @Volatile
    private var loggedOut = false

    @Volatile
    var sessionGeneration: Long = 0
        private set

    private val closures = RegistrationClosures()
    private val mutableState = MutableStateFlow(AuthorizationState.Checking)
    val state = mutableState.asStateFlow()
    private val mutableLogoutState = MutableStateFlow(LogoutState.Ready)
    val logoutState = mutableLogoutState.asStateFlow()

    @Synchronized
    fun logout(store: LogoutStore): Deferred<RefreshCredential?> {
        logoutFlight?.takeUnless { it.isCompleted }?.let { return it }
        closureCommit.cancel()
        loggedOut = true
        closures.clear()
        sessionGeneration++
        onAuthorityReset()
        flight = null
        publish(AuthorizationState.LoggedOut)
        mutableLogoutState.value = LogoutState.Pending
        return scope.async(start = CoroutineStart.UNDISPATCHED) {
            gate.withLock {
                try {
                    store.logout().also { mutableLogoutState.value = LogoutState.Ready }
                } catch (_: IdentityStorageException) {
                    mutableLogoutState.value = LogoutState.Failed
                    null
                }
            }
        }.also { logoutFlight = it }
    }

    @Synchronized
    fun resumeSession() {
        sessionGeneration++
        onAuthorityReset()
        closureCommit = LoginAttempt()
        loggedOut = false
        logoutFlight = null
        publish(AuthorizationState.Checking)
    }

    fun finishLogout(operation: Deferred<RefreshCredential?>) {
        if (logoutFlight === operation) logoutFlight = null
    }

    fun isSessionCurrent(generation: Long): Boolean = !loggedOut && generation == sessionGeneration

    private fun requireSession(session: Long) {
        if (!isSessionCurrent(session)) throw AuthorizationException(AuthorizationState.LoggedOut)
    }

    suspend fun recover() {
        try {
            authorize(CredentialAccess.Management)
        } catch (_: AuthorizationException) {
            return
        }
    }

    suspend fun rejectAccess(
        identity: SenderIdentity,
        failure: RefreshState,
        session: Long = sessionGeneration
    ) {
        withContext(NonCancellable) {
            gate.withLock {
                if (!isSessionCurrent(session)) return@withLock
                retainRevocation(identity, failure, session = session)
                val current = store.read()
                if (!isSessionCurrent(session)) return@withLock
                if (current.identity?.accountId != identity.accountId ||
                    current.identity.senderId != identity.senderId
                ) {
                    return@withLock
                }
                if (failure == RefreshState.RegistrationRevoked) {
                    closeRegistration(current, identity)
                    requireAuthority(store.read())
                    return@withLock
                }
                if (current.identity.management.token != identity.management.token) {
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
        val session = sessionGeneration
        val authorized = authorize(access)
        return try {
            execute(authorized, session, work)
        } catch (_: AccessExpiredException) {
            requireSession(session)
            val renewed = authorize(access, authorized.generation)
            execute(renewed, session, work)
        }
    }

    private suspend fun <Result> execute(
        authorized: IdentitySnapshot,
        session: Long,
        work: suspend (SenderIdentity) -> Result
    ): Result {
        requireSession(session)
        val identity = checkNotNull(authorized.identity)
        val result = try {
            work(identity)
        } catch (_: CurrentSenderRevokedException) {
            requireSession(session)
            rejectAccess(identity, RefreshState.RegistrationRevoked, session)
            throw AuthorizationException(AuthorizationState.TemporarilyUnavailable)
        }
        gate.withLock {
            val current = store.read()
            requireAuthority(current)
            if (!isSessionCurrent(session) || current.generation != authorized.generation ||
                current.identity?.senderId != identity.senderId ||
                current.identity.accountId != identity.accountId
            ) {
                throw AuthorizationException(AuthorizationState.TemporarilyUnavailable)
            }
        }
        return result
    }

    suspend fun confirmSelfRevocation(identity: SenderIdentity, session: Long) {
        withContext(NonCancellable) {
            gate.withLock {
                if (!isSessionCurrent(session)) return@withLock
                retainRevocation(identity, RefreshState.RegistrationClosed, session = session)
                val current = store.read()
                if (!isSessionCurrent(session)) return@withLock
                retainRevocation(
                    identity,
                    RefreshState.RegistrationClosed,
                    current.takeIf {
                        it.refreshState == RefreshState.RegistrationRevoked &&
                            identity.senderId in it.closedSenderIds
                    }
                )
                readAuthority()
            }
        }
    }

    suspend fun beginRegistration(): IdentitySnapshot {
        logoutFlight?.await()
        if (logoutState.value == LogoutState.Failed) throw IdentityStorageException()
        return gate.withLock {
            val current = readAuthority()
            if (current.refreshState == RefreshState.LoggedOut) {
                publish(AuthorizationState.LoginRequired)
                return@withLock current
            }
            if (!current.refreshState.closesRegistration) throw IdentityStorageException()
            val next = current.copy(
                generation = Math.addExact(current.generation, 1),
                refreshState = RefreshState.Active
            )
            if (!store.updateRenewal(current, next)) throw IdentityStorageException()
            publish(AuthorizationState.LoginRequired)
            next
        }
    }

    fun isRegistrationClosed(identity: SenderIdentity): Boolean = closures[identity] != null

    @Synchronized
    fun retrySelfRevocation(selection: SenderSelection) {
        if (isSessionCurrent(selection.owner.session)) closures.retry(selection)
    }

    fun finishSelfRevocation(selection: SenderSelection) = closures.finish(selection)

    @Synchronized
    private fun retainRevocation(
        identity: SenderIdentity,
        destination: RefreshState,
        terminalSnapshot: IdentitySnapshot? = null,
        session: Long = sessionGeneration
    ) {
        if (!isSessionCurrent(session) || !destination.closesRegistration) return
        closures.retain(identity, destination, terminalSnapshot, session)
        onAuthorityReset()
    }

    private suspend fun readAuthority(): IdentitySnapshot {
        val current = store.read()
        if (loggedOut) return current
        if (current.refreshState == RefreshState.RegistrationRevoked &&
            closures.retainsSelfSnapshot(current)
        ) {
            return withContext(NonCancellable) {
                val next = current.copy(
                    generation = Math.addExact(current.generation, 1),
                    refreshState = RefreshState.RegistrationClosed
                )
                if (!store.updateRenewal(current, next)) throw IdentityStorageException()
                publish(AuthorizationState.RegistrationClosed)
                next
            }
        }
        val identity = current.identity ?: return current
        if (!isRegistrationClosed(identity)) return current
        withContext(NonCancellable) { closeRegistration(current, identity) }
        return store.read()
    }

    private suspend fun closeRegistration(current: IdentitySnapshot, expected: SenderIdentity) {
        if (loggedOut || current.identity?.senderId != expected.senderId ||
            current.identity.accountId != expected.accountId
        ) {
            return
        }
        val closure = closures[expected] ?: return
        val destination = closure.destination
        val outcome = if (destination == RefreshState.RegistrationClosed) {
            AuthorizationState.RegistrationClosed
        } else {
            AuthorizationState.RegistrationRevoked
        }
        if (outcome == AuthorizationState.RegistrationRevoked) publish(outcome)
        val next = IdentitySnapshot(
            generation = Math.addExact(current.generation, 1),
            refreshState = destination,
            closedSenderIds = current.closedSenderIds + expected.senderId
        )
        retainRevocation(expected, destination, next)
        val saved = store.updateRenewal(current, next, closureCommit)
        if (loggedOut) return
        if (!saved) {
            requireAuthority(store.read())
            throw AuthorizationException(AuthorizationState.StorageUnavailable)
        }
        publish(outcome)
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
                        !current.needsRenewal(access, rejectedGeneration, clock.instant())
                    ) {
                        publish(AuthorizationState.Ready)
                        return current
                    }
                    publish(AuthorizationState.Renewing)
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

    private fun requireAuthority(snapshot: IdentitySnapshot) {
        when {
            loggedOut || snapshot.refreshState == RefreshState.LoggedOut ->
                unavailable(AuthorizationState.LoggedOut)

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
                if (current.renewal == null) publish(AuthorizationState.Ready)
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
        val session = sessionGeneration
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
            requireSession(session)
            validateReply(reply, operation, identity)
            val accepted = pending.copy(
                generation = Math.addExact(pending.generation, 1),
                identity = reply.identity,
                renewal = null
            )
            gate.withLock {
                requireAuthority(store.read())
                onAuthorityReset()
                if (store.updateRenewal(pending, accepted)) accepted else store.read()
            }
        } catch (failure: RenewalServiceException) {
            requireSession(session)
            handleFailure(pending, failure.failure, session)
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
        failure: RenewalFailure,
        session: Long
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
                rejectAccess(
                    checkNotNull(pending.identity),
                    RefreshState.RegistrationRevoked,
                    session
                )
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

    @Synchronized
    private fun publish(outcome: AuthorizationState): AuthorizationState {
        val destination = if (loggedOut) AuthorizationState.LoggedOut else outcome
        mutableState.value = destination
        return destination
    }

    private fun unavailable(outcome: AuthorizationState): Nothing =
        throw AuthorizationException(publish(outcome)).also { onAuthorityReset() }
}
