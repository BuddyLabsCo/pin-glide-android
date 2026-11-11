package com.buddylabs.pinglide.domain.identity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountSender(val senderId: String, val deviceName: String)

fun interface SenderInventoryService {
    suspend fun list(identity: SenderIdentity): List<AccountSender>
}

data class SenderInventoryOwner(
    val accountId: String,
    val senderId: String,
    val generation: Long,
    val session: Long
)

data class SenderSelection(
    val owner: SenderInventoryOwner,
    val senderId: String,
    val deviceName: String,
    val readGeneration: Long
)

data class SenderInventoryState(
    val owner: SenderInventoryOwner? = null,
    val senders: List<AccountSender>? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val fresh: Boolean = false,
    val selection: SenderSelection? = null
) {
    val canSelect: Boolean get() = fresh && !loading && !failed && owner != null && senders != null
}

class AccountSenders(
    private val store: IdentityStore,
    private val renewal: CredentialRenewal,
    private val service: SenderInventoryService,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(SenderInventoryState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var generation = 0L
    private var opened = false
    private var backgrounded = false

    init {
        scope.launch {
            renewal.state.collect {
                synchronized(this@AccountSenders) {
                    val authorization = renewal.state.value
                    if (authorization != AuthorizationState.Ready) {
                        mutableState.value = state.value.copy(fresh = false)
                    }
                    if (!renewal.isSessionCurrent(
                            state.value.owner?.session ?: renewal.sessionGeneration
                        ) ||
                        authorization == AuthorizationState.RegistrationRevoked ||
                        authorization == AuthorizationState.RegistrationClosed
                    ) {
                        close()
                    }
                }
            }
        }
    }

    @Synchronized
    fun open() {
        if (opened) return
        opened = true
        backgrounded = false
        refresh()
    }

    @Synchronized
    fun background() {
        backgrounded = true
        generation++
        request?.cancel()
        mutableState.value = state.value.copy(fresh = false)
    }

    @Synchronized
    fun foreground() {
        if (opened && backgrounded) {
            backgrounded = false
            refresh()
        }
    }

    @Synchronized
    fun refresh() {
        generation++
        val expected = generation
        val session = renewal.sessionGeneration
        request?.cancel()
        if (state.value.owner?.session?.let { it != session } == true) {
            mutableState.value = SenderInventoryState()
        }
        mutableState.value = state.value.copy(loading = true, failed = false, fresh = false)
        request = scope.launch { load(expected, session) }
    }

    private suspend fun load(expected: Long, session: Long) {
        try {
            val result = renewal.withAccess(CredentialAccess.Management) { identity ->
                val authority = store.read()
                check(authority.identity?.management?.token == identity.management.token)
                val owner = SenderInventoryOwner(
                    identity.accountId,
                    identity.senderId,
                    authority.generation,
                    session
                )
                owner to service.list(identity)
            }
            val current = store.read()
            synchronized(this) {
                if (expected != generation || !renewal.isSessionCurrent(session)) return
                check(matches(result.first, current))
                mutableState.value = SenderInventoryState(
                    owner = result.first,
                    senders = result.second,
                    loading = false,
                    fresh = true,
                    selection = state.value.selection?.takeIf {
                        it.owner.accountId == result.first.accountId && it.owner.session == session
                    }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            synchronized(this) {
                if (expected == generation && renewal.isSessionCurrent(session)) {
                    mutableState.value =
                        state.value.copy(loading = false, failed = true, fresh = false)
                }
            }
        }
    }

    suspend fun select(expected: SenderInventoryState, senderId: String): SenderSelection? {
        val authority = currentAuthority() ?: return null
        return synchronized(this) {
            val owner = expected.owner ?: return null
            if (state.value !== expected || !expected.canSelect) return null
            if (!matches(owner, authority)) {
                mutableState.value = expected.copy(fresh = false, failed = true)
                return null
            }
            val sender = expected.senders?.singleOrNull { it.senderId == senderId } ?: return null
            SenderSelection(owner, sender.senderId, sender.deviceName, generation).also {
                mutableState.value = expected.copy(selection = it)
            }
        }
    }

    suspend fun confirmsAbsence(selection: SenderSelection): Boolean {
        val authority = currentAuthority() ?: return false
        return synchronized(this) {
            val current = state.value
            val owner = current.owner ?: return false
            current.selection === selection && generation > selection.readGeneration &&
                current.canSelect && matches(owner, authority) &&
                owner.accountId == selection.owner.accountId &&
                owner.senderId == selection.owner.senderId &&
                owner.session == selection.owner.session && current.senders?.none {
                    it.senderId == selection.senderId
                } == true
        }
    }

    private suspend fun currentAuthority(): IdentitySnapshot? = try {
        store.read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IdentityStorageException) {
        synchronized(this) { mutableState.value = state.value.copy(fresh = false) }
        null
    }

    private fun matches(owner: SenderInventoryOwner, authority: IdentitySnapshot): Boolean =
        renewal.isSessionCurrent(
            owner.session
        ) && renewal.state.value == AuthorizationState.Ready &&
            authority.refreshState == RefreshState.Active &&
            authority.generation == owner.generation &&
            authority.identity?.accountId == owner.accountId &&
            authority.identity.senderId == owner.senderId

    @Synchronized
    fun clearSelection() {
        mutableState.value = state.value.copy(selection = null)
    }

    @Synchronized
    fun close() {
        opened = false
        backgrounded = false
        generation++
        request?.cancel()
        request = null
        mutableState.value = SenderInventoryState()
    }
}
