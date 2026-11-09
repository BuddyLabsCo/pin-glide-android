package com.buddylabs.pinglide.platform.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

class SetupPermissions(private val context: Context) {
    private val preferences = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)

    val smsGranted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    val smsRequestBlocked: Boolean
        get() = preferences.getBoolean("sms_request_blocked", false)

    fun recordSmsRequestResult(blocked: Boolean) {
        preferences.edit().putBoolean("sms_request_blocked", blocked).apply()
    }

    fun openApplicationSettings(launch: (Intent) -> Unit = context::startActivity): Boolean = open(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ),
        launch
    )

    fun openBatterySettings(): Boolean =
        open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
            open(Intent(Settings.ACTION_SETTINGS))

    private fun open(intent: Intent, launch: (Intent) -> Unit = context::startActivity): Boolean =
        try {
            launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
