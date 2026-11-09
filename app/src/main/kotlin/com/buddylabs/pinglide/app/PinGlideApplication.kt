package com.buddylabs.pinglide.app

import android.app.Application
import com.buddylabs.pinglide.BuildConfig
import com.buddylabs.pinglide.data.service.CredentialRenewalService
import com.buddylabs.pinglide.data.service.CurrentSenderAuthorizationService
import com.buddylabs.pinglide.data.service.OwnPairingService
import com.buddylabs.pinglide.data.service.QqLoginService
import com.buddylabs.pinglide.data.storage.ProtectedIdentityStore
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.CurrentSenderAuthorization
import com.buddylabs.pinglide.domain.identity.ExplicitLogin
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.platform.DeviceInfo
import com.buddylabs.pinglide.platform.qq.QqConnectAuthorization
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class PinGlideApplication : Application() {
    val qqAuthorization by lazy { QqConnectAuthorization(this) }
    private val identityStore by lazy { ProtectedIdentityStore(this) }
    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val identityClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val loginService by lazy { QqLoginService(BuildConfig.SERVICE_URL, identityClient) }
    private val authorization = QqAuthorization {
        check(BuildConfig.SERVICE_URL.isNotBlank())
        qqAuthorization.authorize()
    }
    val credentialRenewal by lazy {
        CredentialRenewal(
            identityStore,
            CredentialRenewalService(
                BuildConfig.SERVICE_URL,
                identityClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
            ),
            processScope
        )
    }

    val ownPairings by lazy {
        OwnPairings(
            identityStore,
            OwnPairingService(BuildConfig.SERVICE_URL, identityClient),
            credentialRenewal,
            processScope
        )
    }

    private val currentSenderAuthorization by lazy {
        CurrentSenderAuthorization(
            credentialRenewal,
            CurrentSenderAuthorizationService(BuildConfig.SERVICE_URL, identityClient)
        )
    }

    override fun onCreate() {
        super.onCreate()
        processScope.launch { credentialRenewal.recover() }
    }

    fun checkCurrentSenderAuthorization() {
        processScope.launch { currentSenderAuthorization.check() }
    }

    fun explicitLogin() = ExplicitLogin(
        store = identityStore,
        authorization = authorization,
        service = loginService,
        renewal = credentialRenewal,
        deviceName = DeviceInfo(this)::senderName
    )

    fun senderRestoration() = SenderRestoration(
        store = identityStore,
        authorization = authorization,
        service = loginService,
        renewal = credentialRenewal,
        deviceName = DeviceInfo(this)::senderName
    )
}
