package com.buddylabs.pinglide.platform.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.buddylabs.pinglide.platform.permissions.SetupPermissions

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !SetupPermissions(context).smsGranted
        ) {
            return
        }
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                .mapNotNull { it?.messageBody }
            if (messages.isEmpty()) return
            context.startForegroundService(
                Intent(context, SmsForwardingService::class.java)
                    .putStringArrayListExtra(SmsForwardingService.MESSAGES, ArrayList(messages))
            )
        } catch (_: RuntimeException) {
            return
        }
    }
}
