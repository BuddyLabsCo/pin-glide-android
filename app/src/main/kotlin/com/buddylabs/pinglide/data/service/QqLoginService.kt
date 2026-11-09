package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.LoginService
import com.buddylabs.pinglide.domain.identity.LoginServiceException
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.infrastructure.Success
import com.buddylabs.pinglide.generated.http.model.QQLoginRequest
import java.io.IOException
import java.util.Locale
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.RequestBody
import okhttp3.Response
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
            val call = client.newCall(
                request.newBuilder().method(request.method, oneShotBody).build()
            )
            object : Call by call {
                override fun enqueue(responseCallback: Callback) =
                    call.enqueue(LoginResponseCallback(responseCallback))
            }
        }
    )

    override suspend fun login(code: String, deviceName: String): SenderIdentity {
        val response = try {
            api.postAuthQQWithHttpInfo(QQLoginRequest(deviceName = deviceName, qqCode = code))
        } catch (failure: Exception) {
            when (failure) {
                is IOException, is SerializationException ->
                    throw LoginServiceException()

                else -> throw failure
            }
        }
        val bundle = when (response) {
            is Success -> response.data ?: throw LoginServiceException()
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

private class LoginResponseCallback(private val callback: Callback) : Callback by callback {
    override fun onResponse(call: Call, response: Response) {
        val mediaType = response.header("Content-Type")?.substringBefore(";")?.lowercase(Locale.US)
        if (response.isSuccessful && mediaType != null &&
            !(mediaType.startsWith("application/") && mediaType.endsWith("json"))
        ) {
            response.close()
            callback.onFailure(call, IOException("QQ login returned an unsupported response"))
        } else {
            callback.onResponse(call, response)
        }
    }
}
