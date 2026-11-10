package com.buddylabs.pinglide.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.ExplicitLogin
import com.buddylabs.pinglide.domain.identity.LoginState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoginViewModel(private val login: ExplicitLogin) : ViewModel() {
    val state = combine(login.state, login.authorizationState) { entry, authorization ->
        when {
            authorization == AuthorizationState.LoggedOut && entry in setOf(
                LoginState.FirstDevice,
                LoginState.Restoring,
                LoginState.Welcome
            ) -> LoginState.Welcome

            entry == LoginState.StorageError -> LoginState.StorageError

            authorization == AuthorizationState.RegistrationRevoked -> LoginState.Revoked

            authorization == AuthorizationState.RegistrationClosed -> LoginState.Welcome

            else -> entry
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoginState.Restoring)
    private var operation: Job? = null
    private var registration: Job? = null

    init {
        restore()
    }

    fun restore() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch { login.restore() }
    }

    fun signIn() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch { login.login() }
    }

    fun signInNewAccount() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch { login.login(registerNew = true) }
    }

    fun beginRegistration() {
        if (state.value !in setOf(LoginState.Revoked, LoginState.Welcome)) return
        if (registration?.isActive == true) return
        login.cancel()
        operation?.cancel()
        registration = viewModelScope.launch { login.beginRegistration() }
        operation = registration
    }

    fun cancel() {
        login.cancel()
        operation?.cancel()
    }

    fun leaveAccount() {
        login.leaveAccount()
        operation?.cancel()
        registration?.cancel()
    }

    override fun onCleared() {
        login.cancel()
    }
}
