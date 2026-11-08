package com.buddylabs.pinglide.data.storage

import android.content.Context
import com.buddylabs.pinglide.domain.settings.SetupProgress

class SetupPreferences(context: Context) : SetupProgress {
    private val preferences = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    override val started: Boolean
        get() = preferences.getBoolean("setup_started", false)

    override fun start() {
        preferences.edit().putBoolean("setup_started", true).apply()
    }
}
