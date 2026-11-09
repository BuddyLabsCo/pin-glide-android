package com.buddylabs.pinglide.platform.qq

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.domain.identity.QqAuthorizationException
import com.tencent.tauth.IUiListener
import com.tencent.tauth.UiError
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

class QqConnectAuthorization(private val context: Context) : QqAuthorization {
    private val main = Handler(Looper.getMainLooper())
    private var pending: PendingAuthorization? = null

    override suspend fun authorize(): String = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            check(pending == null)
            val request = PendingAuthorization(UUID.randomUUID().toString(), continuation)
            pending = request
            continuation.invokeOnCancellation {
                main.post { complete(request.id, null) }
            }
            try {
                context.startActivity(
                    Intent(context, QqAuthorizationActivity::class.java)
                        .putExtra("request_id", request.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: RuntimeException) {
                complete(request.id, null)
            }
        }
    }

    fun attach(requestId: String, activity: Activity): IUiListener? {
        val request = pending?.takeIf { it.id == requestId } ?: return null
        request.activity = WeakReference(activity)
        return request.listener
    }

    fun complete(requestId: String, code: String?, cancelled: Boolean = false) {
        val request = pending?.takeIf { it.id == requestId } ?: return
        pending = null
        if (request.continuation.isActive) {
            if (code.isNullOrBlank()) {
                request.continuation.resumeWithException(QqAuthorizationException(cancelled))
            } else {
                request.continuation.resume(code)
            }
        }
        request.activity?.get()?.finish()
    }

    private inner class PendingAuthorization(
        val id: String,
        val continuation: CancellableContinuation<String>
    ) {
        var activity: WeakReference<Activity>? = null
        val listener = object : IUiListener {
            override fun onComplete(response: Any?) {
                val result = response as? JSONObject
                val code = if (result?.optInt("ret", 0) ==
                    0
                ) {
                    result.optString("access_token")
                } else {
                    null
                }
                main.post { complete(id, code) }
            }

            override fun onError(error: UiError?) {
                main.post { complete(id, null) }
            }

            override fun onCancel() {
                main.post { complete(id, null, cancelled = true) }
            }

            override fun onWarning(code: Int) = Unit
        }
    }
}
