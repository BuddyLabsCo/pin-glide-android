package com.buddylabs.pinglide.domain.identity

import kotlinx.coroutines.sync.Mutex

class CurrentSenderAuthorization(
    private val renewal: CredentialRenewal,
    private val service: CurrentSenderAuthorizationCheck
) {
    private val gate = Mutex()

    suspend fun check() {
        if (!gate.tryLock()) return
        try {
            renewal.withAccess(CredentialAccess.Upload, service::check)
        } catch (_: AccessExpiredException) {
            return
        } catch (_: AuthorizationException) {
            return
        } catch (_: IdentityStorageException) {
            return
        } catch (_: AuthorizationCheckException) {
            return
        } finally {
            gate.unlock()
        }
    }
}

fun interface CurrentSenderAuthorizationCheck {
    suspend fun check(identity: SenderIdentity)
}

class AuthorizationCheckException : Exception("Own authorization is unavailable")
