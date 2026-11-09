package com.buddylabs.pinglide.data.storage

import android.content.Context
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState

class SetupPreferences(context: Context) : SetupProgress {
    private val preferences = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    override val state: SetupState
        get() = SetupState(
            started = preferences.getBoolean("setup_started", false),
            smsRequested = preferences.getBoolean("sms_requested", false),
            smsCompleted = preferences.getBoolean("sms_completed", false),
            batteryAcknowledged = preferences.getBoolean("battery_acknowledged", false),
            completed = preferences.getBoolean("setup_completed", false)
        )

    override fun start() {
        preferences.edit().putBoolean("setup_started", true).apply()
    }

    override fun recordSmsRequest() {
        preferences.edit().putBoolean("sms_requested", true).apply()
    }

    override fun completeSmsPrerequisite() {
        preferences.edit().putBoolean("sms_completed", true).apply()
    }

    override fun acknowledgeBattery() {
        preferences.edit().putBoolean("battery_acknowledged", true).apply()
    }

    override fun complete() {
        preferences.edit().putBoolean("setup_completed", true).apply()
    }
}
