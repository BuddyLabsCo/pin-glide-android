package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.RefreshState
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

    init {
        scope.launch {
            renewal.state.collect { authorization ->
                stateGate.withLock {
                    mutableState.value = state.value.copy(authorization = authorization)
                }
            }
        }
    }

    suspend fun refresh() {
        if (!refreshGate.tryLock()) return
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
                try {
                    stateGate.withLock { mutableState.value = state.value.copy(refreshing = false) }
                } finally {
                    refreshGate.unlock()
                }
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
                service.list(identity.management.token)
            } catch (failure: PairingServiceException) {
                if (store.readPairings().authority.generation != expected.authority.generation) {
                    return@withAccess false
                }
                when (failure.failure) {
                    PairingFailure.Revoked -> renewal.rejectAccess(
                        identity,
                        RefreshState.RegistrationRevoked
                    )

                    PairingFailure.LoginRequired -> renewal.rejectAccess(
                        identity,
                        RefreshState.LoginRequired
                    )

                    PairingFailure.Unavailable -> Unit
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

    suspend fun select(expectedState: HomeState, pairingId: String, enabled: Boolean) =
        stateGate.withLock {
            if (state.value !== expectedState) return@withLock
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
            authorization = renewal.state.value
        )
    }
}
