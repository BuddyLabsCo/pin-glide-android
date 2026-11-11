package com.buddylabs.pinglide.domain.pairing

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingEntry(
    private val store: PairingStore,
    private val pairings: OwnPairings,
    private val service: PairingSessionService,
    private val decoder: PairingQrDecoder,
    private val renewal: CredentialRenewal,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
    private val activation: PairingActivation
) {
    private val mutableState = MutableStateFlow(PairingEntryState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var owner: Pair<String, String>? = null
    private var qr: PairingQr? = null
    private var session: PairingSession? = null
    private var aliases = emptyList<String>()
    private var watcher: Job? = null
    private var ticker: Job? = null
    private val releasedSessions = mutableSetOf<String>()

    suspend fun start(host: PairingHost) {
        leave()
        val snapshot = store.readPairings()
        if (snapshot.authority.identity == null) return
        if (snapshot.items.size >= 5) {
            mutableState.value =
                PairingEntryState(host = host, problem = PairingEntryProblem.Capacity)
            return
        }
        owner = snapshot.authority.identity.accountId to snapshot.authority.identity.senderId
        aliases = snapshot.items.map { it.alias }
        mutableState.value = PairingEntryState(page = PairingEntryPage.Scanner, host = host)
    }

    suspend fun scan(text: String, defaultAlias: String) {
        if (state.value.page != PairingEntryPage.Scanner || state.value.pending) return
        val scanned = try {
            decoder.decode(text)
        } catch (_: InvalidPairingQrException) {
            mutableState.value = state.value.copy(problem = PairingEntryProblem.InvalidQr)
            return
        }
        val expected = ++epoch
        mutableState.value = state.value.copy(pending = true, problem = null)
        try {
            admit(scanned, defaultAlias, expected)
        } catch (failure: PairingEntryException) {
            if (expected == epoch) fail(failure.problem)
        } catch (_: AuthorizationException) {
            if (expected == epoch) leave()
        } catch (_: IdentityStorageException) {
            if (expected == epoch) fail(PairingEntryProblem.Unavailable)
        } finally {
            scanned.clear()
        }
    }

    private suspend fun admit(scanned: PairingQr, defaultAlias: String, expected: Long) {
        pairings.synchronize()
        if (pairings.state.value.failed || !pairings.state.value.listed) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
        val snapshot = currentSnapshot() ?: return
        if (expected != epoch) return
        if (!admitTarget(snapshot, scanned.receiverId)) return
        requireReusable(scanned)
        val same = qr?.matches(scanned) == true
        if (!same) releaseCurrent()
        authorizeScan(scanned, defaultAlias, expected, same)
    }

    private fun admitTarget(snapshot: PairingSnapshot, receiverId: String): Boolean {
        val duplicate = snapshot.items.find { it.receiverId == receiverId }
        if (duplicate != null) {
            leave()
            mutableState.value = state.value.copy(
                duplicatePairingId = duplicate.pairingId,
                highlightUntil = clock.millis() + 3000
            )
            return false
        }
        if (snapshot.items.size >= 5) {
            fail(PairingEntryProblem.Capacity)
            return false
        }
        return true
    }

    private suspend fun authorizeScan(
        scanned: PairingQr,
        defaultAlias: String,
        expected: Long,
        same: Boolean
    ) {
        val scannedOwner = owner
        var accepted = false
        try {
            val result = authorize(scanned.sessionId)
            val latest = currentSnapshot() ?: return
            if (expected != epoch) return
            validate(result, scanned)
            aliases = latest.items.map { it.alias }
            if (!same) {
                qr = PairingQr(
                    scanned.sessionId,
                    scanned.receiverId,
                    scanned.publicKey.copyOf(),
                    scanned.psk.copyOf()
                )
            }
            session = result
            accepted = true
            val alias = if (same) state.value.alias else defaultAlias
            mutableState.value = state.value.copy(
                page = PairingEntryPage.Consent,
                pending = false,
                problem = null,
                alias = alias,
                aliasError = pairingAliasError(alias, aliases),
                confirmed = false,
                remainingSeconds = remaining(result)
            )
            observe(expected)
        } finally {
            if (!accepted) releaseSession(scanned.sessionId, scannedOwner)
        }
    }

    fun editAlias(alias: String) {
        if (state.value.pending || state.value.confirmed) return
        mutableState.value =
            state.value.copy(alias = alias, aliasError = pairingAliasError(alias, aliases))
    }

    suspend fun confirm() {
        val current = qr ?: return
        if (state.value.pending || state.value.confirmed) return
        val observed = epoch
        pairings.synchronize()
        if (observed != epoch) return
        if (pairings.state.value.failed) {
            fail(PairingEntryProblem.Unavailable)
            return
        }
        val snapshot = currentSnapshot() ?: return
        if (observed != epoch || !admitTarget(snapshot, current.receiverId)) return
        aliases = snapshot.items.map { it.alias }
        val alias = state.value.alias.trim()
        val error = pairingAliasError(alias, aliases)
        mutableState.value = state.value.copy(aliasError = error)
        if (error != null) return
        val expected = ++epoch
        watcher?.cancel()
        mutableState.value = state.value.copy(pending = true)
        confirmSession(current, alias, expected)
    }

    private suspend fun confirmSession(current: PairingQr, alias: String, expected: Long) {
        try {
            val result = authorize(current.sessionId)
            if (expected != epoch) return
            val snapshot = currentSnapshot() ?: return
            if (expected != epoch || !admitTarget(snapshot, current.receiverId)) return
            validate(result, current)
            session = result
            ticker?.cancel()
            mutableState.value = state.value.copy(
                page = PairingEntryPage.Progress,
                alias = alias,
                confirmed = true,
                stage = PairingStage.Opening
            )
            val pairingId = activation.activate(current, result, alias) { stage ->
                if (expected == epoch) mutableState.value = state.value.copy(stage = stage)
            }
            activated(pairingId, expected)
        } catch (failure: PairingEntryException) {
            failCurrent(expected, failure.problem)
        } catch (_: AuthorizationException) {
            if (expected == epoch) leave()
        } catch (_: IdentityStorageException) {
            failCurrent(expected, PairingEntryProblem.Unavailable)
        } catch (_: AccessExpiredException) {
            failCurrent(expected, PairingEntryProblem.Unavailable)
        } catch (_: PairingServiceException) {
            failCurrent(expected, PairingEntryProblem.Unavailable)
        }
    }

    private suspend fun activated(pairingId: String, expected: Long) {
        if (expected != epoch || currentSnapshot() == null) return
        qr?.clear()
        qr = null
        session = null
        mutableState.value = state.value.copy(
            page = PairingEntryPage.Success,
            pending = false,
            problem = null,
            activatedPairingId = pairingId
        )
    }

    private fun failCurrent(expected: Long, problem: PairingEntryProblem) {
        if (expected == epoch) fail(problem)
    }

    fun returnToScanner() {
        epoch++
        watcher?.cancel()
        ticker?.cancel()
        mutableState.value = state.value.copy(
            page = PairingEntryPage.Scanner,
            pending = false,
            problem = null,
            confirmed = false
        )
    }

    fun freshScan() {
        epoch++
        releaseCurrent()
        mutableState.value = state.value.copy(
            page = PairingEntryPage.Scanner,
            pending = false,
            problem = null,
            alias = "",
            aliasError = null,
            confirmed = false,
            remainingSeconds = 0
        )
    }

    fun leave() {
        epoch++
        releaseCurrent()
        mutableState.value = PairingEntryState()
        owner = null
    }

    fun cameraFailed() {
        if (state.value.page == PairingEntryPage.Scanner) fail(PairingEntryProblem.Camera)
    }

    fun dismissNotice() {
        if (state.value.page == PairingEntryPage.Idle) {
            mutableState.value = state.value.copy(problem = null)
        }
    }

    fun clearHighlight() {
        mutableState.value = state.value.copy(duplicatePairingId = null, highlightUntil = 0)
    }

    suspend fun foreground() {
        if (state.value.page == PairingEntryPage.Idle) return
        if (state.value.page == PairingEntryPage.Progress) {
            if (state.value.problem == PairingEntryProblem.Unconfirmed && !state.value.pending) {
                val pairingId = session?.pairingId ?: return
                val expected = epoch
                mutableState.value = state.value.copy(pending = true)
                try {
                    activated(activation.resume(pairingId), expected)
                } catch (failure: PairingEntryException) {
                    failCurrent(expected, failure.problem)
                }
            }
            return
        }
        if (state.value.page == PairingEntryPage.Success) {
            currentSnapshot()
            return
        }
        val snapshot = currentSnapshot() ?: return
        aliases = snapshot.items.map { it.alias }
        mutableState.value =
            state.value.copy(aliasError = pairingAliasError(state.value.alias, aliases))
        val current = session ?: return
        if (!current.expiresAt.isAfter(clock.instant())) {
            fail(PairingEntryProblem.Closed)
        } else if (state.value.page == PairingEntryPage.Consent && !state.value.pending) {
            observe(epoch)
        }
    }

    private suspend fun authorize(sessionId: String): PairingSession = withContext(NonCancellable) {
        try {
            renewal.withAccess(CredentialAccess.Management) { identity ->
                if (owner != identity.accountId to identity.senderId) {
                    throw PairingEntryException(PairingEntryProblem.Closed)
                }
                requestSession(identity, sessionId)
            }
        } catch (_: AccessExpiredException) {
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
    }

    private suspend fun requestSession(
        identity: SenderIdentity,
        sessionId: String
    ): PairingSession = try {
        service.authorize(identity.management.token, sessionId)
    } catch (failure: PairingServiceException) {
        if (failure.failure == PairingFailure.LoginRequired) {
            renewal.rejectAccess(identity, RefreshState.LoginRequired)
        }
        throw PairingEntryException(PairingEntryProblem.Unavailable)
    }

    private fun requireReusable(scanned: PairingQr) {
        if (scanned.sessionId in
            releasedSessions
        ) {
            throw PairingEntryException(PairingEntryProblem.Closed)
        }
        val previous = qr ?: return
        if (previous.sessionId != scanned.sessionId) return
        if (!previous.matches(scanned)) throw PairingEntryException(PairingEntryProblem.InvalidQr)
        if (session?.expiresAt?.isAfter(clock.instant()) != true) {
            throw PairingEntryException(PairingEntryProblem.Closed)
        }
    }

    private suspend fun currentSnapshot(): PairingSnapshot? {
        val current = store.readPairings()
        val identity = current.authority.identity
        if (identity == null || owner != identity.accountId to identity.senderId ||
            renewal.state.value ==
            com.buddylabs.pinglide.domain.identity.AuthorizationState.LoggedOut ||
            current.authority.refreshState != RefreshState.Active
        ) {
            leave()
            return null
        }
        return current
    }

    private fun validate(result: PairingSession, scanned: PairingQr) {
        result.validateConsent(scanned, owner?.second, session, clock.instant())
    }

    private fun observe(expected: Long) {
        watcher?.cancel()
        ticker?.cancel()
        val current = session ?: return
        watcher = scope.launch {
            try {
                renewal.withAccess(CredentialAccess.Management) { identity ->
                    watch(identity, current, expected)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PairingEntryException) {
                if (expected == epoch) fail(failure.problem)
            } catch (_: AuthorizationException) {
                if (expected == epoch) leave()
            } catch (_: IdentityStorageException) {
                if (expected == epoch) fail(PairingEntryProblem.Unavailable)
            } catch (_: AccessExpiredException) {
                if (expected == epoch) fail(PairingEntryProblem.Unavailable)
            }
        }
        ticker = scope.launch {
            while (expected == epoch && state.value.page == PairingEntryPage.Consent) {
                val seconds = remaining(current)
                mutableState.value = state.value.copy(remainingSeconds = seconds)
                if (seconds <= 0) {
                    fail(PairingEntryProblem.Closed)
                    return@launch
                }
                delay(250)
            }
        }
    }

    private suspend fun watch(identity: SenderIdentity, current: PairingSession, expected: Long) {
        try {
            service.watch(identity.management.token, current.sessionId).collect { updated ->
                if (expected == epoch) {
                    val scanned = qr ?: return@collect
                    if (currentSnapshot() != null) validate(updated, scanned)
                }
            }
        } catch (failure: PairingServiceException) {
            if (failure.failure == PairingFailure.LoginRequired) {
                renewal.rejectAccess(identity, RefreshState.LoginRequired)
            }
            throw PairingEntryException(PairingEntryProblem.Unavailable)
        }
    }

    private fun remaining(current: PairingSession): Long =
        ((current.expiresAt.toEpochMilli() - clock.millis() + 999) / 1000).coerceAtLeast(0)

    private fun fail(problem: PairingEntryProblem) {
        watcher?.cancel()
        ticker?.cancel()
        if (problem == PairingEntryProblem.Unconfirmed) {
            qr?.clear()
            qr = null
        } else if (problem != PairingEntryProblem.InvalidQr) {
            releaseCurrent()
        }
        mutableState.value = state.value.copy(
            pending = false,
            problem = problem,
            remainingSeconds = if (problem ==
                PairingEntryProblem.Closed
            ) {
                0
            } else {
                state.value.remainingSeconds
            },
            page = if (problem ==
                PairingEntryProblem.Capacity
            ) {
                PairingEntryPage.Idle
            } else {
                state.value.page
            }
        )
    }

    private fun releaseCurrent() {
        watcher?.cancel()
        ticker?.cancel()
        val previous = session
        qr?.clear()
        qr = null
        session = null
        if (previous != null && state.value.problem != PairingEntryProblem.Unconfirmed) {
            releaseSession(previous.sessionId, owner)
        }
    }

    private fun releaseSession(sessionId: String, previousOwner: Pair<String, String>?) {
        if (!releasedSessions.add(sessionId)) return
        scope.launch {
            try {
                renewal.withAccess(CredentialAccess.Management) { identity ->
                    if (previousOwner == identity.accountId to identity.senderId) {
                        if (store.readPairings().provisional.any {
                                it.provisionalSessionId == sessionId
                            }
                        ) {
                            return@withAccess
                        }
                        try {
                            service.cancel(identity.management.token, sessionId)
                        } catch (failure: PairingServiceException) {
                            if (failure.failure == PairingFailure.LoginRequired) {
                                renewal.rejectAccess(identity, RefreshState.LoginRequired)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: PairingEntryException) {
                return@launch
            } catch (_: AuthorizationException) {
                return@launch
            } catch (_: IdentityStorageException) {
                return@launch
            } catch (_: AccessExpiredException) {
                return@launch
            }
        }
    }
}
