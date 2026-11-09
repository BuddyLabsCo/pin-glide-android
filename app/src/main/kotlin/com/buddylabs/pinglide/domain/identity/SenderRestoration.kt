package com.buddylabs.pinglide.domain.identity

import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RestorationState { Idle, Loading, Cancelled, Error, AccountMismatch, Restored }

class SenderRestoration(
    private val store: IdentityStore,
    private val authorization: QqAuthorization,
    private val service: LoginService,
    private val renewal: CredentialRenewal,
    private val deviceName: () -> String,
    private val clock: Clock = Clock.systemUTC()
) {
    private val mutableState = MutableStateFlow(RestorationState.Idle)
    val state = mutableState.asStateFlow()
    private var attempt: LoginAttempt? = null

    suspend fun restore() {
        if (state.value == RestorationState.Loading) return
        val current = LoginAttempt()
        attempt = current
        mutableState.value = RestorationState.Loading
        try {
            finish(current, restore(current))
        } catch (cancelled: CancellationException) {
            finish(current, RestorationState.Cancelled)
            throw cancelled
        } catch (failure: QqAuthorizationException) {
            finish(
                current,
                if (failure.cancelled) RestorationState.Cancelled else RestorationState.Error
            )
        } catch (_: Exception) {
            finish(current, RestorationState.Error)
        } finally {
            current.cancel()
        }
    }

    private suspend fun restore(current: LoginAttempt): RestorationState {
        val original = restorationSnapshot() ?: return RestorationState.Idle
        if (attempt !== current) return RestorationState.Cancelled
        val saved = checkNotNull(original.identity)
        val name = deviceName()
        require(
            name.isNotBlank() && name == name.trim() &&
                name.codePointCount(0, name.length) <= 64
        )
        val code = authorization.authorize()
        if (!isCurrent(original, current)) return RestorationState.Idle
        require(code.isNotBlank())
        val identity = try {
            service.login(code, name, saved.senderId)
        } catch (failure: LoginServiceException) {
            if (!failure.registrationRevoked) throw failure
            if (isCurrent(original, current)) renewal.revokeRestoration(original, current)
            return RestorationState.Idle
        }
        if (!isCurrent(original, current)) return RestorationState.Idle
        identity.validate(clock.instant(), requireFresh = true)
        if (identity.accountId != saved.accountId) return RestorationState.AccountMismatch
        require(identity.senderId == saved.senderId)
        check(store.accept(original.generation, identity, current))
        renewal.recover()
        return RestorationState.Restored
    }

    private suspend fun isCurrent(expected: IdentitySnapshot, current: LoginAttempt): Boolean {
        if (attempt !== current) return false
        val latest = store.read()
        return attempt === current && latest.generation == expected.generation &&
            latest.identity?.accountId == expected.identity?.accountId &&
            latest.identity?.senderId == expected.identity?.senderId &&
            latest.refreshState != RefreshState.RegistrationRevoked
    }

    private suspend fun restorationSnapshot(): IdentitySnapshot? {
        try {
            renewal.authorize(CredentialAccess.Management)
            return null
        } catch (failure: AuthorizationException) {
            check(
                failure.outcome == AuthorizationState.LoginRequired ||
                    failure.outcome == AuthorizationState.RecoveryRequired
            )
        }
        return store.read().also {
            check(it.identity != null && it.refreshState != RefreshState.RegistrationRevoked)
        }
    }

    fun cancel() {
        val current = attempt ?: return
        current.cancel()
        finish(current, RestorationState.Cancelled)
        attempt = null
    }

    fun leave() {
        cancel()
        if (state.value != RestorationState.Restored) mutableState.value = RestorationState.Idle
    }

    fun dismissNotice() {
        if (state.value == RestorationState.Restored) {
            mutableState.value = RestorationState.Idle
            attempt = null
        }
    }

    private fun finish(current: LoginAttempt, result: RestorationState) {
        if (attempt === current) {
            mutableState.value = when {
                current.registrationRevoked -> RestorationState.Idle
                current.accepted -> RestorationState.Restored
                else -> result
            }
        }
    }
}
