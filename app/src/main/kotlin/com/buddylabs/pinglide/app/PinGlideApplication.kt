package com.buddylabs.pinglide.app

import android.app.Application
import com.buddylabs.pinglide.BuildConfig
import com.buddylabs.pinglide.data.service.QqLoginService
import com.buddylabs.pinglide.data.storage.ProtectedIdentityStore
import com.buddylabs.pinglide.domain.identity.ExplicitLogin
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.generated.http.api.IdentityApi
import com.buddylabs.pinglide.platform.DeviceInfo
import com.buddylabs.pinglide.platform.qq.QqConnectAuthorization
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class PinGlideApplication : Application() {
    val qqAuthorization by lazy { QqConnectAuthorization(this) }
    private val identityStore by lazy { ProtectedIdentityStore(this) }
    private val loginService by lazy {
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        QqLoginService(IdentityApi(BuildConfig.SERVICE_URL, client))
    }

    fun explicitLogin() = ExplicitLogin(
        store = identityStore,
        authorization = QqAuthorization {
            check(BuildConfig.SERVICE_URL.isNotBlank())
            qqAuthorization.authorize()
        },
        service = loginService,
        deviceName = DeviceInfo(this)::senderName
    )
}
