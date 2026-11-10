package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.AccountService
import com.buddylabs.pinglide.domain.identity.AuthorizationCheckException
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.LogoutRequest
import okhttp3.Call

class ServiceAccount(private val baseUrl: String, private val client: Call.Factory) :
    AccountService {
    override suspend fun profile(identity: SenderIdentity): String? {
        if (baseUrl.isBlank()) throw AuthorizationCheckException()
        val api = IdentityApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { identity.upload.token }
        }
        val response = api.getSelfAccountWithHttpInfo()
        if (response is ClientError) requireCurrentSenderAccess(response.statusCode, response.body)
        if (response !is Success || response.statusCode != 200 ||
            response.data?.accountId != identity.accountId
        ) {
            throw AuthorizationCheckException()
        }
        return response.data.nickname?.takeIf { it.isNotBlank() }
    }

    override suspend fun logout(credential: RefreshCredential) {
        if (baseUrl.isBlank()) throw AuthorizationCheckException()
        IdentityApi(baseUrl, identityCalls(client)).postAuthLogoutWithHttpInfo(
            LogoutRequest(mode = LogoutRequest.Mode.logout, refreshToken = credential.token)
        )
    }
}
