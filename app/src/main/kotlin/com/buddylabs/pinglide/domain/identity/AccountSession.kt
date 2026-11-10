package com.buddylabs.pinglide.domain.identity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface LogoutStore {
    suspend fun logout(): RefreshCredential?
}

interface AccountService {
    suspend fun profile(identity: SenderIdentity): String?

    suspend fun logout(credential: RefreshCredential)
}

data class AccountState(
    val loading: Boolean = true,
    val nickname: String? = null,
    val failed: Boolean = false
)

class AccountSession(
    private val store: LogoutStore,
    private val renewal: CredentialRenewal,
    private val service: AccountService,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(AccountState())
    val state = mutableState.asStateFlow()
    private var profile: Job? = null
    private var generation = 0L
    private var opened = false

    @Synchronized
    fun open() {
        if (opened) return
        close()
        opened = true
        val expected = generation
        mutableState.value = AccountState()
        profile = scope.launch {
            val result = try {
                AccountState(
                    loading = false,
                    nickname = renewal.withAccess(CredentialAccess.Upload, service::profile)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AccountState(loading = false, failed = true)
            }
            synchronized(this@AccountSession) {
                if (generation == expected) mutableState.value = result
            }
        }
    }

    @Synchronized
    fun close() {
        opened = false
        generation++
        profile?.cancel()
        profile = null
        mutableState.value = AccountState()
    }

    fun retry() {
        close()
        open()
    }

    fun logout() {
        close()
        val local = renewal.logout(store)
        scope.launch {
            try {
                local.await()?.let { service.logout(it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch
            } finally {
                renewal.finishLogout(local)
            }
        }
    }
}
