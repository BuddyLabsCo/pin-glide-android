package com.buddylabs.pinglide.platform

import android.content.Context
import android.os.Build
import android.provider.Settings

class DeviceInfo(private val context: Context) {
    fun installedVersion(): String = context.packageManager.getPackageInfo(context.packageName, 0)
        .versionName.orEmpty()

    fun senderName(): String {
        val systemName = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (_: SecurityException) {
            null
        }
        val fallback = listOf(Build.BRAND, Build.MODEL)
            .filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ")
        val name = systemName?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallback.trim().ifEmpty { "Android device" }
        return name.substring(
            0,
            name.offsetByCodePoints(0, minOf(64, name.codePointCount(0, name.length)))
        ).trim()
    }
}
