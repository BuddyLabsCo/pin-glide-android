package com.buddylabs.pinglide.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddylabs.pinglide.domain.identity.ExplicitLogin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LoginViewModel(private val login: ExplicitLogin) : ViewModel() {
    val state = login.state
    private var operation: Job? = null

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

    fun cancel() {
        login.cancel()
        operation?.cancel()
    }

    override fun onCleared() {
        login.cancel()
    }
}
