package com.buddylabs.pinglide.platform.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.PinGlideApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SmsForwardingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var operations = 0
    private var latestStartId = 0
    private var foreground = false
    private var stopping = false

    private fun enterForeground(): Boolean {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.sms_forwarding_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_sms)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.sms_forwarding_active))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .build()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            } else {
                0
            }
            startForeground(NOTIFICATION, notification, type)
            foreground = true
            return true
        } catch (_: RuntimeException) {
            return false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val messages = intent?.getStringArrayListExtra(MESSAGES)
        if (stopping || messages.isNullOrEmpty() || (!foreground && !enterForeground())) {
            finishIfIdle()
            return START_NOT_STICKY
        }
        operations++
        scope.launch {
            try {
                withTimeout(150_000) {
                    val forwarding = (application as PinGlideApplication).smsForwarding
                    messages.forEach { forwarding.forward(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch
            } finally {
                operations--
                finishIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    private fun finishIfIdle() {
        if (operations == 0 && stopSelfResult(latestStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
    }

    override fun onTimeout(startId: Int) = stopOperations()

    override fun onTimeout(startId: Int, fgsType: Int) = stopOperations()

    private fun stopOperations() {
        stopping = true
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val MESSAGES = "sms_messages"
        private const val CHANNEL = "sms_forwarding"
        private const val NOTIFICATION = 1
    }
}
