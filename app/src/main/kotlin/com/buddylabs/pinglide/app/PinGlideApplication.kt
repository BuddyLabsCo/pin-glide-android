package com.buddylabs.pinglide.app

import android.app.Application
import com.buddylabs.pinglide.BuildConfig
import com.buddylabs.pinglide.data.service.CredentialRenewalService
import com.buddylabs.pinglide.data.service.CurrentSenderAuthorizationService
import com.buddylabs.pinglide.data.service.OwnPairingService
import com.buddylabs.pinglide.data.service.QqLoginService
import com.buddylabs.pinglide.data.storage.ProtectedIdentityStore
import com.buddylabs.pinglide.domain.identity.AuthorizationException
import com.buddylabs.pinglide.domain.identity.CredentialAccess
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.ExplicitLogin
import com.buddylabs.pinglide.domain.identity.IdentityStorageException
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.platform.DeviceInfo
import com.buddylabs.pinglide.platform.qq.QqConnectAuthorization
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
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
    private val currentSenderService by lazy {
        CurrentSenderAuthorizationService(BuildConfig.SERVICE_URL, identityClient)
    }
    private var authorizationCheck: Job? = null
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

    override fun onCreate() {
        super.onCreate()
        processScope.launch { credentialRenewal.recover() }
    }

    fun checkCurrentSenderAuthorization() {
        if (authorizationCheck?.isActive == true) return
        authorizationCheck = processScope.launch {
            try {
                credentialRenewal.withAccess(CredentialAccess.Upload, currentSenderService::check)
            } catch (_: AuthorizationException) {
                return@launch
            } catch (_: IdentityStorageException) {
                return@launch
            } catch (_: IOException) {
                return@launch
            } catch (_: SerializationException) {
                return@launch
            }
        }
    }

    fun explicitLogin() = ExplicitLogin(
        store = identityStore,
        authorization = QqAuthorization {
            check(BuildConfig.SERVICE_URL.isNotBlank())
            qqAuthorization.authorize()
        },
        service = loginService,
        renewal = credentialRenewal,
        deviceName = DeviceInfo(this)::senderName
    )
}
