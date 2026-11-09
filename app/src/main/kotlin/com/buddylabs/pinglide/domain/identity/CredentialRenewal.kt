package com.buddylabs.pinglide.domain.identity

import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CredentialRenewal(
    private val store: IdentityStore,
    private val service: RenewalService,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) {
    private val gate = Mutex()
    private var flight: Deferred<IdentitySnapshot>? = null
    private val mutableState = MutableStateFlow(AuthorizationState.Checking)
    val state = mutableState.asStateFlow()

    suspend fun recover() {
        try {
            authorize(CredentialAccess.Management)
        } catch (_: AuthorizationException) {
            return
        }
    }

    suspend fun rejectAccess(identity: SenderIdentity, failure: RefreshState) {
        gate.withLock {
            val current = store.read()
            if (current.identity?.management?.token != identity.management.token) return@withLock
            if (current.refreshState == RefreshState.RegistrationRevoked) requireAuthority(current)
            require(
                failure == RefreshState.LoginRequired || failure == RefreshState.RegistrationRevoked
            )
            val updated = invalidate(current, failure)
            requireAuthority(updated)
        }
    }

    suspend fun <Result> withAccess(
        access: CredentialAccess,
        work: suspend (SenderIdentity) -> Result
    ): Result {
        val authorized = authorize(access)
        return try {
            work(checkNotNull(authorized.identity))
        } catch (_: AccessExpiredException) {
            val renewed = authorize(access, authorized.generation)
            work(checkNotNull(renewed.identity))
        }
    }

    suspend fun authorize(
        access: CredentialAccess,
        rejectedGeneration: Long? = null
    ): IdentitySnapshot {
        try {
            while (true) {
                val pending = gate.withLock {
                    val current = store.read()
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
            snapshot.refreshState == RefreshState.RegistrationRevoked ->
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
        val pending = IdentitySnapshot(original.generation, identity, operation)
        if (!store.updateRenewal(original, pending)) return store.read()
        return send(pending)
    }

    private suspend fun send(pending: IdentitySnapshot): IdentitySnapshot {
        val operation = checkNotNull(pending.renewal)
        val identity = checkNotNull(pending.identity)
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
            val accepted = IdentitySnapshot(Math.addExact(pending.generation, 1), reply.identity)
            if (store.updateRenewal(pending, accepted)) accepted else store.read()
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
        val current = store.read()
        if (current.generation != pending.generation || current.renewal != pending.renewal ||
            current.refreshState != pending.refreshState
        ) {
            return current
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

            RenewalFailure.Revoked -> invalidate(pending, RefreshState.RegistrationRevoked)
        }
    }

    private suspend fun invalidate(
        original: IdentitySnapshot,
        refreshState: RefreshState
    ): IdentitySnapshot {
        val replacement =
            IdentitySnapshot(original.generation, original.identity, original.renewal, refreshState)
        if (!store.updateRenewal(original, replacement)) return store.read()
        return replacement
    }

    private fun unavailable(outcome: AuthorizationState): Nothing {
        mutableState.value = outcome
        throw AuthorizationException(outcome)
    }
}
