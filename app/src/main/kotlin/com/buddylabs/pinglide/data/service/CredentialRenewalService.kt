package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.AccessExpiredException
import com.buddylabs.pinglide.domain.identity.CurrentSenderRevokedException
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RenewalFailure
import com.buddylabs.pinglide.domain.identity.RenewalOperation
import com.buddylabs.pinglide.domain.identity.RenewalReply
import com.buddylabs.pinglide.domain.identity.RenewalService
import com.buddylabs.pinglide.domain.identity.RenewalServiceException
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.ServerError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.Problem
import com.buddylabs.pinglide.generated.http.model.RefreshRequest
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call

private val problemJson = Json { ignoreUnknownKeys = true }

class CredentialRenewalService(baseUrl: String, client: Call.Factory) : RenewalService {
    private val api = IdentityApi(baseUrl, identityCalls(client))

    override suspend fun renew(
        credential: RefreshCredential,
        operation: RenewalOperation
    ): RenewalReply {
        val response = try {
            api.postAuthRefreshWithHttpInfo(
                RefreshRequest(operation.attempt.toLong(), operation.operationId, credential.token)
            )
        } catch (failure: Exception) {
            when (failure) {
                is IOException, is SerializationException ->
                    throw RenewalServiceException(RenewalFailure.Uncertain)

                else -> throw failure
            }
        }
        val bundle = when {
            response is Success && response.statusCode == 200 -> response.data

            response is ServerError -> throw RenewalServiceException(RenewalFailure.Uncertain)

            response is ClientError -> throw RenewalServiceException(
                renewalFailure(response.statusCode, response.body)
            )

            else -> throw RenewalServiceException(RenewalFailure.Uncertain)
        } ?: throw RenewalServiceException(RenewalFailure.Uncertain)
        return RenewalReply(
            bundle.operationId,
            bundle.attempt,
            SenderIdentity(
                bundle.accountId,
                bundle.senderId,
                ManagementCredential(bundle.userSession.token, bundle.userSession.expiresAt),
                UploadCredential(bundle.uploader.token, bundle.uploader.expiresAt),
                RefreshCredential(bundle.refresh.token, bundle.refresh.expiresAt)
            )
        )
    }
}

internal fun requireCurrentSenderAccess(status: Int, body: Any?) {
    val code = serviceProblem(status, body)?.code
    if (status == 409 && code == "device_registration_revoked") {
        throw CurrentSenderRevokedException()
    }
    if (status == 401 && code == "credential_expired") {
        throw AccessExpiredException()
    }
}

private fun renewalFailure(status: Int, body: Any?): RenewalFailure {
    val code = serviceProblem(status, body)?.code
    return when {
        status == 401 && code in setOf(
            "credential_invalid",
            "credential_expired",
            "credential_revoked",
            "refresh_reuse_detected"
        ) -> RenewalFailure.LoginRequired

        status == 409 && code == "device_registration_revoked" -> RenewalFailure.Revoked

        status == 409 && code in setOf("operation_superseded", "operation_mismatch") ->
            RenewalFailure.RecoveryRequired

        else -> RenewalFailure.Unavailable
    }
}

internal fun serviceProblem(status: Int, body: Any?): Problem? = try {
    (body as? String)?.let { problemJson.decodeFromString<Problem>(it) }?.takeIf {
        it.status ==
            status.toLong()
    }
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
