package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.RevocationReply
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.SenderRevocationService
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.ServerError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.WeChatProofRequest
import okhttp3.Call

class ServiceSenderRevocation(private val baseUrl: String, private val client: Call.Factory) :
    SenderRevocationService {
    override suspend fun revoke(
        identity: SenderIdentity,
        senderId: String,
        code: String
    ): RevocationReply {
        check(baseUrl.isNotBlank())
        val api = IdentityApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { identity.management.token }
        }
        val response = api.postDeviceRevokeWithHttpInfo(senderId, WeChatProofRequest(qqCode = code))
        if (response is Success && response.statusCode == 204) return RevocationReply.Revoked
        val body = when (response) {
            is ClientError -> response.body
            is ServerError -> response.body
            else -> null
        }
        return rejection(
            response.statusCode,
            serviceProblem(response.statusCode, body)?.code,
            senderId == identity.senderId
        )
    }

    private fun rejection(status: Int, code: String?, isSelf: Boolean): RevocationReply = when {
        isSelf && status == 409 && code == "device_registration_revoked" ->
            RevocationReply.CurrentSenderRevoked

        status == 403 && code == "fresh_identity_required" -> RevocationReply.WrongAccount

        (status == 401 && code == "qq_authorization_invalid") ||
            (status == 502 && code == "identity_provider_error") -> RevocationReply.ProofFailed

        else -> RevocationReply.Unconfirmed
    }
}
