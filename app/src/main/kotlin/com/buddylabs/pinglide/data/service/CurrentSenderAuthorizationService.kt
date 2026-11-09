package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.AuthorizationCheckException
import com.buddylabs.pinglide.domain.identity.CurrentSenderAuthorizationCheck
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.Call

class CurrentSenderAuthorizationService(
    private val baseUrl: String,
    private val client: Call.Factory
) : CurrentSenderAuthorizationCheck {
    override suspend fun check(identity: SenderIdentity) {
        if (baseUrl.isBlank()) throw AuthorizationCheckException()
        val api = IdentityApi(baseUrl, identityCalls(client)).apply {
            accessTokenProvider = { identity.upload.token }
        }
        val response = try {
            api.getSelfAccountWithHttpInfo()
        } catch (_: IOException) {
            throw AuthorizationCheckException()
        } catch (_: SerializationException) {
            throw AuthorizationCheckException()
        }
        if (response is ClientError) {
            requireCurrentSenderAccess(response.statusCode, response.body)
        }
        if (response !is Success || response.statusCode != 200 ||
            response.data?.accountId != identity.accountId
        ) {
            throw AuthorizationCheckException()
        }
    }
}
