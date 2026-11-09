package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.LoginService
import com.buddylabs.pinglide.domain.identity.LoginServiceException
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientError
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.QQLoginRequest
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.Call

class QqLoginService(baseUrl: String, client: Call.Factory) : LoginService {
    private val api = IdentityApi(baseUrl, identityCalls(client))

    override suspend fun login(
        code: String,
        deviceName: String,
        senderId: String?
    ): SenderIdentity {
        val response = try {
            api.postAuthQQWithHttpInfo(
                QQLoginRequest(deviceName = deviceName, qqCode = code, senderId = senderId)
            )
        } catch (failure: Exception) {
            when (failure) {
                is IOException, is SerializationException ->
                    throw LoginServiceException()

                else -> throw failure
            }
        }
        val bundle = when (response) {
            is Success -> response.data ?: throw LoginServiceException()

            is ClientError -> throw LoginServiceException(
                registrationRevoked = response.statusCode == 409 &&
                    serviceProblem(response.statusCode, response.body)?.code ==
                    "device_registration_revoked"
            )

            else -> throw LoginServiceException()
        }
        return SenderIdentity(
            accountId = bundle.accountId,
            senderId = bundle.senderId,
            management = ManagementCredential(
                bundle.userSession.token,
                bundle.userSession.expiresAt
            ),
            upload = UploadCredential(bundle.uploader.token, bundle.uploader.expiresAt),
            refresh = RefreshCredential(bundle.refresh.token, bundle.refresh.expiresAt)
        )
    }
}
