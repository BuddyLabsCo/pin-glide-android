package com.buddylabs.pinglide.domain.identity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class RevocationReply { Revoked, CurrentSenderRevoked, WrongAccount, ProofFailed, Unconfirmed }

fun interface SenderRevocationService {
    suspend fun revoke(identity: SenderIdentity, senderId: String, code: String): RevocationReply
}

enum class RevocationStep {
    Confirmation,
    Proof,
    Verifying,
    Submitting,
    Unconfirmed,
    Retrying,
    Revoked
}

enum class OwnerProofNotice { None, Cancelled, Failed, WrongAccount }

data class SenderRevocationState(
    val selection: SenderSelection,
    val step: RevocationStep = RevocationStep.Confirmation,
    val notice: OwnerProofNotice = OwnerProofNotice.None
)

class SenderRevocation(
    private val store: IdentityStore,
    private val inventory: AccountSenders,
    private val renewal: CredentialRenewal,
    private val authorization: QqAuthorization,
    private val service: SenderRevocationService,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow<SenderRevocationState?>(null)
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var proofCode: String? = null

    init {
        scope.launch {
            renewal.state.collect {
                synchronized(this@SenderRevocation) {
                    val selection = state.value?.selection ?: return@collect
                    if (!renewal.isSessionCurrent(selection.owner.session)) clear()
                }
            }
        }
    }

    @Synchronized
    fun select(selection: SenderSelection) {
        if (state.value != null || inventory.state.value.selection !== selection ||
            !renewal.isSessionCurrent(selection.owner.session)
        ) {
            return
        }
        mutableState.value = SenderRevocationState(selection)
    }

    @Synchronized
    fun confirm(expected: SenderRevocationState? = state.value) {
        val current = state.value ?: return
        if (current !== expected) return
        if (current.step == RevocationStep.Confirmation) {
            mutableState.value = current.copy(step = RevocationStep.Proof)
        }
    }

    @Synchronized
    fun verify(expected: SenderRevocationState? = state.value) {
        val current = state.value ?: return
        if (current !== expected) return
        if (current.step != RevocationStep.Proof) return
        mutableState.value = current.copy(
            step = RevocationStep.Verifying,
            notice = OwnerProofNotice.None
        )
        request = scope.launch { verifyAndSubmit(current.selection) }
    }

    private suspend fun verifyAndSubmit(selection: SenderSelection) {
        try {
            val snapshot = withTimeout(30_000) { renewal.authorize(CredentialAccess.Management) }
            val identity = checkNotNull(snapshot.identity)
            check(identity.accountId == selection.owner.accountId)
            check(identity.senderId == selection.owner.senderId)
            if (!isCurrent(selection)) return
            val code = authorization.authorize()
            if (!isCurrent(selection)) return
            require(code.isNotBlank() && code.length <= 512)
            val current = store.read()
            check(current.generation == snapshot.generation)
            check(current.identity?.management?.token == identity.management.token)
            check(current.refreshState == RefreshState.Active)
            check(!renewal.isRegistrationClosed(identity))
            synchronized(this) {
                if (!transition(selection, RevocationStep.Submitting)) return
                proofCode = code
            }
            val reply = withTimeout(30_000) { service.revoke(identity, selection.senderId, code) }
            acceptReply(selection, identity, reply)
        } catch (_: TimeoutCancellationException) {
            failed(selection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: QqAuthorizationException) {
            proofFailed(
                selection,
                if (failure.cancelled) OwnerProofNotice.Cancelled else OwnerProofNotice.Failed
            )
        } catch (_: Exception) {
            failed(selection)
        }
    }

    @Synchronized
    fun retry(expected: SenderRevocationState) {
        if (state.value !== expected || expected.step != RevocationStep.Unconfirmed) return
        val code = proofCode ?: return
        if (!transition(expected.selection, RevocationStep.Retrying)) return
        renewal.retrySelfRevocation(expected.selection)
        request = scope.launch { retryAndSubmit(expected.selection, code) }
    }

    private suspend fun retryAndSubmit(selection: SenderSelection, code: String) {
        try {
            val read = withTimeout(30_000) { inventory.reload(selection) }
            if (!isCurrent(selection)) return
            if (read == null) {
                transition(selection, RevocationStep.Unconfirmed)
                return
            }
            val snapshot = store.read()
            val identity = checkNotNull(snapshot.identity)
            check(snapshot.generation == read.owner?.generation)
            check(identity.accountId == selection.owner.accountId)
            check(identity.senderId == selection.owner.senderId)
            check(inventory.reconciled(selection) === read)
            if (!isCurrent(selection)) return
            if (checkNotNull(read.senders).none { it.senderId == selection.senderId }) {
                complete(selection, identity)
            } else {
                val reply =
                    withTimeout(30_000) { service.revoke(identity, selection.senderId, code) }
                acceptReply(selection, identity, reply)
            }
        } catch (_: TimeoutCancellationException) {
            transition(selection, RevocationStep.Unconfirmed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            transition(selection, RevocationStep.Unconfirmed)
        }
    }

    private suspend fun acceptReply(
        selection: SenderSelection,
        identity: SenderIdentity,
        reply: RevocationReply
    ) {
        if (!isCurrent(selection)) return
        val retrying = state.value?.step == RevocationStep.Retrying
        if (retrying &&
            (reply == RevocationReply.WrongAccount || reply == RevocationReply.ProofFailed)
        ) {
            transition(selection, RevocationStep.Unconfirmed)
            return
        }
        when (reply) {
            RevocationReply.Revoked -> complete(selection, identity)

            RevocationReply.CurrentSenderRevoked -> try {
                if (retrying && selection.senderId == selection.owner.senderId) {
                    renewal.confirmSelfRevocation(identity, selection.owner.session)
                } else {
                    renewal.rejectAccess(
                        identity,
                        RefreshState.RegistrationRevoked,
                        selection.owner.session
                    )
                }
            } finally {
                synchronized(this) {
                    if (isCurrent(selection)) clear()
                }
            }

            RevocationReply.WrongAccount -> proofFailed(selection, OwnerProofNotice.WrongAccount)

            RevocationReply.ProofFailed -> proofFailed(selection, OwnerProofNotice.Failed)

            RevocationReply.Unconfirmed -> transition(selection, RevocationStep.Unconfirmed)
        }
    }

    private suspend fun complete(selection: SenderSelection, identity: SenderIdentity) {
        if (selection.senderId == selection.owner.senderId) {
            renewal.confirmSelfRevocation(identity, selection.owner.session)
            synchronized(this) {
                if (isCurrent(selection)) clear()
            }
        } else {
            synchronized(this) {
                if (!transition(selection, RevocationStep.Revoked)) return
                proofCode = null
                inventory.refresh()
            }
        }
    }

    @Synchronized
    private fun failed(selection: SenderSelection) {
        if (!isCurrent(selection)) return
        if (state.value?.step == RevocationStep.Submitting) {
            transition(selection, RevocationStep.Unconfirmed)
        } else {
            proofFailed(selection, OwnerProofNotice.Failed)
        }
    }

    @Synchronized
    private fun proofFailed(selection: SenderSelection, notice: OwnerProofNotice) {
        if (!isCurrent(selection)) return
        proofCode = null
        mutableState.value = SenderRevocationState(selection, RevocationStep.Proof, notice)
    }

    @Synchronized
    private fun isCurrent(selection: SenderSelection): Boolean =
        state.value?.selection === selection && renewal.isSessionCurrent(selection.owner.session)

    @Synchronized
    private fun transition(selection: SenderSelection, step: RevocationStep): Boolean {
        if (!isCurrent(selection)) return false
        if (step != RevocationStep.Retrying) renewal.finishSelfRevocation(selection)
        mutableState.value = SenderRevocationState(selection, step)
        return true
    }

    @Synchronized
    fun dismiss(expected: SenderRevocationState? = state.value) {
        if (state.value !== expected) return
        if (state.value?.step == RevocationStep.Submitting ||
            state.value?.step == RevocationStep.Retrying
        ) {
            return
        }
        clear()
        inventory.refresh()
    }

    @Synchronized
    fun close() {
        clear()
    }

    @Synchronized
    private fun clear() {
        state.value?.selection?.let(renewal::finishSelfRevocation)
        mutableState.value = null
        proofCode = null
        request?.cancel()
        request = null
        inventory.clearSelection()
    }
}
