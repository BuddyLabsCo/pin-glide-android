package com.buddylabs.pinglide.domain.identity

import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LoginState {
    Restoring,
    Entry,
    Loading,
    Error,
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
    private var snapshot = IdentitySnapshot()
    private var attempt: LoginAttempt? = null

    suspend fun restore() {
        mutableState.value = LoginState.Restoring
        try {
            snapshot = store.read()
            mutableState.value = when {
                snapshot.refreshState == RefreshState.RegistrationRevoked -> LoginState.Revoked

                snapshot.refreshState == RefreshState.RegistrationClosed -> LoginState.Welcome

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
        cancel()
        try {
            snapshot = renewal.beginRegistration()
            mutableState.value = LoginState.Entry
        } catch (_: IdentityStorageException) {
            mutableState.value = LoginState.StorageError
        }
    }

    suspend fun login() {
        if (state.value !in listOf(LoginState.Entry, LoginState.Error)) return
        val current = LoginAttempt()
        attempt = current
        mutableState.value = LoginState.Loading
        try {
            val name = deviceName()
            require(
                name.isNotBlank() && name == name.trim() &&
                    name.codePointCount(0, name.length) <= 64
            )
            val code = authorization.authorize()
            if (attempt !== current) return
            require(code.isNotBlank())
            check(store.read().generation == snapshot.generation)
            val identity = service.login(code, name)
            if (attempt !== current) return
            identity.validate(clock.instant(), requireFresh = true)
            require(identity.senderId !in snapshot.closedSenderIds)
            check(store.accept(snapshot.generation, identity, current))
            renewal.recover()
            if (attempt === current) mutableState.value = LoginState.FirstDevice
        } catch (cancelled: CancellationException) {
            current.cancel()
            if (attempt === current) {
                mutableState.value =
                    if (current.accepted) LoginState.FirstDevice else LoginState.Entry
            }
            throw cancelled
        } catch (_: IdentityStorageException) {
            if (attempt === current) mutableState.value = LoginState.StorageError
        } catch (_: Exception) {
            if (attempt === current) mutableState.value = LoginState.Error
        }
    }

    fun cancel() {
        val current = attempt
        current?.cancel()
        mutableState.value =
            if (current?.accepted == true) LoginState.FirstDevice else LoginState.Entry
        attempt = null
    }
}
