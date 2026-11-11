package com.buddylabs.pinglide.domain.identity

internal data class RegistrationClosure(
    val destination: RefreshState,
    val terminalSnapshot: IdentitySnapshot?
)

internal class RegistrationClosures {
    @Volatile
    private var closed = emptyMap<Pair<String, String>, RegistrationClosure>()
    private var selfRetry: SenderSelection? = null

    @Synchronized
    fun retry(selection: SenderSelection) {
        selfRetry = selection.takeIf { it.senderId == it.owner.senderId }
    }

    @Synchronized
    fun finish(selection: SenderSelection) {
        if (selfRetry === selection) selfRetry = null
    }

    @Synchronized
    fun clear() {
        closed = emptyMap()
        selfRetry = null
    }

    operator fun get(identity: SenderIdentity): RegistrationClosure? =
        closed[identity.accountId to identity.senderId]

    fun retainsSelfSnapshot(snapshot: IdentitySnapshot): Boolean = closed.values.any {
        it.destination == RefreshState.RegistrationClosed && it.terminalSnapshot == snapshot
    }

    @Synchronized
    fun retain(
        identity: SenderIdentity,
        destination: RefreshState,
        terminalSnapshot: IdentitySnapshot?,
        session: Long
    ) {
        val self = selfRetry?.takeIf {
            it.owner.accountId == identity.accountId && it.owner.senderId == identity.senderId &&
                it.owner.session == session
        }
        val resolved = if (destination == RefreshState.RegistrationRevoked && self != null) {
            RefreshState.RegistrationClosed
        } else {
            destination
        }
        val owner = identity.accountId to identity.senderId
        val retained = closed[owner]
        closed = closed + (
            owner to RegistrationClosure(
                retained?.destination?.takeIf { it == RefreshState.RegistrationClosed } ?: resolved,
                retained?.terminalSnapshot ?: terminalSnapshot
            )
            )
    }
}
