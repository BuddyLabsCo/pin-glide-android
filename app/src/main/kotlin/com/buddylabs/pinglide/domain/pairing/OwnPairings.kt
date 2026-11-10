package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OwnPairings(
    private val store: PairingStore,
    private val service: PairingService,
    private val renewal: CredentialRenewal,
    scope: CoroutineScope
) {
    private val refreshGate = Mutex()
    private val stateGate = Mutex()
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private var snapshot: PairingSnapshot? = null
    private var presence = emptyMap<String, RemotePairing>()
    private var presenceOwner: Pair<String, String>? = null
    private val removals = mutableSetOf<String>()

    init {
        scope.launch {
            renewal.state.collect { authorization ->
                stateGate.withLock {
                    if (authorization == AuthorizationState.LoggedOut) {
                        snapshot = null
                        presence = emptyMap()
                        presenceOwner = null
                        removals.clear()
                        mutableState.value = HomeState(authorization = authorization)
                        return@withLock
                    }
                    mutableState.value = state.value.copy(authorization = authorization)
                }
            }
        }
    }

    suspend fun refresh() {
        if (!refreshGate.tryLock()) return
        try {
            refreshCurrent()
        } finally {
            refreshGate.unlock()
        }
    }

    suspend fun synchronize() = refreshGate.withLock { refreshCurrent() }

    private suspend fun refreshCurrent() {
        try {
            stateGate.withLock {
                presence = emptyMap()
                publish(store.readPairings(), refreshing = true)
            }
            if (!fetch()) fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PairingServiceException) {
            refreshFailed()
        } catch (_: AccessExpiredException) {
            refreshFailed()
        } catch (_: AuthorizationException) {
            refreshFailed()
        } catch (_: IdentityStorageException) {
            refreshFailed()
        } finally {
            withContext(NonCancellable) {
                stateGate.withLock { mutableState.value = state.value.copy(refreshing = false) }
            }
        }
    }

    private suspend fun fetch(): Boolean =
        renewal.withAccess(CredentialAccess.Management) { identity ->
            val expected = store.readPairings()
            if (expected.authority.identity?.management?.token != identity.management.token) {
                throw PairingServiceException(PairingFailure.Unavailable)
            }
            val items = try {
                restoreAliases(
                    identity.management.token,
                    expected,
                    service.list(identity.management.token)
                )
            } catch (failure: PairingServiceException) {
                if (store.readPairings().authority.generation != expected.authority.generation) {
                    return@withAccess false
                }
                when (failure.failure) {
                    PairingFailure.LoginRequired -> renewal.rejectAccess(
                        identity,
                        RefreshState.LoginRequired
                    )

                    PairingFailure.Unavailable, PairingFailure.Gone -> Unit
                }
                throw failure
            }
            stateGate.withLock {
                val accepted = store.reconcile(expected, items)
                presence = if (accepted) items.associateBy { it.pairingId } else emptyMap()
                presenceOwner = identity.accountId to identity.senderId
                publish(store.readPairings(), refreshing = true, failed = !accepted)
                accepted
            }
        }

    private suspend fun restoreAliases(
        token: String,
        expected: PairingSnapshot,
        items: List<RemotePairing>
    ): List<RemotePairing> = items.map { remote ->
        val provisional = expected.provisional.find { it.pairingId == remote.pairingId }
        if (provisional != null && provisional.receiverId != remote.receiverId) {
            throw PairingServiceException(PairingFailure.Unavailable)
        }
        if (provisional != null && provisional.alias != remote.alias) {
            service.rename(token, remote.pairingId, provisional.alias)
        } else {
            remote
        }
    }

    suspend fun select(expectedState: HomeState, pairingId: String, enabled: Boolean) =
        stateGate.withLock {
            if (!canSelect(expectedState)) return@withLock
            if (pairingId in removals) return@withLock
            val expected = snapshot ?: return@withLock
            val owner = expected.authority.identity ?: return@withLock
            val target = expected.items.find { it.pairingId == pairingId } ?: return@withLock
            try {
                val current = store.readPairings()
                val identity = current.authority.identity
                val accepted = current.revision == expected.revision &&
                    current.authority.refreshState == expected.authority.refreshState &&
                    identity?.accountId == owner.accountId && identity.senderId == owner.senderId &&
                    current.items.any {
                        it.pairingId == pairingId && it.receiverId == target.receiverId
                    } && store.select(current, pairingId, enabled)
                publish(
                    store.readPairings(),
                    refreshing = state.value.refreshing,
                    failed = state.value.failed || !accepted
                )
            } catch (_: IdentityStorageException) {
                mutableState.value = state.value.copy(failed = true)
            }
        }

    suspend fun rename(expected: HomeState, pairingId: String, alias: String): PairingChange =
        change(expected, pairingId, alias.trim())

    private fun canSelect(expected: HomeState): Boolean =
        state.value === expected && renewal.isSessionCurrent(renewal.sessionGeneration)

    suspend fun remove(expected: HomeState, pairingId: String): PairingChange =
        change(expected, pairingId, null)

    private suspend fun change(
        expected: HomeState,
        pairingId: String,
        alias: String?
    ): PairingChange {
        var removalStarted = false
        try {
            val starting = beginChange(expected, pairingId, removing = alias == null)
                ?: return PairingChange.Unavailable
            removalStarted = alias == null
            val target = starting.items.find { it.pairingId == pairingId }
                ?: return PairingChange.Gone
            return refreshGate.withLock { performChange(starting, target, alias) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PairingServiceException) {
            return PairingChange.Unavailable
        } catch (_: AccessExpiredException) {
            return PairingChange.Unavailable
        } catch (_: AuthorizationException) {
            return PairingChange.Unavailable
        } catch (_: IdentityStorageException) {
            return PairingChange.Unavailable
        } finally {
            if (removalStarted) {
                withContext(NonCancellable) {
                    stateGate.withLock { removals.remove(pairingId) }
                }
            }
        }
    }

    private suspend fun beginChange(
        expected: HomeState,
        pairingId: String,
        removing: Boolean
    ): PairingSnapshot? = stateGate.withLock {
        if (state.value !== expected) return@withLock null
        if (pairingId in removals) return@withLock null
        val displayed = snapshot ?: return@withLock null
        val target = displayed.items.find { it.pairingId == pairingId } ?: return@withLock null
        val current = store.readPairings()
        if (current.authority.identity?.let { it.accountId to it.senderId } != expected.owner ||
            current.revision != displayed.revision || !current.contains(target)
        ) {
            return@withLock null
        }
        if (removing && !store.select(current, pairingId, false)) return@withLock null
        readCurrent().also { if (removing) removals.add(pairingId) }
    }

    private suspend fun performChange(
        starting: PairingSnapshot,
        target: OwnPairing,
        alias: String?
    ): PairingChange = renewal.withAccess(CredentialAccess.Management) { identity ->
        val requested = store.readPairings()
        if (requested.authority.identity?.management?.token != identity.management.token ||
            !requested.sameOwner(starting) || !requested.contains(target)
        ) {
            return@withAccess PairingChange.Unavailable
        }
        if (alias != null && !validAlias(requested, target.pairingId, alias)) {
            return@withAccess PairingChange.InvalidAlias
        }
        val updated = submitChange(identity, requested, target.pairingId, alias)
        if (updated != null && updated.alias != alias) {
            return@withAccess PairingChange.Unavailable
        }
        finishChange(requested, target, updated, alias == null)
    }

    private suspend fun submitChange(
        identity: SenderIdentity,
        requested: PairingSnapshot,
        pairingId: String,
        alias: String?
    ): RemotePairing? = try {
        if (alias == null) {
            submitRemoval(identity.management.token, pairingId)
            null
        } else {
            service.rename(identity.management.token, pairingId, alias)
        }
    } catch (failure: PairingServiceException) {
        if (failure.failure != PairingFailure.Gone || alias == null) {
            if (store.readPairings().authority.generation == requested.authority.generation) {
                when (failure.failure) {
                    PairingFailure.LoginRequired -> renewal.rejectAccess(
                        identity,
                        RefreshState.LoginRequired
                    )

                    else -> Unit
                }
            }
            throw failure
        }
        null
    }

    private suspend fun submitRemoval(managementToken: String, pairingId: String) {
        try {
            service.remove(managementToken, pairingId)
        } catch (failure: PairingServiceException) {
            if (failure.failure != PairingFailure.Gone) throw failure
            if (service.list(managementToken).any { it.pairingId == pairingId }) {
                throw PairingServiceException(PairingFailure.Unavailable)
            }
        }
    }

    private fun PairingSnapshot.contains(target: OwnPairing): Boolean = items.any {
        it.pairingId == target.pairingId && it.receiverId == target.receiverId &&
            it.key.contentEquals(target.key)
    }

    private fun PairingSnapshot.canFinishChange(
        requested: PairingSnapshot,
        target: OwnPairing
    ): Boolean = sameOwner(requested) && authority.generation == requested.authority.generation &&
        authority.refreshState == requested.authority.refreshState && contains(target) &&
        items.first { it.pairingId == target.pairingId }.alias ==
        requested.items.first { it.pairingId == target.pairingId }.alias

    private suspend fun finishChange(
        requested: PairingSnapshot,
        target: OwnPairing,
        updated: RemotePairing?,
        removing: Boolean
    ): PairingChange = stateGate.withLock {
        val current = store.readPairings()
        if (!current.canFinishChange(requested, target)) {
            return@withLock PairingChange.Unavailable
        }
        if (updated != null &&
            (updated.pairingId != target.pairingId || updated.receiverId != target.receiverId)
        ) {
            return@withLock PairingChange.Unavailable
        }
        val items = current.items.mapNotNull {
            if (it.pairingId == target.pairingId) {
                updated
            } else {
                RemotePairing(it.pairingId, it.receiverId, it.alias, Presence.Unknown, "")
            }
        }
        if (!store.reconcile(current, items)) return@withLock PairingChange.Unavailable
        presence =
            if (updated ==
                null
            ) {
                presence - target.pairingId
            } else {
                presence + (target.pairingId to updated)
            }
        readCurrent()
        when {
            removing -> PairingChange.Removed
            updated == null -> PairingChange.Gone
            else -> PairingChange.Renamed
        }
    }

    private fun validAlias(current: PairingSnapshot, pairingId: String, alias: String): Boolean =
        validPairingAlias(
            alias,
            current.items.filter {
                it.pairingId != pairingId
            }.map { it.alias }
        )

    private fun PairingSnapshot.sameOwner(other: PairingSnapshot): Boolean =
        authority.identity?.accountId == other.authority.identity?.accountId &&
            authority.identity?.senderId == other.authority.identity?.senderId

    suspend fun forwardingTargets(initial: PairingSnapshot? = null): PairingSnapshot =
        stateGate.withLock {
            val current = readCurrent()
            val eligible = current.items.filter { target ->
                target.enabled && target.keyUsable &&
                    (initial == null || initial.items.any { it.sameKey(target) })
            }
            PairingSnapshot(
                current.authority,
                current.revision,
                current.listed,
                eligible.takeIf { current.canSend(initial) }.orEmpty()
            )
        }

    suspend fun testTarget(expected: HomeState, pairingId: String): OwnPairing? =
        stateGate.withLock {
            if (state.value !== expected) return@withLock null
            val initial = snapshot ?: return@withLock null
            val current = readCurrent()
            current.items.find { target ->
                current.canSend(initial) && target.pairingId == pairingId && target.keyUsable &&
                    initial.items.any { it.sameKey(target) }
            }
        }

    private suspend fun readCurrent(): PairingSnapshot {
        val current = store.readPairings()
        publish(current, refreshing = state.value.refreshing, failed = state.value.failed)
        return current
    }

    private fun OwnPairing.sameKey(other: OwnPairing): Boolean =
        pairingId == other.pairingId && receiverId == other.receiverId &&
            keyUsable && key.contentEquals(other.key)

    private fun PairingSnapshot.canSend(initial: PairingSnapshot?): Boolean =
        authority.identity != null && authority.refreshState == RefreshState.Active &&
            renewal.isSessionCurrent(renewal.sessionGeneration) &&
            !renewal.isRegistrationClosed(authority.identity) &&
            renewal.state.value !in setOf(
                AuthorizationState.RegistrationRevoked,
                AuthorizationState.RegistrationClosed,
                AuthorizationState.LoggedOut,
                AuthorizationState.StorageUnavailable
            ) &&
            (
                initial == null ||
                    (sameOwner(initial) && authority.generation == initial.authority.generation)
                )

    private suspend fun refreshFailed() = stateGate.withLock {
        presence = emptyMap()
        try {
            publish(store.readPairings(), failed = true)
        } catch (_: IdentityStorageException) {
            mutableState.value = state.value.copy(
                failed = true,
                targets = state.value.targets.map {
                    it.copy(presence = Presence.Unknown, stateChangedAt = null)
                }
            )
        }
    }

    private fun publish(
        current: PairingSnapshot,
        refreshing: Boolean = false,
        failed: Boolean = false
    ) {
        if (renewal.state.value == AuthorizationState.LoggedOut) {
            snapshot = null
            presence = emptyMap()
            presenceOwner = null
            mutableState.value = HomeState(authorization = AuthorizationState.LoggedOut)
            return
        }
        snapshot = current
        mutableState.value = HomeState(
            targets = current.items.map { pairing ->
                val owner = current.authority.identity
                val remote = presence[pairing.pairingId]?.takeIf {
                    it.receiverId == pairing.receiverId &&
                        presenceOwner == (owner?.accountId to owner?.senderId)
                }
                TargetDevice(
                    pairing.pairingId,
                    pairing.alias,
                    pairing.enabled && pairing.keyUsable,
                    pairing.keyUsable,
                    remote?.presence ?: Presence.Unknown,
                    remote?.stateChangedAt
                )
            },
            listed = current.listed,
            refreshing = refreshing,
            failed = failed,
            authorization = renewal.state.value,
            generation = current.authority.generation,
            owner = current.authority.identity?.let { it.accountId to it.senderId }
        )
    }
}
