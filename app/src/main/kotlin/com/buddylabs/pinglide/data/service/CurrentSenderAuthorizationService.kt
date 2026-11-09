package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import java.io.IOException
import okhttp3.Call

class CurrentSenderAuthorizationService(
    private val baseUrl: String,
    private val client: Call.Factory
) {
    suspend fun check(identity: SenderIdentity) {
        if (baseUrl.isBlank()) throw IOException("Service is unavailable")
        val api = IdentityApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { identity.upload.token }
        }
        val response = api.getSelfAccountWithHttpInfo()
        if (response is ClientError) {
            requireCurrentSenderAccess(response.statusCode, response.body)
        }
        if (response !is Success || response.statusCode != 200 ||
            response.data?.accountId != identity.accountId
        ) {
            throw IOException("Own authorization is unavailable")
        }
    }
}
