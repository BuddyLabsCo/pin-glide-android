package com.buddylabs.pinglide.domain.identity

import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
class ManagementCredential(val token: String, val expiresAt: String)

@Serializable
class UploadCredential(val token: String, val expiresAt: String)

@Serializable
class RefreshCredential(val token: String, val expiresAt: String)

@Serializable
class SenderIdentity(
    val accountId: String,
    val senderId: String,
    val management: ManagementCredential,
    val upload: UploadCredential,
    val refresh: RefreshCredential
) {
    fun validate(now: Instant, requireFresh: Boolean) {
        require(accountId.isNotBlank() && senderId.isNotBlank())
        validateCredential(management.token, management.expiresAt, now, requireFresh)
        validateCredential(upload.token, upload.expiresAt, now, requireFresh)
        validateCredential(refresh.token, refresh.expiresAt, now, requireFresh)
        require(setOf(management.token, upload.token, refresh.token).size == 3)
    }

    private fun validateCredential(token: String, expiry: String, now: Instant, fresh: Boolean) {
        require(token.isNotBlank())
        val expiresAt = Instant.parse(expiry)
        require(!fresh || expiresAt.isAfter(now))
    }
}

class IdentitySnapshot(
    val generation: Long = 0,
    val identity: SenderIdentity? = null,
    val renewal: RenewalOperation? = null,
    val refreshState: RefreshState = RefreshState.Active
)

interface IdentityStore {
    suspend fun read(): IdentitySnapshot

    suspend fun updateRenewal(
        expected: IdentitySnapshot,
        replacement: IdentitySnapshot,
        attempt: LoginAttempt = LoginAttempt()
    ): Boolean

    suspend fun accept(
        expectedGeneration: Long,
        identity: SenderIdentity,
        attempt: LoginAttempt
    ): Boolean
}

fun interface QqAuthorization {
    suspend fun authorize(): String
}

fun interface LoginService {
    suspend fun login(code: String, deviceName: String, senderId: String?): SenderIdentity
}

class IdentityStorageException : Exception("Protected identity is unavailable")

class QqAuthorizationException(val cancelled: Boolean = false) :
    Exception("QQ authorization did not complete")

class LoginServiceException(val registrationRevoked: Boolean = false) :
    Exception("QQ login did not complete")

class LoginAttempt {
    private var active = true

    @Volatile
    var accepted: Boolean = false
        private set

    @Volatile
    var registrationRevoked: Boolean = false
        private set

    @Synchronized
    fun cancel() {
        active = false
    }

    @Synchronized
    fun commit(registrationRevoked: Boolean = false, write: () -> Unit): Boolean {
        if (!active) return false
        write()
        this.registrationRevoked = registrationRevoked
        accepted = true
        active = false
        return true
    }
}
