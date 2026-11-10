package com.buddylabs.pinglide.domain.identity

import java.time.Instant
import kotlinx.serialization.Serializable

enum class CredentialAccess { Management, Upload }

internal data class RegistrationClosure(
    val destination: RefreshState,
    val terminalSnapshot: IdentitySnapshot?
)

internal fun IdentitySnapshot.needsRenewal(
    access: CredentialAccess,
    rejectedGeneration: Long?,
    now: Instant
): Boolean {
    val sender = checkNotNull(identity)
    val expiresAt = when (access) {
        CredentialAccess.Management -> sender.management.expiresAt
        CredentialAccess.Upload -> sender.upload.expiresAt
    }
    return generation == rejectedGeneration ||
        !Instant.parse(expiresAt).isAfter(now.plusSeconds(300))
}

enum class AuthorizationState {
    Checking,
    Ready,
    Renewing,
    LoginRequired,
    RecoveryRequired,
    TemporarilyUnavailable,
    StorageUnavailable,
    RegistrationRevoked,
    RegistrationClosed,
    LoggedOut
}

@Serializable
enum class RefreshState {
    Active,
    LoginRequired,
    RecoveryRequired,
    RegistrationRevoked,
    RegistrationClosed,
    LoggedOut;

    val closesRegistration: Boolean
        get() = this == RegistrationRevoked || this == RegistrationClosed
}

@Serializable
data class RenewalOperation(
    val operationId: String,
    val attempt: Int,
    val generation: Long,
    val startedAt: String,
    val originalExpiresAt: String
) {
    fun validate(identity: SenderIdentity, identityGeneration: Long) {
        require(operationId.isNotBlank() && operationId.length <= 128)
        require(attempt in 0..1 && generation == identityGeneration)
        require(originalExpiresAt == identity.refresh.expiresAt)
        require(Instant.parse(startedAt).isBefore(Instant.parse(originalExpiresAt)))
    }

    fun canRetransmit(now: Instant): Boolean = attempt == 0 && withinRecoveryWindow(now)

    fun withinRecoveryWindow(now: Instant): Boolean {
        val started = Instant.parse(startedAt)
        return !now.isBefore(started) &&
            now.isBefore(started.plusSeconds(30)) && now.isBefore(Instant.parse(originalExpiresAt))
    }
}

class RenewalReply(val operationId: String, val attempt: Long, val identity: SenderIdentity)

fun interface RenewalService {
    suspend fun renew(credential: RefreshCredential, operation: RenewalOperation): RenewalReply
}

enum class RenewalFailure { Uncertain, Unavailable, LoginRequired, RecoveryRequired, Revoked }

class RenewalServiceException(val failure: RenewalFailure) : Exception("Credential renewal failed")

class AuthorizationException(val outcome: AuthorizationState) :
    Exception("Authorization unavailable")

class AccessExpiredException : Exception("Required access expired")

class CurrentSenderRevokedException : Exception("Current Sender registration is closed")
