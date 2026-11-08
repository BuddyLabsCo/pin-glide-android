package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.LoginService
import com.buddylabs.pinglide.domain.identity.LoginServiceException
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.ClientException
import com.buddylabs.pinglide.generated.http.infrastructure.ServerException
import com.buddylabs.pinglide.generated.http.model.QQLoginRequest
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.RequestBody
import okio.BufferedSink

class QqLoginService(baseUrl: String, client: Call.Factory) : LoginService {
    private val api = IdentityApi(
        baseUrl,
        Call.Factory { request ->
            val body = checkNotNull(request.body)
            val oneShotBody = object : RequestBody() {
                override fun contentType() = body.contentType()

                override fun contentLength() = body.contentLength()

                override fun writeTo(sink: BufferedSink) = body.writeTo(sink)

                override fun isOneShot() = true
            }
            client.newCall(request.newBuilder().method(request.method, oneShotBody).build())
        }
    )

    override suspend fun login(code: String, deviceName: String): SenderIdentity {
        val bundle = try {
            api.postAuthQQ(QQLoginRequest(deviceName = deviceName, qqCode = code))
        } catch (failure: Exception) {
            when (failure) {
                is IOException, is ClientException, is ServerException, is SerializationException ->
                    throw LoginServiceException()

                else -> throw failure
            }
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
