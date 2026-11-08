package com.buddylabs.pinglide.platform.qq

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.buddylabs.pinglide.BuildConfig
import com.buddylabs.pinglide.app.PinGlideApplication
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent

class QqAuthorizationActivity : ComponentActivity() {
    private val authorization get() = (application as PinGlideApplication).qqAuthorization
    private val requestId get() = intent.getStringExtra("request_id").orEmpty()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var listener: IUiListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainedListener = authorization.attach(requestId, this)
        if (retainedListener == null) {
            finish()
            return
        }
        listener = retainedListener
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finishAuthorization()
            }
        )
        if (savedInstanceState == null) startAuthorization()
    }

    private fun startAuthorization() {
        try {
            check(BuildConfig.QQ_APP_ID.isNotBlank())
            Tencent.setIsPermissionGranted(true, Build.MODEL)
            val sdk =
                checkNotNull(Tencent.createInstance(BuildConfig.QQ_APP_ID, applicationContext))
            if (sdk.loginServerSide(this, "get_simple_userinfo", listener) <
                0
            ) {
                finishAuthorization()
            }
        } catch (_: RuntimeException) {
            finishAuthorization()
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Tencent.onActivityResultData(requestCode, resultCode, data, listener)
    }

    override fun onDestroy() {
        if (isFinishing) authorization.complete(requestId, null)
        super.onDestroy()
    }

    private fun finishAuthorization() {
        main.post { authorization.complete(requestId, null) }
    }
}
