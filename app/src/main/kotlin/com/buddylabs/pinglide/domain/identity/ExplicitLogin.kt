package com.buddylabs.pinglide.domain.identity

import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class LoginState {
    Restoring,
    Entry,
    Loading,
    Error,
    RestorationError,
    StorageError,
    FirstDevice,
    Revoked,
    Welcome
}

class ExplicitLogin(
    private val store: IdentityStore,
    private val authorization: QqAuthorization,
    private val service: LoginService,
    private val renewal: CredentialRenewal,
    private val deviceName: () -> String,
    private val clock: Clock = Clock.systemUTC()
) {
    private val mutableState = MutableStateFlow(LoginState.Restoring)
    val state = mutableState.asStateFlow()
    val authorizationState = renewal.state
    val logoutState = renewal.logoutState
    private var snapshot = IdentitySnapshot()
    private var attempt: LoginAttempt? = null

    suspend fun restore() {
        mutableState.value = LoginState.Restoring
        try {
            snapshot = store.read()
            mutableState.value = when {
                snapshot.refreshState == RefreshState.RegistrationRevoked -> LoginState.Revoked

                snapshot.refreshState == RefreshState.RegistrationClosed -> LoginState.Welcome

                snapshot.refreshState == RefreshState.LoggedOut -> LoginState.Welcome

                snapshot.identity != null -> LoginState.FirstDevice

                snapshot.generation == 0L || snapshot.closedSenderIds.isNotEmpty() ->
                    LoginState.Entry

                else -> LoginState.StorageError
            }
        } catch (_: IdentityStorageException) {
            mutableState.value = LoginState.StorageError
        }
    }

    suspend fun beginRegistration() {
        try {
            snapshot = renewal.beginRegistration()
            cancel()
            mutableState.value = LoginState.Entry
        } catch (_: IdentityStorageException) {
            mutableState.value = if (logoutState.value == LogoutState.Failed) {
                LoginState.Welcome
            } else {
                LoginState.StorageError
            }
        }
    }

    suspend fun login(registerNew: Boolean = false) {
        if (state.value !in
            listOf(LoginState.Entry, LoginState.Error, LoginState.RestorationError)
        ) {
            return
        }
        val current = LoginAttempt()
        attempt = current
        val expectedSession = renewal.sessionGeneration
        val retained = snapshot.restoration?.takeUnless { registerNew }
        mutableState.value = LoginState.Loading
        try {
            val name = senderName()
            val code = authorization.authorize()
            if (attempt !== current) return
            require(code.isNotBlank())
            check(store.read().generation == snapshot.generation)
            val identity = service.login(code, name, senderId = retained?.senderId)
            acceptLogin(identity, current, retained, expectedSession)
        } catch (cancelled: CancellationException) {
            current.cancel()
            if (attempt === current) {
                mutableState.value =
                    if (current.accepted) LoginState.FirstDevice else LoginState.Entry
            }
            throw cancelled
        } catch (failure: Exception) {
            if (attempt === current) {
                mutableState.value = loginFailure(failure, retained)
            }
        }
    }

    private fun loginFailure(failure: Exception, retained: SenderContext?): LoginState = when {
        failure is IdentityStorageException -> LoginState.StorageError
        failure is QqAuthorizationException && failure.cancelled -> LoginState.Entry
        retained != null -> LoginState.RestorationError
        else -> LoginState.Error
    }

    private fun senderName(): String = deviceName().also {
        require(it.isNotBlank() && it == it.trim() && it.codePointCount(0, it.length) <= 64)
    }

    private suspend fun acceptLogin(
        identity: SenderIdentity,
        current: LoginAttempt,
        retained: SenderContext?,
        expectedSession: Long
    ) {
        if (attempt !== current || renewal.sessionGeneration != expectedSession) return
        identity.validate(clock.instant(), requireFresh = true)
        require(
            retained == null || retained.accountId != identity.accountId ||
                retained.senderId == identity.senderId
        )
        require(identity.senderId !in snapshot.closedSenderIds)
        try {
            check(store.accept(snapshot.generation, identity, current))
        } finally {
            if (current.accepted) {
                withContext(NonCancellable) {
                    if (renewal.resumeSession(expectedSession)) renewal.recover()
                }
            }
        }
        if (attempt === current) mutableState.value = LoginState.FirstDevice
    }

    fun cancel() {
        val current = attempt
        current?.cancel()
        mutableState.value =
            if (current?.accepted == true) LoginState.FirstDevice else LoginState.Entry
        attempt = null
    }

    fun leaveAccount() {
        cancel()
        snapshot = IdentitySnapshot()
        mutableState.value = LoginState.Welcome
    }
}
