package com.buddylabs.pinglide.app

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.buddylabs.pinglide.domain.settings.AppLanguage

@Composable
internal fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(systemConfiguration, language) {
        val english = language == AppLanguage.English ||
            (language == AppLanguage.System && systemConfiguration.locales[0]?.language == "en")
        Configuration(systemConfiguration).apply {
            setLocales(LocaleList.forLanguageTags(if (english) "en" else "zh-Hans"))
        }
    }
    val localizedContext = remember(context, configuration) {
        context.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalResources provides localizedContext.resources,
        content = content
    )
}
