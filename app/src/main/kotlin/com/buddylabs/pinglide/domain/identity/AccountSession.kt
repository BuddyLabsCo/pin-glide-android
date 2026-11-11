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

enum class LogoutState { Ready, Pending, Failed }

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
    private var openedSession: Long? = null

    @Synchronized
    fun open() {
        val session = renewal.sessionGeneration
        if (openedSession == session) return
        close()
        openedSession = session
        val expected = generation
        mutableState.value = AccountState()
        profile = scope.launch {
            val result = try {
                renewal.withAccess(CredentialAccess.Upload, ::readProfile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AuthorizationException) {
                return@launch
            } catch (_: Exception) {
                AccountState(loading = false, failed = true)
            }
            synchronized(this@AccountSession) {
                if (generation == expected && renewal.isSessionCurrent(session)) {
                    mutableState.value = result
                }
            }
        }
    }

    private suspend fun readProfile(identity: SenderIdentity): AccountState = try {
        AccountState(loading = false, nickname = service.profile(identity))
    } catch (failure: Exception) {
        when (failure) {
            is CancellationException, is AccessExpiredException,
            is CurrentSenderRevokedException -> throw failure

            else -> AccountState(loading = false, failed = true)
        }
    }

    @Synchronized
    fun close() {
        openedSession = null
        generation++
        profile?.cancel()
        profile = null
        mutableState.value = AccountState()
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
