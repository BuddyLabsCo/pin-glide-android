package com.buddylabs.pinglide.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.buddylabs.pinglide.data.storage.SetupPreferences
import com.buddylabs.pinglide.platform.permissions.SetupPermissions
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.navigation.SetupNavigation
import com.buddylabs.pinglide.ui.onboarding.LoginViewModel

class MainActivity : ComponentActivity() {
    private val permissions by lazy { SetupPermissions(this) }
    private val login by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <Model : ViewModel> create(modelClass: Class<Model>): Model =
                    modelClass.cast(
                        LoginViewModel((application as PinGlideApplication).explicitLogin())
                    )!!
            }
        )[LoginViewModel::class.java]
    }
    private var smsGranted by mutableStateOf(false)
    private var smsRequestPending by mutableStateOf(false)
    private val smsPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        smsRequestPending = false
        checkSmsPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        smsRequestPending = savedInstanceState?.getBoolean("sms_request_pending") ?: false
        checkSmsPermission()
        val progress = SetupPreferences(applicationContext)
        setContent {
            PinGlideTheme {
                val loginState by login.state.collectAsState()
                SetupNavigation(
                    progress = progress,
                    smsGranted = smsGranted,
                    smsRequestPending = smsRequestPending,
                    onRequestSms = {
                        if (!smsRequestPending) {
                            smsRequestPending = true
                            smsPermissionRequest.launch(Manifest.permission.RECEIVE_SMS)
                        }
                    },
                    onCheckSms = ::checkSmsPermission,
                    onApplicationSettings = permissions::openApplicationSettings,
                    onBatterySettings = permissions::openBatterySettings,
                    loginState = loginState,
                    onLogin = login::signIn,
                    onCancelLogin = login::cancel,
                    onRetryIdentity = login::restore,
                    onBackground = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkSmsPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("sms_request_pending", smsRequestPending)
        super.onSaveInstanceState(outState)
    }

    private fun checkSmsPermission(): Boolean {
        smsGranted = permissions.smsGranted
        return smsGranted
    }
}
