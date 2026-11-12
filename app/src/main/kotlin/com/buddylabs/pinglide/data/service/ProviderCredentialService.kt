package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.generated.http.api.ModelApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.ModelCredential
import okhttp3.Call

class ProviderCredentialService(private val baseUrl: String, private val client: Call.Factory) {
    suspend fun issue(identity: SenderIdentity): ModelCredential {
        check(baseUrl.isNotBlank())
        val api = ModelApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { identity.upload.token }
        }
        val response = api.postModelCredentialsWithHttpInfo()
        if (response is ClientError) {
            requireCurrentSenderAccess(response.statusCode, response.body)
            if (response.statusCode == 401) throw ModelAccessRejectedException()
        }
        check(response is Success && response.statusCode == 200)
        return checkNotNull(response.data)
    }
}

class ModelAccessRejectedException : Exception("Model credential access rejected")
