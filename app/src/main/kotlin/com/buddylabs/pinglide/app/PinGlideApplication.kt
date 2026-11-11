package com.buddylabs.pinglide.app

import android.app.Application
import com.buddylabs.pinglide.BuildConfig
import com.buddylabs.pinglide.data.service.CredentialRenewalService
import com.buddylabs.pinglide.data.service.CurrentSenderAuthorizationService
import com.buddylabs.pinglide.data.service.OwnPairingService
import com.buddylabs.pinglide.data.service.PairingSessionService
import com.buddylabs.pinglide.data.service.QqLoginService
import com.buddylabs.pinglide.data.service.ServiceAccount
import com.buddylabs.pinglide.data.service.TestDeliveryService
import com.buddylabs.pinglide.data.storage.ProtectedIdentityStore
import com.buddylabs.pinglide.data.storage.SetupPreferences
import com.buddylabs.pinglide.domain.forwarding.PairingTestMessage
import com.buddylabs.pinglide.domain.forwarding.SmsForwarding
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.CurrentSenderAuthorization
import com.buddylabs.pinglide.domain.identity.ExplicitLogin
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingActivation
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.platform.DeviceInfo
import com.buddylabs.pinglide.platform.crypto.PairingCryptography
import com.buddylabs.pinglide.platform.crypto.PairingQrReader
import com.buddylabs.pinglide.platform.crypto.encryptMessage
import com.buddylabs.pinglide.platform.crypto.encryptTestMessage
import com.buddylabs.pinglide.platform.permissions.SetupPermissions
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

    private val deliveryService by lazy {
        TestDeliveryService(BuildConfig.SERVICE_URL, identityClient)
    }

    val smsForwarding by lazy {
        SmsForwarding(
            identityStore,
            credentialRenewal,
            deliveryService,
            ::encryptMessage,
            permitted = {
                BuildConfig.SERVICE_URL.isNotBlank() && SetupPermissions(this).smsGranted
            }
        )
    }

    val pairingTestMessage by lazy {
        PairingTestMessage(
            identityStore,
            credentialRenewal,
            ::encryptTestMessage,
            deliveryService
        )
    }

    val accountSession by lazy {
        AccountSession(
            identityStore,
            credentialRenewal,
            ServiceAccount(BuildConfig.SERVICE_URL, identityClient),
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
        processScope.launch {
            credentialRenewal.recover()
            pairingActivation.recover()
            ownPairings.synchronize()
            if (ownPairings.state.value.targets.any { it.keyUsable }) {
                completeActivatedSetup()
            }
        }
    }

    fun checkCurrentSenderAuthorization() {
        processScope.launch {
            currentSenderAuthorization.check()
            pairingActivation.recover()
        }
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

    fun pairingEntry(scope: CoroutineScope) = PairingEntry(
        identityStore,
        ownPairings,
        PairingSessionService(BuildConfig.SERVICE_URL, identityClient),
        PairingQrReader(),
        credentialRenewal,
        scope,
        activation = pairingActivation
    )

    private val pairingActivation by lazy {
        PairingActivation(
            identityStore,
            ownPairings,
            PairingSessionService(BuildConfig.SERVICE_URL, identityClient),
            PairingCryptography(),
            credentialRenewal,
            onActivated = ::completeActivatedSetup
        )
    }

    private fun completeActivatedSetup() {
        val progress = SetupPreferences(this)
        if (progress.state.batteryAcknowledged && SetupPermissions(this).smsGranted) {
            progress.complete()
        }
    }
}
