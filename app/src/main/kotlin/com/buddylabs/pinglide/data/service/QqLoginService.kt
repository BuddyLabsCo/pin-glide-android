package com.buddylabs.pinglide.data.service

import com.buddylabs.pinglide.domain.identity.LoginService
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.generated.http.model.QQLoginRequest

class QqLoginService(private val api: IdentityApi) : LoginService {
    override suspend fun login(code: String, deviceName: String): SenderIdentity {
        val bundle = api.postAuthQQ(QQLoginRequest(deviceName = deviceName, qqCode = code))
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
