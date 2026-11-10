package com.buddylabs.pinglide.data.storage

import android.content.Context
import com.buddylabs.pinglide.domain.settings.AppLanguage

class LanguagePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("installation", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.entries.find { it.name == preferences.getString("language", null) }
            ?: AppLanguage.System
        set(value) {
            preferences.edit().putString("language", value.name).apply()
        }
}
